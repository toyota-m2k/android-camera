package io.github.toyota32k.secureCamera.server

import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.secureCamera.PlayerActivity
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.utils.HashUtils.encodeHex
import io.github.toyota32k.server.HttpErrorResponse
import io.github.toyota32k.server.HttpMethod
import io.github.toyota32k.server.HttpServer
import io.github.toyota32k.server.QueryParams
import io.github.toyota32k.server.Route
import io.github.toyota32k.server.response.StatusCode
import io.github.toyota32k.server.response.StreamingHttpResponse
import io.github.toyota32k.server.response.TextHttpResponse
import io.github.toyota32k.server.response.TextHttpResponse.Companion.CT_TEXT_PLAIN
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import kotlin.random.Random

class TcServer(val port:Int) : AutoCloseable {
    companion object {
        val logger = UtLog("Server",null, this::class.java)
        private fun generateToken():String {
            return Random.nextBytes(8).encodeHex()
        }
        fun updateAuthToken():String {
            authToken = generateToken()
            return authToken
        }

        var authToken: String = generateToken()
        val regRange = Regex("bytes=(?<start>\\d+)(?:-(?<end>\\d+))?");
        val routes = arrayOf<Route>(
            Route("Capability", HttpMethod.GET, "/capability") { _, _ ->
                TextHttpResponse(
                    StatusCode.Ok,
                    JSONObject()
                        .put("cmd", "capability")
                        .put("serverName", "SecCamera")
                        .put("version", 1)
                        .put("root", "/")
                        .put("category", false)
                        .put("rating", false)
                        .put("mark", false)
                        .put("chapter", true)
                        .put("sync", false)
                        .put("acceptRequest", false)
                        .put("backup", false)
                        .put("hasView", false)
                        .put("authentication", true)
                        // .put("challenge", ) todo
                )
            },
            Route("list", HttpMethod.GET, Regex("/list(\\?.+)*")) { _, request ->
                val p = QueryParams.parse(request.url)
                if(p["auth"]!= authToken) {
                    return@Route HttpErrorResponse.unauthorized();
                }
                val list = runBlocking {
                    MetaDB.list(PlayerActivity.ListMode.VIDEO).fold(JSONArray()) { array, item ->
                        array.put(JSONObject().apply {
                            put("id", "${item.id}")
                            put("name", item.name)
                            put("size", item.size)
                            put("date", "${item.date}")
                            put("duration", item.duration)
                            put("type", "mp4")
                        })
                    }
                }
                TextHttpResponse(
                    StatusCode.Ok,
                    JSONObject()
                        .put("cmd", "list")
                        .put("list", list)
                        .put("date", "${Date().time}")
                )
            },
            Route("Video", HttpMethod.GET,"/video\\?.+") { _, request ->
                val p = QueryParams.parse(request.url)
                if(p["auth"]!= authToken) {
                    return@Route HttpErrorResponse.unauthorized();
                }
                val id = p["id"]?.toLongOrNull() ?: return@Route HttpErrorResponse.badRequest("id is not specified")
                val item = runBlocking {
                    MetaDB.itemAt(id)?.run {
                        if(CloudStatus.valueOf(cloud).isFileInLocal) this else null
                    }
                } ?: return@Route HttpErrorResponse.notFound()
                val range = request.headers["Range"]
                if(range==null) {
                    StreamingHttpResponse(StatusCode.Ok, "video/mp4", item.file, 0L,0L)
                } else {
                    val c = regRange.find(range) ?: return@Route HttpErrorResponse.badRequest("invalid range")
                    val start = c.groups["start"]?.value?.toLongOrNull() ?: 0L
                    val end = c.groups["end"]?.value?.toLongOrNull() ?: 0L
                    StreamingHttpResponse(StatusCode.Ok, "video/mp4", item.file, start, end)
                }
            },
            Route("Backup Completed", HttpMethod.PUT, "/backup/completed") {_, request->
                val content = request.contentAsString()
                val json = JSONObject(content)
                val id = json.optString("id")
                logger.debug("Backup completed: ${id}")
                TextHttpResponse(StatusCode.Ok, "ok", CT_TEXT_PLAIN)
            }
        )
    }

    val httpServer = HttpServer(routes)

    fun start() {
        httpServer.start(port)
    }

    override fun close() {
        httpServer.close()
    }

}
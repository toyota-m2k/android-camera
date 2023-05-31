package io.github.toyota32k.secureCamera.server

import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.secureCamera.PlayerActivity
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.server.HttpErrorResponse
import io.github.toyota32k.server.HttpMethod
import io.github.toyota32k.server.HttpServer
import io.github.toyota32k.server.QueryParams
import io.github.toyota32k.server.Route
import io.github.toyota32k.server.response.StatusCode
import io.github.toyota32k.server.response.StreamingHttpResponse
import io.github.toyota32k.server.response.TextHttpResponse
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

class TcServer(val port:Int) : AutoCloseable {
    companion object {
        val regRange = Regex("bytes=(?<start>\\d+)(?:-(?<end>\\d+))?");
        val routes = arrayOf<Route>(
            Route("Capability", HttpMethod.GET, "/ytplayer/capability") { _, _ ->
                TextHttpResponse(
                    StatusCode.Ok,
                    JSONObject()
                        .put("cmd", "capability")
                        .put("serverName", "SecCamera")
                        .put("version", 1)
                        .put("category", false)
                        .put("rating", false)
                        .put("mark", false)
                        .put("acceptRequest", false)
                        .put("hasView", false)
                )
            },
            Route("list", HttpMethod.GET, Regex("/ytplayer/list(\\?.+)*")) { _, request ->
                val p = QueryParams.parse(request.url)
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
            Route("Video", HttpMethod.GET,"/ytplayer/video\\?.+") { _, request ->
                val p = QueryParams.parse(request.url)
                val id = p["id"]?.toLongOrNull() ?: return@Route HttpErrorResponse.badRequest("id is not specified")
                val item = runBlocking {
                    MetaDB.itemAt(id)
                } ?: return@Route HttpErrorResponse.notFound()
                val range = request.headers["Range"]
                if(range==null) {
                    StreamingHttpResponse(StatusCode.Ok, "video/mp4", item.file(UtImmortalTaskManager.application), 0L,0L)
                } else {
                    val c = regRange.find(range) ?: return@Route HttpErrorResponse.badRequest("invalid range")
                    val start = c.groups["start"]?.value?.toLongOrNull() ?: 0L
                    val end = c.groups["end"]?.value?.toLongOrNull() ?: 0L
                    StreamingHttpResponse(StatusCode.Ok, "video/mp4", item.file(UtImmortalTaskManager.application), start, end)
                }
            },
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
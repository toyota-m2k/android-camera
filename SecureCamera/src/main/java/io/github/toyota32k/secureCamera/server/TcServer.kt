package io.github.toyota32k.secureCamera.server

import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.PlayerActivity
import io.github.toyota32k.secureCamera.SCApplication
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.db.ScDB
import io.github.toyota32k.secureCamera.settings.PasswordUtil
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.settings.SlotIndex
import io.github.toyota32k.secureCamera.settings.SlotSettings
import io.github.toyota32k.secureCamera.utils.HashUtils.encodeHex
import io.github.toyota32k.server.HttpErrorResponse
import io.github.toyota32k.server.HttpMethod
import io.github.toyota32k.server.HttpRequest
import io.github.toyota32k.server.HttpServer
import io.github.toyota32k.server.QueryParams
import io.github.toyota32k.server.Route
import io.github.toyota32k.server.response.IHttpResponse
import io.github.toyota32k.server.response.StatusCode
import io.github.toyota32k.server.response.StreamingHttpResponse
import io.github.toyota32k.server.response.TextHttpResponse
import io.github.toyota32k.server.response.TextHttpResponse.Companion.CT_TEXT_PLAIN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Path
import java.util.Date
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

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

        // ---- SSL (self-signed PFX) -----------------------------------------------------
        private const val PFX_FILENAME = "server-cert.pfx"
        // 端末ローカルに置く PFX のパスワード。app-private storage 内のため実質的な防御力は無く、
        // PKCS#12 仕様上必須となるため固定値で構わない。
        private const val PFX_PASSWORD = "secureCamera"
        private const val PFX_CERT_CN = "SecureCamera"
        private val PFX_VALIDITY = 3650.days

        /** 起動中の TLS 証明書の SHA-256 fingerprint ("AB:CD:..." 形式)。start() 後に有効。 */
        var fingerprint: String? = null
            private set

        /** 既存 PFX があれば load、なければ新規生成して load。失敗時は例外を投げる。 */
        private fun loadOrCreatePfx(): PFXLoader {
            val ctx = SCApplication.instance.applicationContext
            val pfxPath: Path = ctx.filesDir.toPath().resolve(PFX_FILENAME)
            if (!java.nio.file.Files.exists(pfxPath)) {
                logger.info("Generating self-signed PFX: $pfxPath")
                // SAN は DHCP で変わる LAN IP を入れない (fingerprint pinning で検証されるため
                // SAN 内容自体は到達性に影響しないが、エントリ 0 件を避けるため安定識別子だけ入れる)。
                PFXLoader.createPfxFile(
                    path = pfxPath,
                    cn = PFX_CERT_CN,
                    hostnames = listOf("localhost"),
                    ipAddresses = listOf("127.0.0.1"),
                    pfxPassword = PFX_PASSWORD,
                    validity = PFX_VALIDITY,
                )
            }
            return PFXLoader.loadPfxFile(pfxPath, PFX_PASSWORD)
        }
        // -------------------------------------------------------------------------------

//        private fun videoDownloadProc(@Suppress("UNUSED_PARAMETER") route: Route, request: HttpRequest):IHttpResponse {
//            return downloadProcCore(request, "video/mp4")
//        }
//        private fun photoDownloadProc(@Suppress("UNUSED_PARAMETER") route: Route, request: HttpRequest): IHttpResponse {
//            return downloadProcCore(request, "image/jpeg")
//        }

        private inline fun <T> withDB( fn:(db:ScDB)->T):T {
            return MetaDB[SlotSettings.currentSlotIndex].use { db->
                fn(db)
            }
        }
        private inline fun <T> withDB(slot:Int, fn:(db:ScDB)->T):T {
            if (slot==-1) {
                return withDB(fn)
            }
            return MetaDB[SlotIndex.fromIndex(slot)].use { db->
                fn(db)
            }
        }

        val slotRegex = Regex("/slot(?<slot>\\d+)/")
        private fun getSlot(request: HttpRequest): Int {
            val m = slotRegex.find(request.url)
            return m?.groups["slot"]?.value?.toIntOrNull() ?: -1
        }

        private fun downloadProcCore(@Suppress("UNUSED_PARAMETER") route: Route, request: HttpRequest):IHttpResponse {
            val p = QueryParams.parse(request.url)
            if(p["auth"]!= authToken) {
                return unauthorizedResponseWithChallenge()
            }
            val id = p["id"]?.toIntOrNull() ?: return HttpErrorResponse.badRequest("id is not specified")
            return withDB(getSlot(request)) { db ->
                var type: String = "video/mp4"
                val item = runBlocking {
                    db.itemAt(id)?.run {
                        type = if (this.type==0) "image/jpeg" else "video/mp4"
                        if (CloudStatus.valueOf(cloud).isFileInLocal) this else null
                    }
                } ?: return HttpErrorResponse.notFound()
                val range = request.headers["Range"]
                if (range == null) {
                    StreamingHttpResponse(StatusCode.Ok, type, db.fileOf(item), 0L, 0L)
                } else {
                    val c =
                        regRange.find(range) ?: return HttpErrorResponse.badRequest("invalid range")
                    val start = c.groups["start"]?.value?.toLongOrNull() ?: 0L
                    val end = c.groups["end"]?.value?.toLongOrNull() ?: 0L
                    StreamingHttpResponse(StatusCode.Ok, type, db.fileOf(item), start, end)
                }
            }
        }

        private var challenge:String = UUID.randomUUID().toString()

        val routes = arrayOf<Route>(
            Route("NOP", HttpMethod.GET, "/nop") { _,_ ->
                TextHttpResponse(StatusCode.Ok, CT_TEXT_PLAIN,"SC Server running.")
            },
            Route("Capability", HttpMethod.GET, "(/slot\\d+)?/capability") { _, _ ->
                TextHttpResponse(
                    StatusCode.Ok,
                    JSONObject()
                        .put("cmd", "capability")
                        .put("serverName", "SecCamera")
                        .put("version", 2)
                        .put("root", "/")
                        .put("category", false)
                        .put("rating", false)
                        .put("mark", false)
                        .put("chapter", true)
                        .put("reputation", 0)
                        .put("diff", false)
                        .put("sync", false)
                        .put("acceptRequest", false)
                        .put("backup", false)
                        .put("hasView", false)
                        .put("authentication", true)
                        .put("challenge", challenge)
                        .put("types", "vp")
                )
            },
            Route("List", HttpMethod.GET, Regex("(/slot\\d+)?/list(\\?.+)*")) { _, request ->
                val p = QueryParams.parse(request.url)
                if(p["auth"]!= authToken) {
                    return@Route unauthorizedResponseWithChallenge()
                }
                val type = when(p["type"]) {
                    "all"->PlayerActivity.ListMode.ALL
                    "photo"->PlayerActivity.ListMode.PHOTO
                    else->PlayerActivity.ListMode.VIDEO
                }
                val backup = (p["backup"]?:"").toBoolean()
                val predicate:(item: ItemEx)->Boolean = if(backup) { _-> true } else { item->item.data.cloud != CloudStatus.Cloud.v }
                val visitor = if(backup) MetaDB.allVisitor() else MetaDB.singleVisitor(SlotIndex.fromIndex(getSlot(request)))
                val list = runBlocking {
                    JSONArray().also { array ->
                        visitor.visit(type, predicate) { db, item ->
                            val size = if (CloudStatus.valueOf(item.data.cloud).isFileInLocal) {
                                db.fileOf(item).length()
                            } else item.size
                            array.put(JSONObject().apply {
                                put("id", "${item.id}")
                                put("name", item.name)
                                put("size", size)
                                put("date", "${item.date}")
                                put("duration", item.duration)
                                put("type", if (item.type == 0) "jpg" else "mp4")
                                put("media", if (item.type == 0) "p" else "v")
                                if (backup) {
                                    put("creationDate", "${ItemEx.creationDate(item.data)}")
                                    put("cloud", item.data.cloud)
                                    put("attrDate", "${item.data.attr_date}")
                                    put("slot", "${item.slot}")
                                }
                            })
                        }
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
            Route("Item", HttpMethod.GET,"(/slot\\d+)?/item\\?.+", ::downloadProcCore),
            Route("Video", HttpMethod.GET,"(/slot\\d+)?/video\\?.+", ::downloadProcCore),
            Route("Photo", HttpMethod.GET,"(/slot\\d+)?/photo\\?.+", ::downloadProcCore),
            Route("Chapter", HttpMethod.GET, "(/slot\\d+)?/chapter\\?.+") { _, request->
                val p = QueryParams.parse(request.url)
                val id = p["id"]?.toIntOrNull() ?: return@Route HttpErrorResponse.badRequest("id is required.")
                val item = runBlocking {
                    withDB(getSlot(request)) { db -> db.itemExAt(id) }
                } ?: return@Route HttpErrorResponse.notFound()
                TextHttpResponse(
                    StatusCode.Ok,
                    JSONObject()
                        .put("cmd", "chapter")
                        .put("id", "$id")
                        .put("chapters", item.chapterList?.fold(JSONArray()){acc,chapter->
                            acc.apply {
                                put(JSONObject().apply {
                                    put("position", chapter.position)
                                    put("label", chapter.label)
                                    put("skip", chapter.skip)
                                })
                            }
                        }?:JSONArray())
                )
            },
            Route("Extra Attributes", HttpMethod.GET, "(/slot\\d+)?/extension\\?.+") { _, request ->
                val p = QueryParams.parse(request.url)
                val id = p["id"]?.toIntOrNull() ?: return@Route HttpErrorResponse.badRequest("id is required.")
                val item = runBlocking {
                    withDB(getSlot(request)) { db->db.itemExAt(id) }
                } ?: return@Route HttpErrorResponse.notFound()
                TextHttpResponse(
                    StatusCode.Ok,
                    item.attrDataJson.put("cmd","extension")
                )
            },
            Route("1 file backup finished", HttpMethod.PUT, "/backup/done") {_, request->
                val content = request.contentAsString()
                val json = JSONObject(content)
                val auth = json.optString("auth")
                if(auth!= authToken) {
                    return@Route unauthorizedResponseWithChallenge()
                }
                val ownerId = json.optString("owner")
                if(ownerId != Settings.SecureArchive.clientId) {
                    return@Route HttpErrorResponse.badRequest()
                }
                val id = json.optInt("id", -1)
                val slot = json.optInt("slot", -1)
                val ids = json.optJSONArray("ids")
                val slots = json.optJSONArray("slots")
                val status = json.optBoolean("status")
                logger.debug("Backup id=$id done: $status")
                if(status) {
                    CoroutineScope(Dispatchers.IO).launch {
                        if (id >= 0 && slot >= 0) {
                            withDB(slot) { db ->
                                db.updateCloud(id, CloudStatus.Uploaded)
                            }
                        }
                        if (ids != null) {
                            MetaDB.lockDB().use {
                                for (i in 0 until ids.length()) {
                                    val d = ids.optInt(i, -1)
                                    val s = slots?.optInt(i, -1) ?: -1
                                    if (d >= 0 && s >= 0) {
                                        withDB(s) { db -> db.updateCloud(d, CloudStatus.Uploaded) }
                                    }
                                }
                            }
                        }
                    }
                }
                TextHttpResponse(StatusCode.Ok, "ok", CT_TEXT_PLAIN)
            },
            Route("Request DB backup", HttpMethod.GET, "/db/backup\\?.+") { _, request ->
                val p = QueryParams.parse(request.url)
                if(p["auth"]!= authToken) {
                    return@Route unauthorizedResponseWithChallenge()
                }
                val file = ScDB.zipDbFiles() ?: return@Route HttpErrorResponse.notFound()
                StreamingHttpResponse(StatusCode.Ok, "application/zip", file, 0L, 0L) {
                    file.delete()
                }
            },
            Route("Check Auth Token", HttpMethod.GET, "/auth/.*",) { _, request ->
                if (request.url.length > 6) {
                    // with token
                    val token = request.url.substring(6)
                    if (token == authToken) {
                        // 認証済み
                        return@Route TextHttpResponse(StatusCode.Ok, CT_TEXT_PLAIN, "ok")
                    }
                }
                // 要認証 --> challenge を返す
                unauthorizedResponseWithChallenge()
            },
            Route("Authentication", HttpMethod.PUT, "/auth") { _, request ->
                val passPhrase = request.contentAsString().trim()
                if (PasswordUtil.getPassPhraseWithHashedPassword(Settings.Security.password, challenge) == passPhrase) {
                    val tick = System.currentTimeMillis()
                    TextHttpResponse(StatusCode.Ok,
                        JSONObject()
                            .put("cmd", "auth")
                            .put("token", authToken)
                            .put("term",tick+30.minutes.inWholeMilliseconds)
                        )
                } else {
                    unauthorizedResponseWithChallenge()
                }
            }
        )

        fun unauthorizedResponseWithChallenge(): IHttpResponse {
            return TextHttpResponse(StatusCode.Unauthorized,
                JSONObject().put("challenge", challenge))
        }
    }


    val httpServer = HttpServer(routes).apply {
        httpProcessor.allowCors()
    }

    fun start(ssl:Boolean) {
        val pfx = loadOrCreatePfx()
        fingerprint = pfx.getFingerprint()
        logger.info("Starting HTTPS on port $port (fp=$fingerprint)")
        httpServer.start(port, if (ssl) pfx.createSSLContext() else null)
    }

    override fun close() {
        httpServer.close()
    }

}
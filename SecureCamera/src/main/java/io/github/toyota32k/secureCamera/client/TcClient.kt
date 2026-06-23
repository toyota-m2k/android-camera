package io.github.toyota32k.secureCamera.client

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.ServerActivity
import io.github.toyota32k.secureCamera.client.NetClient.executeAndGetJsonAsync
import io.github.toyota32k.secureCamera.client.NetClient.executeAsync
import io.github.toyota32k.secureCamera.client.auth.AuthHost
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.MetaData
import io.github.toyota32k.secureCamera.db.ScDB
import io.github.toyota32k.secureCamera.server.TcServer
import io.github.toyota32k.secureCamera.settings.SecureArchiveHost
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.settings.SlotIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object TcClient {
    val logger = UtLog("Tc", NetClient.logger, this::class.java)

    fun sizeInKb(size: Long): String {
        return String.format(Locale.US, "%,d KB", size / 1000L)
    }

    suspend fun registerOwnerToSecureArchive(host: SecureArchiveHost):Boolean {
//        val address = Settings.SecureArchive.address
//        if(address.isEmpty()) return false
        val json = JSONObject()
            .put("id", Settings.SecureArchive.clientId)
            .put("name", Settings.SecureArchive.deviceName)
            .put("type", "SecureCamera")
            .toString()
        val request = Request.Builder()
            .url(host.makeUrl("owner"))
            .put(json.toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            NetClient.executeAndGetJsonAsync(request, host)
            logger.info("owner registered.")
            true
        } catch (e:Throwable) {
            logger.error(e)
            false
        }
    }

    suspend fun getPhoto(db:ScDB, item:ItemEx): Bitmap? {
        if(!item.isPhoto) return null
        val host = Authentication.authAndMessage() ?: return null

//        val address = Settings.SecureArchive.address
//        if(address.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(db.urlOf(item, host))
                .build()
            try {
                executeAsync(request, host.activeHost).use { response ->
                    if (response.isSuccessful) {
                        response.body.use { body ->
                            body.byteStream().use { inStream ->
                                BitmapFactory.decodeStream(inStream)
                            }
                        }
                    } else {
                        logger.error(response.message)
                        null
                    }
                }
            } catch (e:Throwable) {
                logger.error(e)
                null
            }
        }
    }

    data class RepairingItem(val slot: Int, val id:Int, val originalId:Int, val name:String, val size:Long, val type:String, val registeredDate:Long, val lastModifiedDate:Long, val creationDate:Long, val metaInfo:String, val deleted:Int, val extAttrDate:Long, val rating:Int, val mark:Int, val label:String, val category:String, val chapters:String, val duration:Long)

    suspend fun getListForRepair(host: AuthHost, slot:SlotIndex):List<RepairingItem>? {
        fun jsonToItems(list: JSONArray):Sequence<RepairingItem> {
            return sequence<RepairingItem> {
                for(i in 0..<list.length()) {
                    val o = list.getJSONObject(i)
                    yield(RepairingItem(
                        slot = o.optInt("slot", 0),
                        id = o.optInt("id", 0),
                        originalId = o.optInt("originalId", 0),
                        name = o.optString("name", ""),
                        size = o.optLong("size", 0),
                        type = o.optString("type", ""),
                        registeredDate = o.optLong("registeredDate", 0L),
                        lastModifiedDate = o.optLong("lastModifiedDate", 0L),
                        creationDate = o.optLong("creationDate", 0L),
                        metaInfo = o.optString("metaInfo", ""),
                        deleted = o.optInt("deleted", 0),
                        extAttrDate = o.optLong("extAttrDate", 0L),
                        rating = o.optInt("rating", 0),
                        mark = o.optInt("mark", 0),
                        label = o.optString("label", ""),
                        category = o.optString("category", ""),
                        chapters = o.optString("chapters", ""),
                        duration = o.optLong("duration")
                    ))
                }
            }
        }
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
//                .url("http://${Authentication.activeHostAddress}/${slot.slotId}/list?auth=${Authentication.authToken}&f=vp&o=${Settings.SecureArchive.clientId}")
                .url(host.makeAuthUrl("${slot.slotId}/list", "f" to "vp",  "o" to Settings.SecureArchive.clientId))
                .get()
                .build()
            try {
                val json = executeAndGetJsonAsync(request, host.activeHost)
                jsonToItems(json.getJSONArray("list")).toList()
            } catch(e:Throwable) {
                logger.error(e)
                null
            }
        }
    }

    data class DeviceInfo(val name:String, val clientId:String)
    suspend fun getDeviceListForMigration(host:AuthHost):List<DeviceInfo>? {
        fun jsonToItems(list: JSONArray):Sequence<DeviceInfo> {
            return sequence<DeviceInfo> {
                for (i in 0..<list.length()) {
                    val o = list.getJSONObject(i)
                    yield(DeviceInfo(
                        name = o.optString("name", "noname"),
                        clientId = o.optString("id", "uav")))
                }
            }
        }

        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(host.makeAuthUrl("migration/devices", "o" to Settings.SecureArchive.clientId))
//                .url("http://${Authentication.activeHostAddress}/migration/devices?auth=${Authentication.authToken}&o=${Settings.SecureArchive.clientId}")
                .get()
                .build()
            try {
                val json = executeAndGetJsonAsync(request, host.activeHost)
                jsonToItems(json.getJSONArray("list")).toList()
            } catch(e:Throwable) {
                logger.error(e)
                null
            }
        }
    }

    data class StoredFileEntry(
        val id:Long,
        val originalId:String,
        val ownerId:String,
        val slot:Int,
        val name:String,
        val size:Long,
        val registeredDate:Long,
        val lastModifiedDate:Long,
        val creationDate:Long,
        val metaInfo:String,
        val deleted:Long,
        val extAttrDate:Long,
        val rating:Int,
        val mark:Int,
        val label:String,
        val category:String,
        val chapters:String,
        val duration:Long,
    ) {
        val isValid:Boolean get() = id>0 && originalId.isNotEmpty() && ownerId.isNotEmpty()
        // なんと、Duration は端末側にしか覚えていない
        fun toMetaData():MetaData {
            return MetaData(
                id=0,
                name=name,
                group=0,
                mark=mark,
                type=if(name.endsWith(".mp4")) 1 else 0,
                date=registeredDate,
                size=size,
                duration=duration,
                rating=rating,
                cloud=CloudStatus.Cloud.v,
                flag=0,
                ext=null,
                attr_date=extAttrDate,
                label=label,
                category=category
            )
        }
        fun toChaptersList():List<IChapter> {
            if(chapters.isEmpty()) return emptyList()
            return ItemEx.decodeChaptersString(chapters).toList()
        }
    }

    data class MigrationInfo(val handle:String, val list:List<StoredFileEntry>, val deviceInfo: DeviceInfo, val host: AuthHost)
    suspend fun startMigration(host:AuthHost, deviceInfo: DeviceInfo):MigrationInfo? {
        val targetClientId = deviceInfo.clientId
        fun jsonToItems(list: JSONArray):Sequence<StoredFileEntry> {
            return sequence<StoredFileEntry> {
                for (i in 0..<list.length()) {
                    val o = list.getJSONObject(i)
                    yield(StoredFileEntry(
                        id = o.optLong("id", 0),
                        originalId = o.optString("originalId", ""),
                        ownerId = o.optString("ownerId",""),
                        slot = o.optInt("slot", 0),
                        name = o.optString("name", ""),
                        size = o.optLong("size", 0),
                        registeredDate = o.optLong("registeredDate", 0L),
                        lastModifiedDate = o.optLong("lastModifiedDate", 0L),
                        creationDate = o.optLong("creationDate", 0L),
                        metaInfo = o.optString("metaInfo", ""),
                        deleted = o.optLong("deleted", 0L),
                        extAttrDate = o.optLong("extAttrDate", 0L),
                        rating = o.optInt("rating", 0),
                        mark = o.optInt("mark", 0),
                        label = o.optString("label", ""),
                        category = o.optString("category", ""),
                        chapters = o.optString("chapters", ""),
                        duration = o.optLong("duration", 0L),
                    ))
                }
            }.filter { it.isValid }
        }
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(host.makeAuthUrl("migration/start", "n" to Settings.SecureArchive.clientId, "o" to targetClientId))
//                .url("http://${Authentication.activeHostAddress}/migration/start?auth=${Authentication.authToken}&n=${Settings.SecureArchive.clientId}&o=$targetClientId")
                .get()
                .build()
            try {
                val json = executeAndGetJsonAsync(request, host.activeHost)
                val handle = json.optString("handle")
                val list = json.getJSONArray("targets")
                MigrationInfo(handle, jsonToItems(list).toList(), deviceInfo, host)
            } catch(e:Throwable) {
                logger.error(e)
                null
            }
        }
    }
    suspend fun endMigration(migrationInfo: MigrationInfo):Boolean {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(migrationInfo.host.makeAuthUrl("migration/end", "h" to migrationInfo.handle))
//                .url("http://${Authentication.activeHostAddress}/migration/end?h=$handle")
                .get()
                .build()
            try {
                executeAndGetJsonAsync(request,migrationInfo.host.activeHost)
                true
            } catch(e:Throwable) {
                logger.error(e)
                false
            }
        }
    }

    suspend fun reportMigratedOne(migrationInfo: MigrationInfo, entry: StoredFileEntry, newId:Int): Boolean {
        return withContext(Dispatchers.IO) {
            val json = JSONObject()
                .put("handle", migrationInfo.handle)
                .put("oldOwnerId", entry.ownerId)
                .put("slot", entry.slot)
                .put("oldOriginalId", entry.originalId)
                .put("newOwnerId", Settings.SecureArchive.clientId)
                .put("newOriginalId", "$newId")
                .toString()
            val request = Request.Builder()
                .url(migrationInfo.host.makeAuthUrl("migration/exec"))
//                .url("http://${Authentication.activeHostAddress}/migration/exec?auth=${Authentication.authToken}")
                .put(json.toRequestBody("application/json".toMediaType()))
                .build()
            try {
                executeAndGetJsonAsync(request, migrationInfo.host.activeHost)
                true
            } catch(e:Throwable) {
                logger.error(e)
                false
            }

        }
    }

    suspend fun requestBackupData(host:AuthHost, address:String): Boolean {
        val json = JSONObject()
            .put("id", Settings.SecureArchive.clientId)
            .put("name", Build.MODEL)
            .put("type", "SecureCamera")
            .put("token", TcServer.updateAuthToken())
            .put("address", address)
            .put("ssl", if(Settings.Server.ssl) "true" else "false")
            .put("fp", TcServer.fingerprint)
            .toString()
        val request = Request.Builder()
            .url(host.makeAuthUrl("backup/request"))
//            .url("http://${Authentication.activeHostAddress}/backup/request")
            .put(json.toRequestBody("application/json".toMediaType()))
            .build()
        return withContext(Dispatchers.IO) {
            try {
                NetClient.executeAndGetJsonAsync(request, host.activeHost)
                ServerActivity.Companion.logger.info("backup started.")
                true
            } catch (e:Throwable) {
                ServerActivity.Companion.logger.error(e)
                false
            }
        }
    }

    /**
     * SecureArchiveにDBのバックアップを要求する。
     * SecureArchiveは、
     * - 自身のDB（SecureArchive.db）
     * - 設定情報（UserSetting.json）
     * - このクライアント(PrivateCamera)のDB（zip)
     * をまとめて、PC上に保存する（ディレクトリ指定ダイアログが表示される）
     */
    suspend fun requestBackupDB(host:AuthHost, address:String):Boolean {
        val json = JSONObject()
            .put("id", Settings.SecureArchive.clientId)
            .put("name", Build.MODEL)
            .put("type", "SecureCamera")
            .put("token", TcServer.updateAuthToken())
            .put("address", address)
            .put("ssl", if(Settings.Server.ssl) "true" else "false")
            .put("fp", TcServer.fingerprint)
            .toString()
        val request = Request.Builder()
            .url(host.makeAuthUrl("backup-db/request"))
//            .url("http://${Authentication.activeHostAddress}/backup-db/request")
            .put(json.toRequestBody("application/json".toMediaType()))
            .build()
        return withContext(Dispatchers.IO) {
            try {
                NetClient.executeAndGetJsonAsync(request, host.activeHost)
                logger.info("backup-db accepted.")
                true
            } catch (e:Throwable) {
                logger.error(e, "backup-db request rejected")
                false
            }
        }
    }
}
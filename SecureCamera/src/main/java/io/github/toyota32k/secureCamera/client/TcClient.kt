package io.github.toyota32k.secureCamera.client

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import io.github.toyota32k.dialog.UtDialog.ParentVisibilityOption
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.lib.player.model.chapter.Chapter
import io.github.toyota32k.secureCamera.client.NetClient.executeAndGetJsonAsync
import io.github.toyota32k.secureCamera.client.NetClient.executeAsync
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.db.MetaData
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.headersContentLength
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object TcClient {
    val logger = UtLog("Tc", NetClient.logger, this::class.java)

    private fun sizeInKb(size: Long): String {
        return String.format(Locale.US, "%,d KB", size / 1000L)
    }

    suspend fun registerOwnerToSecureArchive():Boolean {
//        val address = Settings.SecureArchive.address
//        if(address.isEmpty()) return false
        if(!Authentication.authenticateAndMessage()) return false

        val json = JSONObject()
            .put("id", Settings.SecureArchive.clientId)
            .put("name", Build.MODEL)
            .put("type", "SecureCamera")
            .toString()
        val request = Request.Builder()
            .url("http://${Authentication.activeHostAddress}/owner")
            .put(json.toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            NetClient.executeAndGetJsonAsync(request)
            logger.info("owner registered.")
            true
        } catch (e:Throwable) {
            logger.error(e)
            false
        }
    }

    suspend fun getPhoto(item:ItemEx): Bitmap? {
        if(!item.isPhoto) return null
        if(!Authentication.authenticateAndMessage()) return null
//        val address = Settings.SecureArchive.address
//        if(address.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(item.uri)
                .build()
            try {
                executeAsync(request, null).use { response ->
                    if (response.isSuccessful) {
                        response.body?.use { body ->
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

    suspend fun downloadFromSecureArchive(item: ItemEx):Boolean {
        return UtImmortalSimpleTask.runAsync("downloading item") {
            if(!Authentication.authenticateAndMessage()) return@runAsync false
//            val address = Settings.SecureArchive.address
//            if(address.isEmpty()) return@runAsync false

            val canceller = Canceller()
            val viewModel = ProgressDialog.ProgressViewModel.create(taskName)
            viewModel.message.value = "Downloading..."
            viewModel.cancelCommand.bindForever(canceller::cancel)
            CoroutineScope(Dispatchers.IO).launch {
                val result = downloadFromSecureArchiveAsync(item,canceller) { current, total ->
                    val percent = (current * 100L / total).toInt()
                    viewModel.progress.value = percent
                    viewModel.progressText.value = "${sizeInKb(current)} / ${sizeInKb(total)} (${percent} %)"
                }
                withContext(Dispatchers.Main) {viewModel.closeCommand.invoke(result) }
//                withContext(Dispatchers.Main) {
//                    viewModel.closeCommand.invoke(true)
//                    if(result) {
//                        val newItem = MetaDB.updateCloud(item, CloudStatus.Uploaded)
//                        (getActivity() as? PlayerActivity)?.itemUpdated(newItem.name)
//                    }
//                }
            }
            showDialog(taskName) { ProgressDialog() }
                .status
                .ok
        }
    }

    private suspend fun downloadFromSecureArchiveAsync(item: ItemEx, canceller: Canceller?, progress:((current:Long, total:Long)->Unit)?):Boolean {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(item.serverUri)
                .build()
            try {
                executeAsync(request, canceller).use { response ->
                    try {
                        if (response.isSuccessful) {
                            response.body?.use { body ->
                                body.byteStream().use { inStream ->
                                    item.file.outputStream().use { outStream ->
                                        if (progress != null) {
                                            val totalLength = response.headersContentLength()
                                            var bytesCopied: Long = 0
                                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                            var bytes = inStream.read(buffer)
                                            while (bytes >= 0) {
                                                outStream.write(buffer, 0, bytes)
                                                bytesCopied += bytes
                                                progress.invoke(bytesCopied, totalLength)
                                                bytes = inStream.read(buffer)
//                                                logger.debug("downloading: $bytesCopied / $totalLength")
                                            }
//                                            logger.debug("downloaded: $bytesCopied / $totalLength")
                                        } else {
                                            inStream.copyTo(outStream)
                                        }
                                        outStream.flush()
                                    }
                                }
                            }
                            true
                        } else {
                            logger.error(response.message)
                            false
                        }
                    } catch (e: Throwable) {
                        logger.error(e)
                        false
                    }
                }
            } catch (e:Throwable) {
                logger.error(e)
                false
            }
        }
    }

    suspend fun uploadToSecureArchive(item:ItemEx):Boolean {
        if(!Authentication.authenticateAndMessage()) return false

        return UtImmortalSimpleTask.runAsync("upload item") {
            val canceller = Canceller()
            val viewModel = ProgressDialog.ProgressViewModel.create(taskName)
            viewModel.message.value = "Uploading..."
            viewModel.cancelCommand.bindForever(canceller::cancel)
            CoroutineScope(Dispatchers.IO).launch {
                val result = uploadToSecureArchiveAsync(item,canceller) { current, total ->
                    val percent = (current * 100L / total).toInt()
                    viewModel.progress.value = percent
                    viewModel.progressText.value = "${sizeInKb(current)} / ${sizeInKb(total)} (${percent} %)"
                }
                withContext(Dispatchers.Main) { viewModel.closeCommand.invoke(result) }
            }
            showDialog(taskName) { ProgressDialog().apply{ parentVisibilityOption = ParentVisibilityOption.NONE } }.status.ok
        }
    }

    private suspend fun uploadToSecureArchiveAsync(item: ItemEx, canceller: Canceller?, progress:(current:Long, total:Long)->Unit):Boolean {
        if(!Authentication.authenticateAndMessage()) return false
        val contentType = if(item.type==0) "image/png" else "video/mp4"
        val body = ProgressRequestBody(item.file.asRequestBody(contentType.toMediaType()), progress)
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("OwnerId", Settings.SecureArchive.clientId)
            .addFormDataPart("FileDate", "${item.date}")
            .addFormDataPart("CreationDate", "${item.creationDate}")
            .addFormDataPart("OriginalId", "${item.id}")
            .addFormDataPart("MetaInfo", "")
            .addFormDataPart("ExtAttr", "${item.attrDataJson}")
            .addFormDataPart("File", item.name, body)
            .addFormDataPart("Duration", "${item.duration}")
            .build()
        val request = Request.Builder()
            .url("http://${Authentication.activeHostAddress}/upload")
            .post(multipartBody)
            .build()
        try {
//            val location:String?
            val code = executeAsync(request,canceller).use {
//                location =it.headers ["Location"]
                it.code
            }
            if(code==200) {
                logger.debug("uploaded")
                MetaDB.updateCloud(item, CloudStatus.Uploaded)
                return true
            }
            logger.error("unexpected status code: $code")
//            if(code==202) {
//                val url = "http://${address}${location}?o=${Settings.SecureArchive.clientId}&c=${item.id}"
//                logger.debug("waiting: location:${url}")
//                waitForUploaded(url, item)
//            }
        } catch(e:Throwable) {
            logger.error(e)
        }
        return false
    }

    data class RepairingItem(val id:Int, val originalId:Int, val name:String, val size:Long, val type:String, val registeredDate:Long, val lastModifiedDate:Long, val creationDate:Long, val metaInfo:String, val deleted:Int, val extAttrDate:Long, val rating:Int, val mark:Int, val label:String, val category:String, val chapters:String, val duration:Long)

    suspend fun getListForRepair():List<RepairingItem>? {
        if(!Authentication.authenticateAndMessage()) return null
        fun jsonToItems(list: JSONArray):Sequence<RepairingItem> {
            return sequence<RepairingItem> {
                for(i in 0..<list.length()) {
                    val o = list.getJSONObject(i)
                    yield(RepairingItem(
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
        return UtImmortalSimpleTask.executeAsync("list for repair") {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("http://${Authentication.activeHostAddress}/list?auth=${Authentication.authToken}&f=vp&o=${Settings.SecureArchive.clientId}")
                    .get()
                    .build()
                try {
                    val json = executeAndGetJsonAsync(request)
                    jsonToItems(json.getJSONArray("list")).toList()
                } catch(e:Throwable) {
                    null
                }
            }
        }
    }

    data class DeviceInfo(val name:String, val clientId:String)
    suspend fun getDeviceListForMigration():List<DeviceInfo>? {
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

        if(!Authentication.authenticateAndMessage()) return null
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("http://${Authentication.activeHostAddress}/migration/devices?auth=${Authentication.authToken}&o=${Settings.SecureArchive.clientId}")
                .get()
                .build()
            try {
                val json = executeAndGetJsonAsync(request)
                jsonToItems(json.getJSONArray("list")).toList()
            } catch(e:Throwable) {
                null
            }
        }
    }

    data class StoredFileEntry(
        val id:Long,
        val originalId:String,
        val ownerId:String,
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

    data class MigrationInfo(val handle:String, val list:List<StoredFileEntry>)
    suspend fun startMigration(targetClientId:String):MigrationInfo? {
        fun jsonToItems(list: JSONArray):Sequence<StoredFileEntry> {
            return sequence<StoredFileEntry> {
                for (i in 0..<list.length()) {
                    val o = list.getJSONObject(i)
                    yield(StoredFileEntry(
                        id = o.optLong("id", 0),
                        originalId = o.optString("originalId", ""),
                        ownerId = o.optString("ownerId",""),
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
        if(!Authentication.authenticateAndMessage()) return null
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("http://${Authentication.activeHostAddress}/migration/start?auth=${Authentication.authToken}&o=${Settings.SecureArchive.clientId}&n=$targetClientId")
                .get()
                .build()
            try {
                val json = executeAndGetJsonAsync(request)
                val handle = json.optString("handle")
                val list = json.getJSONArray("targets")
                MigrationInfo(handle, jsonToItems(list).toList())
            } catch(e:Throwable) {
                null
            }
        }
    }
    suspend fun endMigration(handle:String):Boolean {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("http://${Authentication.activeHostAddress}/migration/end?h=$handle")
                .get()
                .build()
            try {
                executeAndGetJsonAsync(request)
                true
            } catch(e:Throwable) {
                false
            }
        }
    }

    suspend fun reportMigratedOne(handle:String, entry: StoredFileEntry, newId:Int): Boolean {
        if(!Authentication.authenticateAndMessage()) return false
        return withContext(Dispatchers.IO) {
            val json = JSONObject()
                .put("handle", handle)
                .put("oldOwnerId", entry.ownerId)
                .put("oldOriginalId", entry.originalId)
                .put("newOwnerId", Settings.SecureArchive.clientId)
                .put("newOriginalId", "$newId")
                .toString()
            val request = Request.Builder()
                .url("http://${Authentication.activeHostAddress}/migration/exec?auth=${Authentication.authToken}")
                .put(json.toRequestBody("application/json".toMediaType()))
                .build()
            try {
                executeAndGetJsonAsync(request)
                true
            } catch(e:Throwable) {
                false
            }

        }
    }
}
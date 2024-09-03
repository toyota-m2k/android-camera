package io.github.toyota32k.secureCamera.client

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import io.github.toyota32k.dialog.UtDialog.ParentVisibilityOption
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.secureCamera.client.NetClient.executeAsync
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.MetaDB
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
                                            }
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

//    private fun waitForUploaded(url:String, item: ItemEx) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                var cont = true
//                while (cont) {
//                    delay(1000)
//                    val locationReq = Request.Builder().url(url).get().build()
//                    NetClient.executeAsync(locationReq).use { result -> //client.newCall(locationReq).executeAsync(null)
//                        when (result.code) {
//                            StatusCode.Ok.code -> {
//                                // upload completed
//                                logger.debug("uploaded")
//                                val newItem = MetaDB.updateCloud(item, CloudStatus.Uploaded)
//                                withContext(Dispatchers.Main) {
//                                    val activity = UtImmortalTaskManager.mortalInstanceSource.getOwner().asActivity() as? PlayerActivity
//                                    if(activity!=null) {
//                                        activity.itemUpdated(newItem.name)
//                                    }
//                                }
//                                cont = false
//                            }
//
//                            StatusCode.Accepted.code -> {
//                                val body = result.body?.use { it.string() }
//                                    ?: throw IllegalStateException("Server Response No Data.")
//                                val json = JSONObject(body)
//                                val total = json.optLong("total")
//                                val current = json.optLong("current")
//                                logger.debug("registering on server: $current / $total")
//                            }
//
//                            else -> {
//                                // error
//                                logger.debug("error response: ${result.code}")
//                                cont = false
//                            }
//                        }
//                    }
//                }
//            } catch(e:Throwable) {
//                logger.error(e)
//            }
//        }
//    }
}
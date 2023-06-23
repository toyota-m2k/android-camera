package io.github.toyota32k.secureCamera.client

import android.content.Context
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.secureCamera.PlayerActivity
import io.github.toyota32k.secureCamera.SCApplication
import io.github.toyota32k.secureCamera.client.NetClient.executeAsync
import io.github.toyota32k.secureCamera.client.NetClient.logger
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.db.MetaData
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.server.response.StatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import kotlin.time.Duration

object TcClient {
    private fun sizeInKb(size: Long): String {
        return String.format("%,d KB", size / 1000L)
    }

    fun uploadToSecureArchive(item:ItemEx) {
        UtImmortalSimpleTask.run("upload item") {
            val canceller = Canceller()
            val viewModel = ProgressDialog.ProgressViewModel.create(taskName)
            viewModel.message.value = "Uploading..."
            viewModel.cancelCommand.bindForever(canceller::cancel)
            CoroutineScope(Dispatchers.IO).launch {
                uploadToSecureArchiveAsync(item,canceller) { current, total ->
                    val percent = (current * 100L / total).toInt()
                    viewModel.progress.value = percent
                    viewModel.progressText.value = "${sizeInKb(current)} / ${sizeInKb(total)} (${percent} %)"
                }
                withContext(Dispatchers.Main) { viewModel.closeCommand.invoke(true) }
            }
            showDialog(taskName) { ProgressDialog() }
            true
        }
    }
    private suspend fun uploadToSecureArchiveAsync(item: ItemEx, canceller: Canceller?, progress:(current:Long, total:Long)->Unit) {
        val address = Settings.SecureArchive.address
        if(address.isEmpty()) return

        val contentType = if(item.type==0) "image/png" else "video/mp4"
        val body = ProgressRequestBody(item.file.asRequestBody(contentType.toMediaType()), progress)
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("OwnerId", Settings.SecureArchive.clientId)
            .addFormDataPart("FileDate", "${item.date}")
            .addFormDataPart("OriginalId", "${item.id}")
            .addFormDataPart("MetaInfo", "")
            .addFormDataPart("File", item.name, body)
            .build()
        val request = Request.Builder()
            .url("http://${address}/upload")
            .post(multipartBody)
            .build()
        try {
            val location:String?
            val code = NetClient.executeAsync(request,canceller).use {
                location =it.headers ["Location"]
                it.code
            }
            if(code==200) {
                logger.debug("uploaded")
                return
            }
            if(code==202) {
//                val location = result.headers["Location"]
                val url = "http://${address}${location}?o=${Settings.SecureArchive.clientId}&c=${item.id}"
                logger.debug("waiting: location:${url}")
                waitForUploaded(url, item)

//                val client = OkHttpClient.Builder().build()
//                while(result.code==202) {
//                    delay(1000)
//                    val locationReq = Request.Builder().url(url).get().build()
//                    result = NetClient.executeAsync(locationReq) //client.newCall(locationReq).executeAsync(null)
//                    if(result.code == 200) {
//                        return true
//                    }
//                    else if(result.code == 202) {
//                        val body = result.body?.use { it.string() }
//                            ?: throw IllegalStateException("Server Response No Data.")
//                        val json = JSONObject(body)
//                        val total = json.optLong("total")
//                        val current = json.optLong("current")
//                        progress(current, total)
//                    }
//                }
            }
//            return result.isSuccessful
        } catch(e:Throwable) {
            NetClient.logger.error(e)
            return
        }
    }

    private fun waitForUploaded(url:String, item: ItemEx) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var cont = true
                while (cont) {
                    delay(1000)
                    val locationReq = Request.Builder().url(url).get().build()
                    NetClient.executeAsync(locationReq).use { result -> //client.newCall(locationReq).executeAsync(null)
                        when (result.code) {
                            StatusCode.Ok.code -> {
                                // upload completed
                                logger.debug("uploaded")
                                val newItem = MetaDB.updateCloud(item, CloudStatus.Uploaded)
                                withContext(Dispatchers.Main) {
                                    val activity = UtImmortalTaskManager.mortalInstanceSource.getOwner().asActivity() as? PlayerActivity
                                    if(activity!=null) {
                                        activity.itemUpdated(newItem.name)
                                    }
                                }
                                cont = false
                            }

                            StatusCode.Accepted.code -> {
                                val body = result.body?.use { it.string() }
                                    ?: throw IllegalStateException("Server Response No Data.")
                                val json = JSONObject(body)
                                val total = json.optLong("total")
                                val current = json.optLong("current")
                                logger.debug("registering on server: $current / $total")
                            }

                            else -> {
                                // error
                                logger.debug("error response: ${result.code}")
                                cont = false
                            }
                        }
                    }
                }
            } catch(e:Throwable) {
                logger.error(e)
            }
        }
    }
}
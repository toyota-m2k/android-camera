package io.github.toyota32k.secureCamera.client

import android.content.Context
import io.github.toyota32k.secureCamera.client.NetClient.executeAsync
import io.github.toyota32k.secureCamera.client.NetClient.logger
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.settings.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject

object TcClient {
    suspend fun uploadToSecureArchive(context: Context, item: ItemEx, canceller: Canceller?, progress:(current:Long, total:Long)->Unit):Boolean {
        val address = Settings.SecureArchive.address
        if(address.isEmpty()) return false

        val contentType = if(item.type==0) "image/png" else "video/mp4"
        val body = ProgressRequestBody(item.file(context).asRequestBody(contentType.toMediaType()), progress)
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
            var result = NetClient.executeAsync(request,canceller)
            if(result.code==200) {
                return true
            }
            if(result.code==202) {
                val location = result.headers["Location"]
                val url = "http://${address}${location}"
                logger.debug("location:${url}")
//                val client = OkHttpClient.Builder().build()
                while(result.code==202) {
                    val locationReq = Request.Builder().url(url).get().build()
                    result = NetClient.executeAsync(locationReq) //client.newCall(locationReq).executeAsync(null)
                    if(result.code == 200) {
                        return true
                    }
                    else if(result.code == 202) {
                        val body = result.body?.use { it.string() }
                            ?: throw IllegalStateException("Server Response No Data.")
                        val json = JSONObject(body)
                        val total = json.optLong("total")
                        val current = json.optLong("current")
                        progress(current, total)
                    }
                }
            }
            return result.isSuccessful
        } catch(e:Throwable) {
            NetClient.logger.error(e)
            return false
        }
    }
}
package io.github.toyota32k.secureCamera.client

import android.content.Context
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.settings.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

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
            val result = NetClient.executeAsync(request,canceller)
            return result.isSuccessful
        } catch(e:Throwable) {
            NetClient.logger.error(e)
            return false
        }
    }
}
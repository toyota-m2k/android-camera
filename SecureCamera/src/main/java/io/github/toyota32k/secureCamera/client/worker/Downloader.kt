package io.github.toyota32k.secureCamera.client.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.toyota32k.media.lib.converter.IAwaiter
import io.github.toyota32k.secureCamera.client.Canceller
import io.github.toyota32k.secureCamera.client.NetClient
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import okhttp3.Request
import java.io.File
import java.util.UUID
import kotlinx.coroutines.ensureActive

object Downloader : ProgressWorkerProcessor() {
    const val KEY_ITEM_ID = "itemId"
    const val KEY_URL = "url"
    const val KEY_FILE_PATH = "path"
    val logger = UtLog("Worker")

    open class DLWorker(context: Context, params: WorkerParameters) : ProgressWorker(context, params) {
        open suspend fun onDownloaded(itemId:Int) {

        }

        override suspend fun doWork(): Result {
            val canceller = Canceller()
            try {
                val url = inputData.getString(KEY_URL) ?: return Result.failure()
                val file = File(inputData.getString(KEY_FILE_PATH) ?: return Result.failure())
                val request = Request.Builder()
                    .url(url)
                    .build()
                return NetClient.executeAsync(request, canceller).use { response ->
                        if (response.isSuccessful) {
                            response.body?.use { body ->
                                val totalLength =
                                    response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                                body.byteStream().use { inStream ->
                                    file.outputStream().use { outStream ->
                                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                        var bytesCopied: Long = 0
                                        var bytes = inStream.read(buffer)

                                        while (bytes >= 0) {
                                            currentCoroutineContext().ensureActive()
//                                            if(!currentCoroutineContext().isActive) {
//                                                throw CancellationException()
//                                            }
                                            outStream.write(buffer, 0, bytes)
                                            bytesCopied += bytes

                                            // 進捗をSetProgressに設定
                                            progress(bytesCopied, totalLength)
                                            bytes = inStream.read(buffer)
                                        }
                                        outStream.flush()
                                    }
                                }
                            }
                            logger.info("completed")
                            onDownloaded(inputData.getInt(KEY_ITEM_ID, -1))
                            Result.success()
                        } else {
                            logger.error(response.message)
                            Result.failure()
                        }
                    }
            } catch (_: CancellationException) {
                logger.info("cancelled")
                canceller.cancel()
                return Result.failure()
            } catch (e: Throwable) {
                logger.error(e)
                canceller.cancel()
                return Result.failure()
            }
        }
    }

    inline fun <reified T:DLWorker> download(context:Context, itemId:Int, url:String, path:String, noinline progress:((current:Long, total:Long)->Unit)?):IAwaiter<Boolean> {
        return process<T>(context,
            workDataOf(
                KEY_URL to url,
                KEY_FILE_PATH to path,
                KEY_ITEM_ID to itemId,
            ), progress)
    }
}
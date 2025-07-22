package io.github.toyota32k.secureCamera.client.worker

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.client.Canceller
import io.github.toyota32k.secureCamera.client.NetClient
import io.github.toyota32k.utils.android.IWorkerAwaiter
import io.github.toyota32k.utils.android.ProgressWorker
import io.github.toyota32k.utils.android.ProgressWorkerGenerator
import io.github.toyota32k.utils.android.WorkerParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Request
import java.io.File

class Downloader(context: Context, params: WorkerParameters) : ProgressWorker(context, params) {
    companion object {
        val logger = UtLog("Worker")

        fun download(context:Context, itemId:Int, url:String, path:String, progress:(current:Long, total:Long)->Unit):IWorkerAwaiter {
            logger.info("download: $itemId, $url, $path")
            return ProgressWorkerGenerator
                .builder<Downloader>()
                .setInputData(DLTargetParams.produce(itemId, url, path))
                .onProgress(progress)
                .build(context)
        }
    }

    private class DLTargetParams(inputData: Data?) : WorkerParams(inputData) {
        var itemId:Int by delegate.intMinusOne
        var url:String? by delegate.stringNullable
        var filePath:String? by delegate.stringNullable
        companion object {
            fun produce(itemId:Int, url:String, filePath:String):Data {
                return DLTargetParams(null).apply {
                    this.itemId = itemId
                    this.url = url
                    this.filePath = filePath
                }.produce()
            }
        }
    }

    private fun error(message:String):Result {
        logger.error(message)
        return Result.failure(workDataOf("error" to message))
    }

    override suspend fun doWork(): Result {
        val canceller = Canceller()
        try {
            var target = DLTargetParams(inputData)
            val url = target.url ?: return error("no url")
            val file = File(target.filePath ?: return error("no file path"))
            val request = Request.Builder()
                .url(url)
                .build()
            return NetClient.executeAsync(request, canceller).use { response ->
                    if (response.isSuccessful) {
                        response.body?.use { body ->
                            val totalLength = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                            body.byteStream().use { inStream ->
                                file.outputStream().use { outStream ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var bytesCopied: Long = 0
                                    var bytes = inStream.read(buffer)

                                    while (bytes >= 0) {
                                        currentCoroutineContext().ensureActive()
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
                        Result.success()
                    } else {
                        logger.error(response.message)
                        error(response.message)
                    }
                }
        } catch (_: CancellationException) {
            logger.info("cancelled")
            canceller.cancel()
            return error("cancelled")
        } catch (e: Throwable) {
            logger.error(e)
            canceller.cancel()
            return error(e.message ?: "unknown error")
        }
    }
}
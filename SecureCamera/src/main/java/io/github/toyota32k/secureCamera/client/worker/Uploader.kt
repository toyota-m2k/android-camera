package io.github.toyota32k.secureCamera.client.worker

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.toyota32k.secureCamera.client.Canceller
import io.github.toyota32k.secureCamera.client.NetClient.executeAsync
import io.github.toyota32k.secureCamera.client.ProgressRequestBody
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.client.worker.Uploader.ULWorker
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.IAwaiter
import io.github.toyota32k.utils.ProgressWorker
import io.github.toyota32k.utils.ProgressWorkerProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

object Uploader : ProgressWorkerProcessor() {
    const val KEY_CONTENT_TYPE = "contentType"
    const val KEY_OWNER_ID = "ownerId"
    const val KEY_FILE_DATE = "fileDate"
    const val KEY_CREATION_DATE = "creationDate"
    const val KEY_ORIGINAL_ID = "originalId"
    const val KEY_META_INFO = "metaInfo"
    const val KEY_EXT_ATTR = "extAttr"
    const val KEY_ITEM_NAME = "name"
    const val KEY_FILE_PATH = "path"
    const val KEY_DURATION = "duration"

    fun workDataFromItem(item: ItemEx):Data {
        return workDataOf(
            KEY_CONTENT_TYPE to if(item.isPhoto) "image/png" else "video/mp4",
            KEY_OWNER_ID to Settings.SecureArchive.clientId,
            KEY_FILE_DATE to "${item.date}",
            KEY_CREATION_DATE to "${item.creationDate}",
            KEY_ORIGINAL_ID to "${item.id}",
            KEY_META_INFO to "",
            KEY_EXT_ATTR to "${item.attrDataJson}",
            KEY_ITEM_NAME to item.name,
            KEY_FILE_PATH to item.file.absolutePath,
            KEY_DURATION to "${item.duration}",
        )
    }

    open class ULWorker(context: Context, params: WorkerParameters) : ProgressWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!Authentication.authenticateAndMessage()) return Result.failure()
            val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
            val file = File(filePath)
            if (!file.exists()) {
                logger.error("file not found: $filePath")
                return Result.failure()
            }
            val contentType = inputData.getString(KEY_CONTENT_TYPE) ?: return Result.failure()
            val ownerId = inputData.getString(KEY_OWNER_ID) ?: return Result.failure()
            val fileDate = inputData.getString(KEY_FILE_DATE) ?: return Result.failure()
            val creationDate = inputData.getString(KEY_CREATION_DATE) ?: return Result.failure()
            val originalId = inputData.getString(KEY_ORIGINAL_ID) ?: return Result.failure()
            val metaInfo = inputData.getString(KEY_META_INFO) ?: ""
            val extAttr = inputData.getString(KEY_EXT_ATTR) ?: ""
            val itemName = inputData.getString(KEY_ITEM_NAME) ?: ""
            val duration = inputData.getString(KEY_DURATION) ?: ""
            val canceller = Canceller()
            val workerContext = currentCoroutineContext()
            var cancelled = false
            return try {
                val body =
                    ProgressRequestBody(file.asRequestBody(contentType.toMediaType())) { current, total ->
                        runBlocking {
                            // ここで例外をスローしたら Call がいい感じに例外を投げてキャンセルされるかと期待したが、
                            // なにやら大切なスレッドが死ぬためかアプリが落ちてしまう。
                            // workerContext.ensureActive()
                            if(!workerContext.isActive) {
                                logger.info("cancelling")
                                cancelled = true
                                canceller.cancel()
                                return@runBlocking
                            }
                            progress(current, total)
                            logger.info("progress: $current / $total")
                        }
                    }
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("OwnerId", ownerId)
                    .addFormDataPart("FileDate", fileDate)
                    .addFormDataPart("CreationDate", creationDate)
                    .addFormDataPart("OriginalId", originalId)
                    .addFormDataPart("MetaInfo", metaInfo)
                    .addFormDataPart("ExtAttr", extAttr)
                    .addFormDataPart("File", itemName, body)
                    .addFormDataPart("Duration", duration)
                    .build()
                val request = Request.Builder()
                    .url("http://${Authentication.activeHostAddress}/upload")
                    .post(multipartBody)
                    .build()
                val code = executeAsync(request, canceller).use {
                    it.code
                }
                if (code == 200) {
                    logger.debug("uploaded")
                    MetaDB.updateCloud(originalId.toInt(), CloudStatus.Uploaded)
                    Result.success()
                } else {
                    logger.error("unexpected status code: $code")
                    Result.failure()
                }
            } catch (_: CancellationException) {
                logger.info("cancelled")
                canceller.cancel()
                Result.failure()
            } catch (e: Throwable) {
                if(cancelled) {
                    logger.info("cancelled: ${e.message}")
                } else {
                    logger.error(e)
                }
                canceller.cancel()
                Result.failure()
            }
        }
    }

    fun upload(context: Context, item:ItemEx, progress:((current:Long, total:Long)->Unit)?):IAwaiter<Boolean> {
        return process<ULWorker>(context, workDataFromItem(item), progress)
    }
}
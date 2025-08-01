package io.github.toyota32k.secureCamera.client.worker

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import io.github.toyota32k.secureCamera.client.Canceller
import io.github.toyota32k.secureCamera.client.NetClient.executeAsync
import io.github.toyota32k.secureCamera.client.ProgressRequestBody
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.db.ScDB
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.worker.IWorkerAwaiter
import io.github.toyota32k.utils.worker.ProgressWorker
import io.github.toyota32k.utils.worker.ProgressWorkerGenerator
import io.github.toyota32k.utils.worker.WorkerParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

//class Uploader_Org(context: Context, params: WorkerParameters) : ProgressWorker(context, params) {
//    companion object {
//        val logger get() = Downloader_Org.logger
//
//        fun upload(context:Context, db:ScDB, item: ItemEx, progress:(current:Long, total:Long)->Unit): IWorkerAwaiter {
//            logger.info("upload: ${item.id}, ${item.name}")
//            return ProgressWorkerGenerator
//                .builder<Uploader_Org>()
//                .setInputData(ULTargetParams.produce(db, item))
//                .onProgress(progress)
//                .build(context)
//        }
//    }
//    private class ULTargetParams(inputData: Data?=null) : WorkerParams(inputData) {
//        var contentType:String by delegate.string
//        var ownerId:String by delegate.string
//        var slot:Int by delegate.intZero
//        var fileDate:Long by delegate.longZero
//        var creationDate:Long by delegate.longZero
//        var originalId:Int by delegate.intZero
//        var metaInfo:String by delegate.string
//        var extAttr:String by delegate.string
//        var itemName:String by delegate.string
//        var filePath:String by delegate.string
//        var duration:Long by delegate.longZero
//
//        constructor(db:ScDB, item: ItemEx):this() {
//            contentType = if(item.isPhoto) "image/png" else "video/mp4"
//            ownerId = Settings.SecureArchive.clientId
//            slot = item.slot
//            fileDate = item.date
//            creationDate = item.creationDate
//            originalId = item.id
//            metaInfo = ""
//            extAttr = "${item.attrDataJson}"
//            itemName = item.name
//            filePath = db.fileOf(item).absolutePath
//            duration = item.duration
//        }
//        companion object {
//            fun produce(db:ScDB, item: ItemEx):Data {
//                return ULTargetParams(db, item).produce()
//            }
//            fun consume(inputData: Data):ULTargetParams {
//                return ULTargetParams(inputData)
//            }
//        }
//    }
//
//    override suspend fun doWork(): Result {
//        val target = ULTargetParams.consume(inputData)
//        val file = File(target.filePath)
//        if (!file.exists()) {
//            logger.error("file not found: ${target.filePath}")
//            return Result.failure()
//        }
//
//        val canceller = Canceller()
//        val workerContext = currentCoroutineContext()
//        var cancelled = false
//        return try {
//            val body = ProgressRequestBody(file.asRequestBody(target.contentType.toMediaType())) { current, total ->
//                    runBlocking {
//                        // ここで例外をスローしたら Call がいい感じに例外を投げてキャンセルされるかと期待したが、
//                        // なにやら大切なスレッドが死ぬためかアプリが落ちてしまう。
//                        // workerContext.ensureActive()
//                        if (!workerContext.isActive) {
//                            logger.info("cancelling")
//                            cancelled = true
//                            canceller.cancel()
//                            return@runBlocking
//                        }
//                        progress(current, total)
//                        logger.info("progress: $current / $total")
//                    }
//                }
//            val multipartBody = MultipartBody.Builder()
//                .setType(MultipartBody.FORM)
//                .addFormDataPart("OwnerId", target.ownerId)
//                .addFormDataPart("Slot", target.slot.toString())
//                .addFormDataPart("FileDate", "${target.fileDate}")
//                .addFormDataPart("CreationDate", "${target.creationDate}")
//                .addFormDataPart("OriginalId", "${target.originalId}")
//                .addFormDataPart("MetaInfo", target.metaInfo)
//                .addFormDataPart("ExtAttr", target.extAttr)
//                .addFormDataPart("Duration", "${target.duration}")
//                .addFormDataPart("Size", "${file.length()}")
//                .addFormDataPart("File", target.itemName, body)
//                .build()
//            val request = Request.Builder()
//                .url("http://${Authentication.activeHostAddress}/slot${target.slot}/upload")
//                .post(multipartBody)
//                .build()
//            val code = executeAsync(request, canceller).use {
//                it.code
//            }
//            if (code == 200) {
//                logger.debug("uploaded")
//                MetaDB.withDB { db -> db.updateCloud(target.originalId, CloudStatus.Uploaded) }
//                Result.success()
//            } else {
//                logger.error("unexpected status code: $code")
//                Result.failure()
//            }
//        } catch (_: CancellationException) {
//            logger.info("cancelled")
//            canceller.cancel()
//            Result.failure()
//        } catch (e: Throwable) {
//            if (cancelled) {
//                logger.info("cancelled: ${e.message}")
//            } else {
//                logger.error(e)
//            }
//            canceller.cancel()
//            Result.failure()
//        }
//    }
//}
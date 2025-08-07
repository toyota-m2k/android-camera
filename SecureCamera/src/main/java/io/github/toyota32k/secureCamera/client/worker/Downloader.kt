package io.github.toyota32k.secureCamera.client.worker

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.client.Canceller
import io.github.toyota32k.secureCamera.client.NetClient
import io.github.toyota32k.secureCamera.client.TcClient.sizeInKb
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.settings.SlotIndex
import io.github.toyota32k.utils.worker.UtTaskWorker
import io.github.toyota32k.utils.worker.WorkerParams
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class Downloader(context: Context, params: WorkerParameters) : UtTaskWorker(context, params) {
    companion object {
        val logger = UtLog("Downloader", null, Downloader::class.java)
        val exclusiveRunner = ExclusiveRunner(logger, "Downloader")
        fun download(context: Context, item: ItemEx, filePath: String, updateFileStatus: Boolean) {
            val data = DLTargetParams(item, filePath, updateFileStatus).produce()
            executeOneTimeWorker<Downloader>(context, data)
            logger.debug("request accepted: slot=${item.slot}, itemId=${item.id}, url=${item.serverUri}, filePath=$filePath, updateFileStatus=$updateFileStatus")
        }

        fun File.safeDelete() {
            try {
                if (exists()) {
                    delete()
                }
            } catch (e: Throwable) {
                logger.error(e, "failed to delete file: ${this.absolutePath}")
            }
        }
    }

    private class DLTargetParams(inputData: Data?) : WorkerParams(inputData) {
        var slot: Int by delegate.intMinusOne
        var itemId: Int by delegate.intMinusOne
        var url: String by delegate.string
        var name: String by delegate.string
        var filePath: String? by delegate.stringNullable
        var updateFileStatus: Boolean by delegate.booleanFalse

        constructor(item: ItemEx, filePath: String, updateFileStatus: Boolean) : this(null) {
            this.slot = item.slot
            this.itemId = item.id
            this.url = item.serverUri
            this.name = item.name
            this.filePath = filePath
            this.updateFileStatus = updateFileStatus
        }

//        companion object {
//            fun produce(slot:Int, itemId:Int, url:String, filePath:String, updateFileStatus:Boolean):Data {
//                return DLTargetParams(null).apply {
//                    this.slot = slot
//                    this.itemId = itemId
//                    this.url = url
//                    this.filePath = filePath
//                    this.updateFileStatus = updateFileStatus
//                }.produce()
//            }
//        }
    }

    override suspend fun doWork(): Result {
        val target = DLTargetParams(inputData)
        if (!Authentication.authenticateAndMessage()) return error("not authenticated")
        val itemId = target.itemId
        val slot = target.slot
        if (slot < 0 || itemId < 0) return error("invalid item")
        exclusiveRunner.run(slot, itemId) {
            MetaDB[SlotIndex.fromIndex(slot)].use { db ->
                val file = File(target.filePath ?: return error("no file path"))
                val tempFile = File(file.parent, "${file.nameWithoutExtension}.tmp")
                if (tempFile.exists()) {
                    logger.info("removing existing temp file: ${tempFile.absolutePath}")
                    tempFile.safeDelete()
                }
                val canceller = Canceller()
                val modeless = showModelessDialog<ProgressDialog.ProgressViewModel>(exclusiveRunner.key(target.slot, target.itemId)) { ProgressDialog() } ?: return error("failed to show modeless dialog")
                modeless.executeOn { vm->
                    vm.title.value = "SecureCamera Downloader"
                    vm.message.value = target.name
                    vm.cancelCommand.bindForever {
                        canceller.cancel()
                    }

                    try {
                        val request = Request.Builder()
                            .url(target.url)
                            .build()
                        NetClient.executeAsync(request, canceller).use { response ->
                            if (response.isSuccessful) {
                                response.body.use { body ->
                                    val totalLength =
                                        response.headers["Content-Length"]?.toLongOrNull() ?: 0L
                                    body.byteStream().use { inStream ->
                                        tempFile.outputStream().use { outStream ->
                                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                            var bytesCopied: Long = 0
                                            var bytes = inStream.read(buffer)

                                            while (bytes >= 0) {
                                                currentCoroutineContext().ensureActive()
                                                outStream.write(buffer, 0, bytes)
                                                bytesCopied += bytes

                                                // 進捗をSetProgressに設定
                                                if (totalLength > 0) {
                                                    val percent = vm.setProgress(bytesCopied, totalLength)
                                                    // 通知にも追加
                                                    notifyProgress( bytesCopied == totalLength,percent)
                                                }
                                                bytes = inStream.read(buffer)
                                            }
                                            outStream.flush()
                                        }
                                    }
                                }
                                logger.info("downloaded")
                                // ダウンロードが成功したらデータファイルを上書き
                                // （元のファイルがあれば削除してからリネーム）
                                file.safeDelete()
                                tempFile.renameTo(file)
                                assert(!tempFile.exists())
                                db.updateCloud(
                                    itemId,
                                    CloudStatus.Uploaded,
                                    target.updateFileStatus
                                )
                                logger.info("download task completed")
                            } else {
                                logger.error(response.message)
                                throw Exception("download failed: ${response.message}")
                            }
                        }
                    } catch (e: Throwable) {
                        if (e is CancellationException) {
                            logger.info("cancelled")
                        } else {
                            logger.error(e, "download failed")
                        }
                    } finally {
                        tempFile.safeDelete()
                    }
                }
            }
        }
        return succeeded()
    }
}

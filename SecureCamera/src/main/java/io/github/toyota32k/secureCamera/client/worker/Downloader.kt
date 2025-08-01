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
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.settings.SlotIndex
import io.github.toyota32k.utils.worker.ForegroundWorker
import io.github.toyota32k.utils.worker.WorkerParams
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class Downloader(context: Context, params: WorkerParameters) : ForegroundWorker(context, params) {
    companion object {
        val logger = UtLog("Downloader", null, Downloader::class.java)
        fun download(context:Context, slot: Int, itemId: Int, url: String, filePath: String, updateFileStatus:Boolean) {
            val data = DLTargetParams.produce(slot, itemId, url, filePath, updateFileStatus)
            executeOneTimeWorker<Downloader>(context, data)
            logger.debug("request accepted: slot=$slot, itemId=$itemId, url=$url, filePath=$filePath, updateFileStatus=$updateFileStatus")
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
        var itemId:Int by delegate.intMinusOne
        var url:String? by delegate.stringNullable
        var filePath:String? by delegate.stringNullable
        var updateFileStatus:Boolean by delegate.booleanFalse

        companion object {
            fun produce(slot:Int, itemId:Int, url:String, filePath:String, updateFileStatus:Boolean):Data {
                return DLTargetParams(null).apply {
                    this.slot = slot
                    this.itemId = itemId
                    this.url = url
                    this.filePath = filePath
                    this.updateFileStatus = updateFileStatus
                }.produce()
            }
        }
    }

    override suspend fun doWork(): Result {
        val target = DLTargetParams(inputData)
        if (!Authentication.authenticateAndMessage()) return error("not authenticated")
        val itemId = target.itemId
        val slot = target.slot
        if (slot < 0 || itemId < 0) return error("invalid item")
        MetaDB[SlotIndex.fromIndex(slot)].use { db ->
            val url = target.url ?: return error("no url")
            val file = File(target.filePath ?: return error("no file path"))
            val tempFile = File(file.parent, "${file.nameWithoutExtension}.tmp")
            if (tempFile.exists()) {
                logger.info("removing existing temp file: ${tempFile.absolutePath}")
                tempFile.safeDelete()
            }
            val canceller = Canceller()
            val viewModel = showModelessDialog<ProgressDialog.ProgressViewModel>("download worker task") { vm->
                vm.message.value = "Downloading..."
                vm.cancelCommand.bindForever { canceller.cancel() }
                ProgressDialog()
            }

            try {
                val request = Request.Builder()
                    .url(url)
                    .build()
                return NetClient.executeAsync(request, canceller).use { response ->
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
                                        viewModel.setProgress(bytesCopied, totalLength)
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
                        db.updateCloud(itemId, CloudStatus.Uploaded, target.updateFileStatus)
                        logger.info("download task completed")
                        Result.success()
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
                return error(e.message ?: "unknown error")
            } finally {
                tempFile.safeDelete()
                MainScope().launch {
                    // ダイアログを閉じる
                    viewModel.closeCommand.invoke(true)
                }
            }
        }
    }
}
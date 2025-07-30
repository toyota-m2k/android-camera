package io.github.toyota32k.secureCamera.client.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.secureCamera.client.Canceller
import io.github.toyota32k.secureCamera.client.NetClient
import io.github.toyota32k.secureCamera.client.TcClient.sizeInKb
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.client.worker.Downloader.Companion.logger
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.settings.SlotIndex
import io.github.toyota32k.utils.FlowableEvent
import io.github.toyota32k.utils.UtResetableEvent
import io.github.toyota32k.utils.android.ProgressWorker
import io.github.toyota32k.utils.android.WorkerParams
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Request
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.text.toLongOrNull

class Downloader2(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private inline fun <reified T: CoroutineWorker> executeOneTimeWorker(context: Context, data: Data) {
            val req = OneTimeWorkRequest.Builder(T::class.java)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
        fun download(context:Context, slot: Int, itemId: Int, url: String, filePath: String, updateFileStatus:Boolean) {
            val data = DLTargetParams.produce(slot, itemId, url, filePath, updateFileStatus)
            executeOneTimeWorker<Downloader2>(context, data)
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

    private fun error(message:String):Result {
        logger.error(message)
        return Result.failure(workDataOf("error" to message))
    }

    /**
     * モードレスダイアログを表示する。
     */
    suspend inline fun <reified T:UtDialogViewModel, D:UtDialog> showModelessDialog(taskName:String, noinline fn:(vm:T)->UtDialog):T {
        val event = FlowableEvent()
        var vm:T? = null
        UtImmortalTask.launchTask(taskName) {
            vm = UtDialogViewModel.create(T::class.java, this)
            val dlg = fn(vm)
            event.set()
            showDialog(this.taskName) { dlg }
        }
        event.waitOne()
        if (vm==null) throw IllegalStateException("view model is null")
        return vm
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
            val canceller = Canceller()
            var viewModel = showModelessDialog<ProgressDialog.ProgressViewModel, ProgressDialog>("download worker task") { vm->
                vm.message.value = "Downloading..."
                vm.cancelCommand.bindForever { canceller.cancel() }
                ProgressDialog()
            }

            try {
                val progress = { current: Long, total: Long ->
                    val percent = if (total == 0L) 0 else (current * 100L / total).toInt()
                    viewModel.progress.value = percent
                    viewModel.progressText.value =
                        "${sizeInKb(current)} / ${sizeInKb(total)} (${percent} %)"
                }
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
                        db.updateCloud(itemId, CloudStatus.Uploaded, target.updateFileStatus)
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
}
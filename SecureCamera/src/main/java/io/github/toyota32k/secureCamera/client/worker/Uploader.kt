package io.github.toyota32k.secureCamera.client.worker

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import io.github.toyota32k.dialog.UtDialog.ParentVisibilityOption
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.client.Canceller
import io.github.toyota32k.secureCamera.client.NetClient.executeAsync
import io.github.toyota32k.secureCamera.client.ProgressRequestBody
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.settings.SlotIndex
import io.github.toyota32k.utils.worker.ForegroundWorker
import io.github.toyota32k.utils.worker.WorkerParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

class Uploader(context: Context, params: WorkerParameters) : ForegroundWorker(context, params) {
    companion object {
        val logger = UtLog("Uploader", null, Uploader::class.java)
        fun upload(context: Context, slotIndex: SlotIndex, itemId: Int) {
            val data = ULTarget(slotIndex, itemId).produce()
            executeOneTimeWorker<Uploader>(context, data)
            logger.debug("request accepted: slot=$slotIndex, itemId=$itemId")
        }
    }

    class ULTarget(inputData: Data?) : WorkerParams(inputData) {
        constructor() : this(null)
        constructor(slotIndex: SlotIndex, itemId: Int) : this() {
            this.slot = slotIndex.index
            this.itemId = itemId
        }

        var slot: Int by delegate.intMinusOne
        var itemId: Int by delegate.intMinusOne
    }

    override suspend fun doWork(): Result {
        val target = ULTarget(inputData)
        if (target.slot < 0 || target.itemId < 0) return error("invalid item")
        if (!Authentication.authenticateAndMessage()) return error("not authenticated")
        var errorMessage:String
        MetaDB[SlotIndex.fromIndex(target.slot)].use { metaDb ->
            val item = metaDb.itemExAt(target.itemId)
                ?: return error("item not found: ${target.itemId} in slot ${target.slot}")
            val file = metaDb.fileOf(item)
            if (!file.exists()) {
                return error("file not found: ${file.absolutePath}")
            }
            val contentType = if (item.isPhoto) "image/png" else "video/mp4"

            val canceller = Canceller()
            val viewModel = showModelessDialog<ProgressDialog.ProgressViewModel>("upload worker task") { vm ->
                vm.message.value = "Uploading..."
                vm.cancelCommand.bindForever { canceller.cancel() }
                ProgressDialog().apply { parentVisibilityOption = ParentVisibilityOption.NONE }
            }

            try {
                val body = ProgressRequestBody(file.asRequestBody(contentType.toMediaType())) { current, total ->
                    viewModel.setProgress(current, total)
                }
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("OwnerId", Settings.SecureArchive.clientId)
                    .addFormDataPart("Slot", "${target.slot}")
                    .addFormDataPart("FileDate", "${item.date}")
                    .addFormDataPart("CreationDate", "${item.creationDate}")
                    .addFormDataPart("OriginalId", "${item.id}")
                    .addFormDataPart("MetaInfo", "")
                    .addFormDataPart("ExtAttr", "${item.attrDataJson}")
                    .addFormDataPart("Duration", "${item.duration}")
                    .addFormDataPart("Size", "${file.length()}")
                    .addFormDataPart("File", item.name, body)
                    .build()
                val request = Request.Builder()
                    .url("http://${Authentication.activeHostAddress}/slot${target.slot}/upload")
                    .post(multipartBody)
                    .build()
                val code = executeAsync(request, canceller).use {
                    it.code
                }
                if (code == 200) {
                    logger.debug("uploaded")
                    metaDb.updateCloud(item.id, CloudStatus.Uploaded)
                    return Result.success()
                } else {
                    errorMessage = "cannot upload with error status code: $code"
                }
            } catch (_: CancellationException) {
                errorMessage = "cancelled"
            } catch (e: Throwable) {
                errorMessage = "error: ${e.message ?: e.toString()}"
            } finally {
                MainScope().launch { viewModel.closeCommand.invoke(false) }
            }
        }
        return error(errorMessage)
    }
}
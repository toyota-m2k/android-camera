package io.github.toyota32k.secureCamera.client.worker

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import io.github.toyota32k.dialog.UtDialog.ParentVisibilityOption
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.client.Canceller
import io.github.toyota32k.secureCamera.client.NetClient.executeAsync
import io.github.toyota32k.secureCamera.client.ProgressRequestBody
import io.github.toyota32k.secureCamera.client.TcClient
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.db.CloudStatus
import io.github.toyota32k.secureCamera.db.ItemEx
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.settings.SlotIndex
import io.github.toyota32k.utils.worker.UtTaskWorker
import io.github.toyota32k.utils.worker.WorkerParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

class Uploader(context: Context, params: WorkerParameters) : UtTaskWorker(context, params) {
    companion object {
        val logger = UtLog("Uploader", null, Uploader::class.java)
        val exclusiveRunner = ExclusiveRunner(logger, "Uploader")
        fun upload(context: Context, item: ItemEx) {
            val data = ULTarget(item.slot, item.id, item.name).produce()
            executeOneTimeWorker<Uploader>(context, data)
            logger.debug("request accepted: slot=${item.slot}, itemId=${item.id}")
        }
    }

    class ULTarget(inputData: Data?) : WorkerParams(inputData) {
        constructor() : this(null)
        constructor(slot: Int, itemId: Int, name:String) : this() {
            this.slot = slot
            this.itemId = itemId
            this.name = name
        }

        var slot: Int by delegate.intMinusOne
        var itemId: Int by delegate.intMinusOne
        var name: String by delegate.string
    }

    override suspend fun doWork(): Result {
        val target = ULTarget(inputData)
        if (target.slot < 0 || target.itemId < 0) return error("invalid item")

        // exclusiveRunnerを使って同じターゲットに対する重複アップロードを禁止する
        exclusiveRunner.run(target.slot, target.itemId) {
            if (!Authentication.authenticateAndMessage()) return error("not authenticated")
            MetaDB[SlotIndex.fromIndex(target.slot)].use { metaDb ->
                val item = metaDb.itemExAt(target.itemId)
                    ?: return error("item not found: ${target.itemId} in slot ${target.slot}")
                val file = metaDb.fileOf(item)
                if (!file.exists()) {
                    return error("file not found: ${file.absolutePath}")
                }
                if (!TcClient.registerOwnerToSecureArchive()) {
                    return error("cannot register owner info.")
                }
                val contentType = if (item.isPhoto) "image/png" else "video/mp4"

                val canceller = Canceller()

                // プログレスダイアログ（モードレスダイアログ）を表示
                val modeless = showModelessDialog<ProgressDialog.ProgressViewModel>(exclusiveRunner.key(target.slot, target.itemId)) {
                    ProgressDialog().apply {
                        parentVisibilityOption = ParentVisibilityOption.NONE
                    }
                } ?: return error("failed to show modeless dialog")

                // プログレスダイアログ（モードレスダイアログ）上でアップロードを実行
                modeless.executeOn { vm->
                    vm.title.value = "SecureCamera Uploader"
                    vm.message.value = target.name
                    vm.cancelCommand.bindForever {
                        canceller.cancel()
                    }
                    try {
                        val body = ProgressRequestBody(file.asRequestBody(contentType.toMediaType())) { current, total ->
                            if (total > 0) {
                                val percent = vm.setProgress(current, total)
                                notifyProgress(current == total, percent)
                            }
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
                            logger.info("successfully uploaded: slot=${target.slot}, itemId=${target.itemId}")
                        } else {
                            logger.error("error: slot=${target.slot}, itemId=${target.itemId}, code=$code")
                        }
                    } catch (_: CancellationException) {
                        logger.info("upload cancelled: slot=${target.slot}, itemId=${target.itemId}")
                    } catch (e: Throwable) {
                        logger.error(e,"upload failed: slot=${target.slot}, itemId=${target.itemId}")
                    }
                }
            }
        }
        return succeeded()
    }
}
package io.github.toyota32k.secureCamera.utils

import android.content.Context
import android.os.ParcelFileDescriptor
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.launchSubTask
import io.github.toyota32k.dialog.task.showYesNoMessageBox
import io.github.toyota32k.media.lib.io.AndroidFile
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.io.toAndroidFile
import io.github.toyota32k.media.lib.legacy.converter.Splitter
import io.github.toyota32k.media.lib.processor.contract.format
import io.github.toyota32k.media.lib.processor.optimizer.FastStart
import io.github.toyota32k.secureCamera.SCApplication.Companion.logger
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.utils.FileUtil.safeDelete
import io.github.toyota32k.secureCamera.utils.FileUtil.safeDeleteFile
import io.github.toyota32k.utils.TimeSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.cancellation.CancellationException

class SplitHelper(
    val inputFile: IInputMediaFile,
) {
    var resultFirstFile: File? = null
        private set
    var resultLastFile: File? = null
        private set
    var cancelled: Boolean = false
        private set
    var error: Throwable? = null
        private set

    private fun optimize(src:File, dst:File, vm:ProgressDialog.ProgressViewModel):File {
        return try {
            if (FastStart.process(src.toAndroidFile(), dst.toAndroidFile(), removeFree = true) {
                    vm.progress.value = it.percentage
                    vm.progressText.value = it.format()
                }
            ) {
                safeDeleteFile(src)
                dst
            } else {
                safeDeleteFile(dst)
                src
            }
        } catch (e: Throwable) {
            safeDeleteFile(dst)
            src
        }
    }

    data class Result(val firstFile:File, val lastFile:File, val actualSplitPosMs:Long)

    suspend fun chop(context: Context, pos:Long):Result? {
        return UtImmortalTask.awaitTaskResult("SplitHelper") {
            if(!showYesNoMessageBox("Split File", "Split this video file into 2 files at ${TimeSpan(pos).formatAuto()}\nAre you sure?")) {
                return@awaitTaskResult null
            }
            error = null
            resultFirstFile = null
            resultLastFile = null
            val vm = createViewModel<ProgressDialog.ProgressViewModel>()
            vm.message.value = "Splitting Now..."

            val af = inputFile as AndroidFile
            val fd = ParcelFileDescriptor.open(af.path, ParcelFileDescriptor.parseMode("r"))
            val stream = FileInputStream(fd.fileDescriptor)
            val bytes = stream.available()
            logger.debug("available = $bytes")

            val optimize = if (inputFile is AndroidFile) { FastStart.check(inputFile) == FastStart.CheckResult.AlreadyOptimized } else true
            val firstFile = File(context.cacheDir ?: throw java.lang.IllegalStateException("no cacheDir"),"first")
            val lastFile = File(context.cacheDir ?: throw java.lang.IllegalStateException("no cacheDir"),"last")

            val splitter = Splitter.Builder()
                .setProgressHandler {
                    vm.progress.value = it.percentage
                    vm.progressText.value = it.format()
                }
                .build()
            vm.cancelCommand.bindForever {
                cancelled = true
                splitter.cancel()
            }
            launchSubTask { showDialog("SplitHelper.ProgressDialog") { ProgressDialog() } }
            withContext(Dispatchers.IO) {
                try {
                    val result = splitter.chop(inputFile, firstFile.toAndroidFile(), lastFile.toAndroidFile(), pos)
                    if (!result.all { it.succeeded }) {
                        throw result.firstNotNullOfOrNull { it.exception } ?: IllegalStateException("unknown error")
                    }
                    if (!optimize) {
                        resultFirstFile = firstFile
                        resultLastFile = lastFile
                    } else {
                        vm.message.value = "Optimizing Now..."
                        if (cancelled) throw CancellationException()
                        resultFirstFile = optimize( firstFile,File(context.cacheDir ?: throw java.lang.IllegalStateException("no cacheDir"),"optFirst"), vm)

                        if (cancelled) throw CancellationException()
                        resultLastFile = optimize( lastFile, File(context.cacheDir ?: throw java.lang.IllegalStateException("no cacheDir"),"optLast"),vm)
                    }
                    if (resultFirstFile?.exists() != true || resultLastFile?.exists() != true ) {
                        // 両方が揃っていなければエラー
                        resultFirstFile?.safeDelete()
                        resultLastFile?.safeDelete()
                        resultFirstFile = null
                        resultLastFile = null
                        null
                    } else Result(resultFirstFile!!, resultLastFile!!, splitter.correctPositionMs(pos))
                } catch (e: Throwable) {
                    safeDeleteFile(firstFile)
                    safeDeleteFile(lastFile)
                    resultFirstFile = null
                    resultLastFile = null
                    error = e
                    null
                } finally {
                    withContext(Dispatchers.Main) { vm.closeCommand.invoke(true) }
                }
            }
        }
    }
}
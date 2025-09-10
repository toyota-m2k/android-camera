package io.github.toyota32k.secureCamera.utils

import android.content.Context
import androidx.core.net.toUri
import io.github.toyota32k.dialog.task.IUtImmortalTask
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.awaitSubTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.launchSubTask
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.converter.ConvertResult
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.Converter.Factory.RangeMs
import io.github.toyota32k.media.lib.converter.FastStart
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.converter.Rotation
import io.github.toyota32k.media.lib.converter.format
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.VideoStrategy
import io.github.toyota32k.secureCamera.EditorActivity
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.utils.FlowableEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.seconds

fun safeDeleteFile(file:File) {
    try {
        if (file.exists()) {
            file.delete()
        }
    } catch (_:Throwable) {
    }
}

fun File.safeDelete() {
    safeDeleteFile(this)
}


class ConvertHelper(
    val inputFile: IInputMediaFile,
    var videoStrategy: IVideoStrategy,
    var keepHdr: Boolean,
    val rotation: Rotation,
    val trimmingRanges: Array<RangeMs>,
) {
    val logger = UtLog("CH", EditorActivity.logger)
    var trimFileName: String = "trim"
    var optFileName: String = "opt"
    lateinit var result: ConvertResult
        private set
    val report: Report? get() = result.report

    private suspend fun convert(applicationContext: Context, limitDuration:Long, optimize:Boolean): File? {
        return UtImmortalTask.awaitTaskResult("ConvertHelper") {
            val vm = createViewModel<ProgressDialog.ProgressViewModel>()
            vm.message.value = "Trimming Now..."
            val trimFile = File(applicationContext.cacheDir ?: throw java.lang.IllegalStateException("no cacheDir"), trimFileName)
            val optFile = File(applicationContext.cacheDir ?: throw java.lang.IllegalStateException("no cacheDir"), optFileName)
            val converter = Converter.Factory()
                .input(inputFile)
                .output(trimFile)
                .audioStrategy(PresetAudioStrategies.AACDefault)
                .videoStrategy(videoStrategy)
                .keepHDR(keepHdr)
                .rotate(rotation)
                .addTrimmingRanges(*trimmingRanges)
                .limitDuration(limitDuration)
                .setProgressHandler {
                    vm.progress.value = it.percentage
                    vm.progressText.value = it.format()
                }
                .build()
            vm.cancelCommand.bindForever { converter.cancel() }
            launchSubTask { showDialog("ConvertHelper.ProgressDialog") { ProgressDialog() } }

            withContext(Dispatchers.IO) {
                try {
                    val r = converter.execute().apply { result = this }
                    if (!r.succeeded) {
                        if (r.cancelled) {
                            throw CancellationException("conversion cancelled")
                        } else {
                            throw r.exception ?: IllegalStateException("unknown error")
                        }
                    }
                    if (!optimize) {
                        trimFile
                    } else {
                        vm.message.value = "Optimizing Now..."
                        if (FastStart.process(trimFile.toUri(), optFile.toUri(), applicationContext) {
                                vm.progress.value = it.percentage
                                vm.progressText.value = it.format()
                            }) {
                            safeDeleteFile(trimFile)
                            optFile
                        } else {
                            safeDeleteFile(optFile)
                            trimFile
                        }
                    }
                } catch (e: Throwable) {
                    safeDeleteFile(trimFile)
                    safeDeleteFile(optFile)
                    throw e
                } finally {
                    withContext(Dispatchers.Main) { vm.closeCommand.invoke(true) }
                }
            }
        }
    }

    private suspend fun safeConvert(applicationContext: Context, limitDuration: Long, optimize: Boolean): File? {
        return try {
            convert(applicationContext, limitDuration, optimize)
        } catch (_: CancellationException) {
            logger.info("conversion cancelled")
            null
        } catch (e: Throwable) {
            logger.stackTrace(e)
            UtImmortalTask("ConvertHelper.Error") {
                showConfirmMessageBox(
                    "Conversion Error",
                    e.localizedMessage ?: e.message ?: "Something wrong."
                )
            }
            null
        }
    }

    suspend fun tryConvert(applicationContext: Context, limitDuration:Long=5.seconds.inWholeMilliseconds): File? {
        return safeConvert(applicationContext, limitDuration, false)
    }

    suspend fun convertAndOptimize(applicationContext: Context): File? {
        return safeConvert(applicationContext, 0, true)
    }
}
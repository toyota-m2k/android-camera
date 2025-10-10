package io.github.toyota32k.secureCamera.utils

import android.content.Context
import androidx.core.net.toUri
import io.github.toyota32k.dialog.task.UtImmortalTask
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
import io.github.toyota32k.media.lib.converter.toAndroidFile
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.secureCamera.EditorActivity
import io.github.toyota32k.secureCamera.dialog.ProgressDialog
import io.github.toyota32k.secureCamera.utils.FileUtil.safeDeleteFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.seconds

class ConvertHelper(
    val inputFile: IInputMediaFile,
    var videoStrategy: IVideoStrategy,
    var keepHdr: Boolean,
    val rotation: Rotation,
    val trimmingRanges: Array<RangeMs>,
    val durationMs:Long
) {
    val logger = UtLog("CH", EditorActivity.logger)
    var trimFileName: String = "trim"
    var optFileName: String = "opt"
    lateinit var result: ConvertResult
        private set
    val report: Report? get() = result.report

    /**
     * トリミング実行後の再生時間
     */
    val trimmedDuration:Long
        get() = calcTrimmedDuration(durationMs, trimmingRanges)


    private suspend fun convert(applicationContext: Context, limitDuration:Long, optimize:Boolean, ranges: Array<RangeMs>?): File? {
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
                .addTrimmingRanges(*(ranges?:trimmingRanges))
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
                        if (FastStart.process(trimFile.toAndroidFile(), optFile.toAndroidFile(), removeFree = true) {
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

    private suspend fun safeConvert(applicationContext: Context, limitDuration: Long, optimize: Boolean, ranges: Array<RangeMs>?=null): File? {
        return try {
            convert(applicationContext, limitDuration, optimize, ranges)
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

    /**
     * 指定位置 (convertFrom) より後ろの総再生時間を計算する
     */
    private fun calcRemainingDurationAfter(convertFrom:Long, duration:Long):Long {
        return trimmingRanges.fold(duration) { acc, range ->
            val end = if (range.endMs==0L) duration else range.endMs
            when {
                end < convertFrom -> acc
                range.startMs <= convertFrom -> acc + (end - convertFrom)
                else -> acc + end - range.startMs
            }
        }
    }

    /**
     * 開始位置(convertFrom)に合わせてthis.trimmingRangeを調整し、新しいTrimmingRanges配列を作成する。
     */
    private fun adjustTrimmingRangeWithPosition(convertFrom:Long, limitDuration:Long):Array<RangeMs> {
        // pos 以降の残り時間を計算
        val remaining = calcRemainingDurationAfter(convertFrom, durationMs)
        return if (remaining>=limitDuration) {
            // 残り時間が十分ある場合は、posを起点とする TrimmingRanges配列を作成
            trimmingRanges.fold(mutableListOf<RangeMs>()) { acc, range ->
                val end = if (range.endMs==0L) durationMs else range.endMs
                when {
                    end < convertFrom -> acc
                    range.startMs <= convertFrom -> acc.add(RangeMs(convertFrom, end))
                    else -> acc.add(range)
                }
                acc
            }.toTypedArray()
        } else {
            // 残り時間が不足する場合は、
            var total = 0L
            val list = mutableListOf<RangeMs>()
            for(range in trimmingRanges.reversed()) {
                val end = if (range.endMs==0L) durationMs else range.endMs
                total += (end - range.startMs)
                if (total<=limitDuration) {
                    list.add(0, range)
                } else {
                    val remain = total-limitDuration
                    if (remain > 1000) {
                        list.add(0, RangeMs(end - remain, end))
                    }
                    break
                }
            }
            list.toTypedArray()
        }
    }


    suspend fun tryConvert(applicationContext: Context, convertFrom:Long, limitDuration:Long=10.seconds.inWholeMilliseconds): File? {
        val duration = calcTrimmedDuration(durationMs, trimmingRanges)
        val ranges = if (duration<=limitDuration) {
            // トリミング後の再生時間が既定時間(limitDuration)に満たない場合は、convertFromは無視して先頭から
            null
        } else {
            // convertFrom と limitDuration から、試行用の trimmingRanges 配列を作成する
            adjustTrimmingRangeWithPosition(convertFrom, limitDuration)
        }
        return safeConvert(applicationContext, limitDuration, true, ranges)
    }

    suspend fun convertAndOptimize(applicationContext: Context): File? {
        return safeConvert(applicationContext, 0, true)
    }

    companion object {
        // Trimming後のdurationを計算
        fun calcTrimmedDuration(duration:Long, trimmingRanges: Array<RangeMs>):Long {
            return trimmingRanges.fold(0L) { acc, range ->
                val end = if (range.endMs==0L) duration else range.endMs
                acc + end - range.startMs
            }
        }
    }
}
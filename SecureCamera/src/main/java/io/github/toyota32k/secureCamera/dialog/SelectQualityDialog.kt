package io.github.toyota32k.secureCamera.dialog

import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.core.net.toUri
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.radioGroupBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtAndroidViewModel
import io.github.toyota32k.dialog.task.UtAndroidViewModel.Companion.createAndroidViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.dialog.task.launchSubTask
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogSelectQualityBinding
import io.github.toyota32k.secureCamera.utils.ConvertHelper
import io.github.toyota32k.secureCamera.utils.FileUtil.safeDelete
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class SelectQualityDialog : UtDialogEx() {
    enum class VideoQuality(@param:IdRes val id: Int, val strategy: IVideoStrategy) {
        High(R.id.radio_high, PresetVideoStrategies.HEVC1080LowProfile),
        Middle(R.id.radio_middle, PresetVideoStrategies.HEVC720Profile),
        Low(R.id.radio_low, PresetVideoStrategies.HEVC720LowProfile);
        companion object {
            fun valueOf(@IdRes id: Int): VideoQuality? {
                return VideoQuality.entries.find { it.id == id }
            }
        }

        object IDResolver : IIDValueResolver<VideoQuality> {
            override fun id2value(id: Int): VideoQuality? = valueOf(id)
            override fun value2id(v: VideoQuality): Int = v.id
        }
    }

    class QualityViewModel(application: Application) : UtAndroidViewModel(application) {
        private class TrialCache {
            private enum class TrialType(val quality: VideoQuality, val keepHdr: Boolean) {
                HighKeep(VideoQuality.High, true),
                HighNoKeep(VideoQuality.High, false),
                MiddleKeep(VideoQuality.Middle, true),
                MiddleNoKeep(VideoQuality.Middle, false),
                LowKeep(VideoQuality.Low, true),
                LowNoKeep(VideoQuality.Low, false);
                companion object {
                    fun typeOf(vq: VideoQuality, keepHdr:Boolean):TrialType? {
                        return entries.find { it.quality == vq && it.keepHdr == keepHdr }
                    }
                }
            }
            private val map = mutableMapOf<TrialType, File>()

            fun fileNameOf(vq: VideoQuality, keepHdr:Boolean):String? {
                val type = TrialType.typeOf(vq, keepHdr) ?: return null
                return type.name
            }

            fun get(vq: VideoQuality, keepHdr:Boolean):File? {
                val type = TrialType.typeOf(vq, keepHdr) ?: return null
                val cached = map[type]
                if(cached?.exists()!=true) {
                    map.remove(type)
                    return null
                }
                return cached
            }
            fun put(vq: VideoQuality, keepHdr:Boolean, file:File) {
                val type = TrialType.typeOf(vq, keepHdr) ?: return
                val old = map[type]
                if (old!=null && old!=file) {
                    old.safeDelete()
                }
                map[type] = file
            }
            fun clear(application: Application?) {
                if (application!=null) {
                    val cacheDir = application.cacheDir
                    TrialType.entries.forEach {
                        File(cacheDir, it.name).safeDelete()
                    }
                } else {
                    map.values.forEach {
                        it.safeDelete()
                    }
                }
                map.clear()
            }
        }

        lateinit var convertHelper: ConvertHelper
        val quality = MutableStateFlow(VideoQuality.High)
        val sourceHdr = MutableStateFlow(false)
        val keepHdr = MutableStateFlow(true)
        private val trialCache = TrialCache()

        override fun onCleared() {
            trialCache.clear(getApplication())
            super.onCleared()
        }

        /**
         * 変換を試みる。
         * 変換に成功したら変換後のファイルを返す。
         * 変換に失敗したらnullを返す。
         */
        suspend fun tryConvert():File? {
            val cached = trialCache.get(quality.value, keepHdr.value)
            if (cached!=null) {
                return cached
            }
            convertHelper.trimFileName = trialCache.fileNameOf(quality.value, keepHdr.value) ?: return null
            convertHelper.keepHdr = keepHdr.value && sourceHdr.value
            convertHelper.videoStrategy = quality.value.strategy
            return convertHelper.tryConvert(getApplication())?.apply {
                trialCache.put(quality.value, keepHdr.value, this)
            }
        }

        val testCommand = LiteUnitCommand {
            immortalTaskContext.launchSubTask {
                var workFile:File? = tryConvert()
                if (workFile!=null) {
                    VideoPreviewDialog.show(workFile.toUri().toString(), "preview")
                }
            }
        }
    }

    val viewModel by lazy { getViewModel<QualityViewModel>() }
    lateinit var controls: DialogSelectQualityBinding

    override fun preCreateBodyView() {
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
        optionButtonType = ButtonType("Try", true)
        optionButtonWithAccent = true
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.COMPACT
        title = requireActivity().getString(R.string.video_quality)
        enableFocusManagement()
            .setInitialFocus(R.id.radio_high)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogSelectQualityBinding.inflate(inflater.layoutInflater)
        return controls.root.also { _ ->
            binder
                .radioGroupBinding(controls.qualityGroup, viewModel.quality, VideoQuality.IDResolver)
                .checkBinding(controls.checkKeepHdr, viewModel.keepHdr)
                .visibilityBinding(controls.convertHdrGroup, viewModel.sourceHdr, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .dialogOptionButtonCommand(viewModel.testCommand)
        }
    }

    companion object {
        data class Result(val quality: VideoQuality, val keepHdr: Boolean)
        suspend fun show(hdr:Boolean, helper:ConvertHelper):Result? {
            return UtImmortalTask.awaitTaskResult<Result?>(this::class.java.name) {
                val vm = createAndroidViewModel<QualityViewModel>().apply {
                    convertHelper = helper
                    sourceHdr.value = hdr
                }
                if(showDialog(this.taskName) { SelectQualityDialog() }.status.positive) {
                    Result(vm.quality.value, vm.keepHdr.value)
                } else null
            }
        }
    }
}
package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.lifecycle.ViewModel
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.binder.editTextBinding
import io.github.toyota32k.binder.radioGroupBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.IUtImmortalTask
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtImmortalViewModel
import io.github.toyota32k.dialog.task.UtImmortalViewModelHelper
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogAddressBinding
import io.github.toyota32k.secureCamera.databinding.DialogSelectQualityBinding
import io.github.toyota32k.secureCamera.dialog.AddressDialog.AddressDialogViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class SelectQualityDialog : UtDialogEx() {
    enum class VideoQuality(@IdRes val id: Int, val compact:Boolean=true) {
        High(R.id.radio_high, false),
        Middle(R.id.radio_middle),
        Low(R.id.radio_low);
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
    class QualityViewModel : UtImmortalViewModel() {
        val quality = MutableStateFlow(VideoQuality.High)
        companion object {
            fun createBy(task: IUtImmortalTask): QualityViewModel
                = UtImmortalViewModelHelper.createBy(QualityViewModel::class.java, task)
            fun instanceFor(dialog: SelectQualityDialog): QualityViewModel
                = UtImmortalViewModelHelper.instanceFor(QualityViewModel::class.java, dialog)
        }
    }

    val viewModel by lazy { QualityViewModel.instanceFor(this) }
    lateinit var controls: DialogSelectQualityBinding

    override fun preCreateBodyView() {
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.DONE)
        gravityOption = GravityOption.CENTER
        setLimitWidth(400)
        heightOption = HeightOption.COMPACT
        title = requireActivity().getString(R.string.video_quality)
        enableFocusManagement()
            .setInitialFocus(R.id.radio_high)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogSelectQualityBinding.inflate(inflater.layoutInflater)
        return controls.root.also { _ ->
            binder.radioGroupBinding(controls.qualityGroup, viewModel.quality, VideoQuality.IDResolver)
        }
    }

    companion object {
        suspend fun show():VideoQuality? {
            return UtImmortalSimpleTask.executeAsync<VideoQuality?>(this::class.java.name) {
                val vm = QualityViewModel.createBy(this)
                if(showDialog(this.taskName) { SelectQualityDialog() }.status.positive) {
                    vm.quality.value
                } else null
            }
        }
    }
}
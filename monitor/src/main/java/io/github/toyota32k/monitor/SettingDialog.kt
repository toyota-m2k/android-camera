package io.github.toyota32k.monitor

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModel
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.binder.headlessBinding
import io.github.toyota32k.binder.materialRadioButtonGroupBinding
import io.github.toyota32k.dialog.UtDialogConfig
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.*
import kotlinx.coroutines.flow.MutableStateFlow

class SettingDialog : UtDialogEx() {
    class SettingViewModel : ViewModel(), IUtImmortalTaskMutableContextSource {
        override lateinit var immortalTaskContext: IUtImmortalTaskContext
        val frontCameraSelected = MutableStateFlow(false)

        companion object {
            fun create(task: IUtImmortalTask, frontCamera:Boolean):SettingViewModel {
                return task.createViewModel<SettingViewModel>().apply {
                    frontCameraSelected.value = frontCamera
                }
            }
        }
    }

    override fun preCreateBodyView() {
        UtDialogConfig.solidBackgroundOnPhone = false
        noHeader = true
        cancellable = true
        heightOption = HeightOption.COMPACT
        widthOption = WidthOption.COMPACT
        gravityOption = GravityOption.CENTER
    }

    val cameraSelectorResolver: IIDValueResolver<Boolean> = object:IIDValueResolver<Boolean> {
        override fun id2value(id: Int): Boolean? {
            return when(id) {
                R.id.button_front -> true
                R.id.button_rear -> false
                else -> null
            }
        }

        override fun value2id(v: Boolean): Int {
            return if(v) R.id.button_front else R.id.button_rear
        }
    }

    val viewModel: SettingViewModel by lazy { immortalTaskContext.getViewModel() }
    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        val orgCamera = viewModel.frontCameraSelected.value
        return inflater.inflate(R.layout.setting_dialog).also { dlg->
            binder.owner(this)
                .materialRadioButtonGroupBinding(dlg.findViewById(R.id.camera_selector), viewModel.frontCameraSelected, cameraSelectorResolver)
                .headlessBinding(viewModel.frontCameraSelected) {
                    if(it!=orgCamera) {
                        onPositive()
                    }
                }
        }
    }

    companion object {
        fun show(frontCamera: MutableStateFlow<Boolean>) {
            UtImmortalSimpleTask.run(SettingDialog::class.java.name) {
                val model = SettingViewModel.create(this, frontCamera.value)
                showDialog(taskName) { SettingDialog() }
                frontCamera.value = model.frontCameraSelected.value
                true
            }
        }
    }
}
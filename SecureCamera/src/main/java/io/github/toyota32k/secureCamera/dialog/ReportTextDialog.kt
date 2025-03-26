package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.databinding.DialogReportTextBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class ReportTextDialog : UtDialogEx() {
    class MultilineTextViewModel : UtDialogViewModel() {
        val label = MutableStateFlow("")
        val message = MutableStateFlow("")
//        companion object {
//            fun create(taskName:String): MultilineTextViewModel {
//                return UtImmortalTaskManager.taskOf(taskName)?.task?.createViewModel() ?: throw IllegalStateException("no task")
//            }
//
//            fun instanceFor(dlg:ReportTextDialog): MultilineTextViewModel {
//                return ViewModelProvider(dlg.immortalTaskContext, ViewModelProvider.NewInstanceFactory())[MultilineTextViewModel::class.java]
//            }
//        }
    }

    private val viewModel by lazy { getViewModel<MultilineTextViewModel>() }
    private lateinit var controls: DialogReportTextBinding

    override fun preCreateBodyView() {
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.FULL
        heightOption = HeightOption.FULL
        rightButtonType = ButtonType.CLOSE
        noHeader = true
    }
    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogReportTextBinding.inflate(inflater.layoutInflater)
        return controls.root.also {_->
            binder
                .textBinding(controls.label, viewModel.label)
                .textBinding(controls.message, viewModel.message)
                .visibilityBinding(controls.label, viewModel.label.map { it.isNotBlank()})
        }
    }

    companion object {
        fun show(label:String, message:String) {
            UtImmortalTask.launchTask(ReportTextDialog::class.java.name) {
                createViewModel<MultilineTextViewModel> {
                    this.label.value = label
                    this.message.value = message
                }
                showDialog(taskName) { ReportTextDialog() }
                true
            }
        }
    }

}
package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.UtImmortalViewModel
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.immortalTaskContext
import io.github.toyota32k.secureCamera.databinding.DialogReportTextBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class ReportTextDialog : UtDialogEx() {
    class MultilineTextViewModel : UtImmortalViewModel() {
        val label = MutableStateFlow("")
        val message = MutableStateFlow("")
        companion object {
            fun create(taskName:String): MultilineTextViewModel {
                return UtImmortalTaskManager.taskOf(taskName)?.task?.createViewModel() ?: throw IllegalStateException("no task")
            }

            fun instanceFor(dlg:ReportTextDialog): MultilineTextViewModel {
                return ViewModelProvider(dlg.immortalTaskContext, ViewModelProvider.NewInstanceFactory())[MultilineTextViewModel::class.java]
            }
        }
    }

    private val viewModel by lazy { MultilineTextViewModel.instanceFor(this) }
    private lateinit var controls: DialogReportTextBinding

    override fun preCreateBodyView() {
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.FULL
        heightOption = HeightOption.FULL
        setRightButton(BuiltInButtonType.CLOSE)
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
            UtImmortalSimpleTask.run(ReportTextDialog::class.java.name) {
                MultilineTextViewModel.create(taskName).also {
                    it.label.value = label
                    it.message.value = message
                }
                showDialog(taskName) { ReportTextDialog() }
                true
            }
        }
    }

}
package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.bindit.LiteUnitCommand
import io.github.toyota32k.bindit.ReliableCommand
import io.github.toyota32k.bindit.progressBarBinding
import io.github.toyota32k.bindit.textBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.*
import io.github.toyota32k.secureCamera.databinding.DialogProgressBinding
import io.github.toyota32k.utils.bindCommand
import kotlinx.coroutines.flow.MutableStateFlow

class ProgressDialog : UtDialogEx() {
    class ProgressViewModel : ViewModel(), IUtImmortalTaskMutableContextSource {
        override lateinit var immortalTaskContext: IUtImmortalTaskContext
        val progress = MutableStateFlow(0)
        val progressText = MutableStateFlow("")
        val message = MutableStateFlow("")
        val cancelCommand = LiteUnitCommand()
        val closeCommand = ReliableCommand<Boolean>()

        companion object {
            fun create(taskName:String):ProgressViewModel {
                return UtImmortalTaskManager.taskOf(taskName)?.task?.createViewModel() ?: throw IllegalStateException("no task")
            }

            fun instanceFor(dlg:ProgressDialog):ProgressViewModel {
                return ViewModelProvider(dlg.immortalTaskContext, ViewModelProvider.NewInstanceFactory())[ProgressViewModel::class.java]
            }
        }
    }

    private val viewModel by lazy { ProgressViewModel.instanceFor(this) }
    lateinit var controls: DialogProgressBinding

    override fun preCreateBodyView() {
        gravityOption = GravityOption.CENTER
        noHeader = true
        setLimitWidth(400)
        heightOption = HeightOption.COMPACT
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogProgressBinding.inflate(inflater.layoutInflater)
        return controls.root.also { _->
            binder
                .textBinding(controls.message, viewModel.message)
                .textBinding(controls.progressText, viewModel.progressText)
                .progressBarBinding(controls.progressBar, viewModel.progress)
                .bindCommand(viewModel.cancelCommand, controls.cancelButton)
                .bindCommand(viewModel.closeCommand) { if(it) onPositive() else onNegative() }
        }
    }
}
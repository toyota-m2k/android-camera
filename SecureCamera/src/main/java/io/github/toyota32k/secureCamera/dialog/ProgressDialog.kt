package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.ReliableCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.progressBarBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.IUtImmortalTaskContext
import io.github.toyota32k.dialog.task.IUtImmortalTaskMutableContextSource
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.dialog.task.immortalTaskContext
import io.github.toyota32k.secureCamera.client.TcClient.sizeInKb
import io.github.toyota32k.secureCamera.databinding.DialogProgressBinding
import kotlinx.coroutines.flow.MutableStateFlow

class ProgressDialog : UtDialogEx() {
    class ProgressViewModel : UtDialogViewModel() {
        val progress = MutableStateFlow(0)
        val progressText = MutableStateFlow("")
        val title = MutableStateFlow("")
        val message = MutableStateFlow("")
        val cancelCommand = LiteUnitCommand()
        val closeCommand = ReliableCommand<Boolean>()

        fun setProgress(current:Long, total:Long):Int {
            val percent = if (total <= 0L) 0 else (current * 100L / total).toInt().coerceIn(0,100)
            progress.value = percent
            progressText.value = "${sizeInKb(current)} / ${sizeInKb(total)} (${percent} %)"
            return percent
        }
    }

    private val viewModel by lazy { getViewModel<ProgressViewModel>() }
    lateinit var controls: DialogProgressBinding

    override fun preCreateBodyView() {
        gravityOption = GravityOption.CENTER
        noHeader = true
        noFooter = true
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.COMPACT
        cancellable = false
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogProgressBinding.inflate(inflater.layoutInflater)
        return controls.root.also { _->
            binder
                .textBinding(controls.message, viewModel.message)
                .textBinding(controls.progressText, viewModel.progressText)
                .dialogTitle(viewModel.title)
                .progressBarBinding(controls.progressBar, viewModel.progress)
                .bindCommand(viewModel.cancelCommand, controls.cancelButton)
                .bindCommand(viewModel.closeCommand) { if(it) onPositive() else onNegative() }
        }
    }
}
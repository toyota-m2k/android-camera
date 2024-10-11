package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.constraintlayout.utils.widget.MotionLabel
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.UtImmortalViewModel
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.immortalTaskContext
import io.github.toyota32k.secureCamera.databinding.DialogDetailMessageBinding
import kotlinx.coroutines.flow.MutableStateFlow

class DetailMessageDialog : UtDialogEx() {
    class DetailMessageViewModel : UtImmortalViewModel() {
        val label = MutableStateFlow("")
        val message = MutableStateFlow("")
        val detailMessage = MutableStateFlow("")
        val showDetailMessage = MutableStateFlow(false)

        companion object {
            fun create(taskName:String, label: String, message: String, detailMessage: String): DetailMessageViewModel {
                return UtImmortalTaskManager.taskOf(taskName)?.task?.createViewModel<DetailMessageViewModel>()?.also {
                    it.label.value = label
                    it.message.value = message
                    it.detailMessage.value = detailMessage
                } ?: throw IllegalStateException("no task")
            }
            fun instanceFor(dlg:DetailMessageDialog): DetailMessageViewModel {
                return ViewModelProvider(dlg.immortalTaskContext, ViewModelProvider.NewInstanceFactory())[DetailMessageViewModel::class.java]
            }
        }
    }

    private val viewModel by lazy { DetailMessageViewModel.instanceFor(this) }
    private lateinit var controls : DialogDetailMessageBinding

    override fun preCreateBodyView() {
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.FULL
        heightOption = HeightOption.AUTO_SCROLL
        setRightButton(BuiltInButtonType.CLOSE)
    }

    override fun createBodyView(
        savedInstanceState: Bundle?,
        inflater: IViewInflater
    ): View {
        controls = DialogDetailMessageBinding.inflate(inflater.layoutInflater).apply {
            binder
                .textBinding(label, viewModel.label)
                .textBinding(message, viewModel.message)
                .textBinding(detailMessage, viewModel.detailMessage)
                .checkBinding(checkShowDetail, viewModel.showDetailMessage)
                .visibilityBinding(detailMessage, viewModel.showDetailMessage)
        }

        return controls.root
    }

    companion object {
        suspend fun showMessage(label:String, message:String, detailMessage:String) {
            UtImmortalSimpleTask.executeAsync(DetailMessageDialog::class.java.name) {
                DetailMessageViewModel.create(taskName, label, message, detailMessage)
                showDialog(taskName) { DetailMessageDialog() }
                true
            }
        }
    }
}
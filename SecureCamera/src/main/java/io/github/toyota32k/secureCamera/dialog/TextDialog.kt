package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.editTextBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.IUtImmortalTask
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogTextBinding
import io.github.toyota32k.utils.ConstantLiveData
import kotlinx.coroutines.flow.MutableStateFlow

class TextDialog : UtDialogEx() {
    class TextViewModel : UtDialogViewModel() {
        val text = MutableStateFlow("")
        var message:String = ""
        var hint:String = ""
        var title:String = "Input Text"

        fun initialize(title:String, initialText:String, hint:String="", message:String="") {
            this.title = title
            text.value = initialText
            this.hint = hint
            this.message = message
        }


        companion object {
//            fun createBy(task: IUtImmortalTask, title:String, initialText:String, hint:String="", message:String=""): TextViewModel {
//                return UtImmortalViewModelHelper.createBy(TextViewModel::class.java, task).apply {
//                    text.value = initialText
//                    this.title = title
//                    this.hint = hint
//                    this.message = message
//                }
//            }
//            fun instanceFor(dialog: TextDialog): TextViewModel {
//                return UtImmortalViewModelHelper.instanceFor(TextViewModel::class.java, dialog)
//            }
        }
    }
    private val viewModel by lazy { getViewModel<TextViewModel>() }
    private lateinit var controls: DialogTextBinding

    override fun preCreateBodyView() {
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.COMPACT
        title = viewModel.title //requireActivity().getString(R.string.secure_archive_address)
        enableFocusManagement()
            .setInitialFocus(R.id.text)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogTextBinding.inflate(inflater.layoutInflater)
        return controls.root.also { _ ->
            binder
                .editTextBinding(controls.text, viewModel.text, BindingMode.TwoWay)
                .visibilityBinding(controls.message, ConstantLiveData(viewModel.message.isNotBlank()), hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            controls.text.hint = viewModel.hint
        }
    }

    companion object {
        suspend fun getText(title:String, initText:String="", hint:String="", message:String="") : String? {
            return UtImmortalTask.awaitTaskResult(this::class.java.name) {
                val vm = createViewModel<TextViewModel> { initialize(title, initText, hint, message) }
                if(showDialog(taskName) { TextDialog() }.status.positive) {
                    vm.text.value
                } else null
            }
        }
    }
}
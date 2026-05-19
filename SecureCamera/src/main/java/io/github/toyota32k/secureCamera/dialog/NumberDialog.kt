package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.editIntBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogTextBinding
import io.github.toyota32k.utils.lifecycle.ConstantLiveData
import kotlinx.coroutines.flow.MutableStateFlow

class NumberDialog : UtDialogEx() {
    class NumberViewModel : UtDialogViewModel() {
        val number = MutableStateFlow(0)
        var message:String = ""
        var hint:String = ""
        var title:String = "Input Text"

        fun initialize(title:String, initialValue:Int, hint:String="", message:String="") {
            this.title = title
            number.value = initialValue
            this.hint = hint
            this.message = message
        }
    }

    private val viewModel by lazy { getViewModel<NumberViewModel>() }
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

    override fun createBodyView(
        savedInstanceState: Bundle?,
        inflater: IViewInflater
    ): View {
        controls = DialogTextBinding.inflate(inflater.layoutInflater)
        controls.text.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        return controls.root.also { _ ->
            binder
                .editIntBinding(controls.text, viewModel.number, BindingMode.TwoWay)
                .visibilityBinding(controls.message, ConstantLiveData(viewModel.message.isNotBlank()), hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            controls.text.hint = viewModel.hint
        }
    }

    companion object {
        suspend fun getNumber(title:String, initNumber:Int, hint:String="", message:String="") {
            UtImmortalTask.awaitTaskResult {
                val vm = createViewModel<NumberViewModel> { initialize(title, initNumber, hint, message) }
                if(showDialog(taskName) { TextDialog() }.status.positive) {
                    vm.number.value
                } else null
            }
        }
    }
}
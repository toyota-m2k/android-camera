package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.editTextBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.IUtImmortalTask
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogAddressBinding
import kotlinx.coroutines.flow.MutableStateFlow

class AddressDialog : UtDialogEx() {
    class AddressDialogViewModel : UtDialogViewModel() {
        val address = MutableStateFlow("")
        fun initialize(address:String) {
            this.address.value = address
        }

//        fun save() {
//            Settings.SecureArchive.address = address.value
//        }
        companion object {
//            fun createBy(task:IUtImmortalTask, initialAddress:String):AddressDialogViewModel {
//                return UtImmortalViewModelHelper.createBy(AddressDialogViewModel::class.java, task).apply {
//                    address.value = initialAddress
//                }
//            }
//            fun instanceFor(dialog: AddressDialog):AddressDialogViewModel {
//                return UtImmortalViewModelHelper.instanceFor(AddressDialogViewModel::class.java, dialog)
//            }
        }
    }

    val viewModel by lazy { getViewModel<AddressDialogViewModel>() }
    lateinit var controls: DialogAddressBinding

    override fun preCreateBodyView() {
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.COMPACT
        title = requireActivity().getString(R.string.secure_archive_address)
        enableFocusManagement()
            .setInitialFocus(R.id.address)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
//        viewModel = ViewModelProvider(requireActivity())[AddressDialogViewModel::class.java]
        controls = DialogAddressBinding.inflate(inflater.layoutInflater)
        return controls.root.also { _ ->
            binder.editTextBinding(controls.address, viewModel.address, BindingMode.TwoWay)
        }
    }

//    override fun onPositive() {
//        viewModel.save()
//        super.onPositive()
//    }

    companion object {
        suspend fun show(initialAddress: String): String? {
            return UtImmortalTask.awaitTaskResult("editAddress") {
                val vm = createViewModel<AddressDialogViewModel> { initialize(initialAddress) }
                if(showDialog(taskName) { AddressDialog() }.status.positive) {
                    vm.address.value
                } else null
            }
        }
    }
}
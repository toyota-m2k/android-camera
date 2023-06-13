package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.editTextBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.IUtImmortalTaskContext
import io.github.toyota32k.dialog.task.IUtImmortalTaskMutableContextSource
import io.github.toyota32k.dialog.task.immortalTaskContext
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogAddressBinding
import io.github.toyota32k.secureCamera.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AddressDialog : UtDialogEx() {
    class AddressDialogViewModel : ViewModel() {
//        override lateinit var immortalTaskContext: IUtImmortalTaskContext
        val address = MutableStateFlow(Settings.SecureArchive.address)
        fun save() {
            Settings.SecureArchive.address = address.value
        }
    }

    val viewModel = AddressDialogViewModel()
    lateinit var controls: DialogAddressBinding

    override fun preCreateBodyView() {
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.DONE)
        gravityOption = GravityOption.CENTER
        setLimitWidth(400)
        heightOption = HeightOption.COMPACT
        title = requireActivity().getString(R.string.secure_archive_address)
        enableFocusManagement()
            .setInitialFocus(R.id.address)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogAddressBinding.inflate(inflater.layoutInflater)
        return controls.root.also { _ ->
            binder.editTextBinding(controls.address, viewModel.address, BindingMode.TwoWay)
        }
    }

    override fun onPositive() {
        viewModel.save()
        super.onPositive()
    }
}
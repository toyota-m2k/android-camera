package io.github.toyota32k.secureCamera.dialog

import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.materialRadioButtonGroupBinding
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.observe
import io.github.toyota32k.binder.sliderBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.*
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.client.TcClient
import io.github.toyota32k.secureCamera.databinding.DialogSettingBinding
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.shared.gesture.UtClickRepeater
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import java.util.*

class SettingDialog : UtDialogEx() {
    class SettingViewModel(application: Application) : AndroidViewModel(application), IUtImmortalTaskMutableContextSource, IUtPropOwner {
        override lateinit var immortalTaskContext: IUtImmortalTaskContext

        companion object {
            const val minNumberOfIncorrectPassword:Int = 2
            const val maxNumberOfIncorrectPassword:Int = 10
            fun clipNumberOfIncorrectPassword(v:Int):Int {
                return min(maxNumberOfIncorrectPassword,max(minNumberOfIncorrectPassword, v))
            }

            fun createBy(taskName:String, application: Application): SettingViewModel {
                return logger.chronos { UtImmortalTaskManager.taskOf(taskName)?.task?.createViewModel(application) ?: throw IllegalStateException("no task") }
            }
            fun instanceFor(dlg: SettingDialog): SettingViewModel {
                return logger.chronos { ViewModelProvider(dlg.immortalTaskContext, ViewModelProvider.NewInstanceFactory())[SettingViewModel::class.java] }
            }
        }
        enum class CameraTapAction(val value:Int, @IdRes val id:Int) {
            NONE(Settings.Camera.TAP_NONE, R.id.radio_camera_action_none),
            VIDEO(Settings.Camera.TAP_VIDEO, R.id.radio_camera_action_video),
            PHOTO(Settings.Camera.TAP_PHOTO, R.id.radio_camera_action_photo)
            ;
            object TapActionResolver : IIDValueResolver<Int> {
                override fun id2value(id: Int): Int? {
                    return enumValues<CameraTapAction>().find { it.id==id }?.value
                }

                override fun value2id(v: Int): Int {
                    return enumValues<CameraTapAction>().find { it.value==v }?.id ?: NONE.id
                }
            }
        }

        val cameraTapAction: MutableStateFlow<Int> = MutableStateFlow(Settings.Camera.tapAction)
        val cameraHidePanelOnStart: MutableStateFlow<Boolean> = MutableStateFlow(Settings.Camera.hidePanelOnStart)
        val playerSpanOfSkipForward: MutableStateFlow<Float> = MutableStateFlow(Settings.Player.spanOfSkipForward.toFloat()/1000f)
        val playerSpanOfSkipBackward: MutableStateFlow<Float> = MutableStateFlow(Settings.Player.spanOfSkipBackward.toFloat()/1000f)
        val securityEnablePassword: MutableStateFlow<Boolean> = MutableStateFlow(Settings.Security.enablePassword)
        val securityPassword: MutableStateFlow<String> = MutableStateFlow(Settings.Security.password)
        val securityNumberOfIncorrectPassword: MutableStateFlow<Int> = MutableStateFlow(Settings.Security.numberOfIncorrectPassword)
        val securityClearAllOnPasswordError = MutableStateFlow(Settings.Security.clearAllOnPasswordError)

        val passwordText: Flow<String> = securityPassword.map { if(it.isBlank()) application.getString(R.string.password_not_set) else application.getString(R.string.password_set) }
        val allowWrongPasswordText: Flow<String> = securityNumberOfIncorrectPassword.map { application.getString(R.string.max_pwd_error_label).format(Locale.US, clipNumberOfIncorrectPassword(it)) }
        val skipForwardText = playerSpanOfSkipForward.map { application.getString(R.string.skip_forward_by).format(Locale.US, it) }
        val skipBackwardText = playerSpanOfSkipBackward.map { application.getString(R.string.skip_backward_by).format(Locale.US, it) }

        val secureArchiveAddress = MutableStateFlow(Settings.SecureArchive.address)
        val secureArchiveAddressForDisplay = secureArchiveAddress.map { if(it.isNotEmpty()) it else "(u/a)" }

        val deviceName = MutableStateFlow(Settings.SecureArchive.deviceName)

        val commandNip = LiteCommand(this::updateNip)
        val commandSkipForward = LiteCommand(this::updateSkipForwardSpan)
        val commandSkipBackward = LiteCommand(this::updateSkipBackwardSpan)
        val commandEditAddress = LiteUnitCommand(this::editAddress)
        val commandDeviceName = LiteUnitCommand(this::editDeviceName)

        private fun updateNip(diff:Int) {
            val before = securityNumberOfIncorrectPassword.value
            val after = min(maxNumberOfIncorrectPassword, max(minNumberOfIncorrectPassword, before+diff))
            if(before!=after) {
                securityNumberOfIncorrectPassword.mutable.value = after
            }
        }
        private fun updateSkipForwardSpan(d:Float) {
            val v = playerSpanOfSkipForward.value + d
            playerSpanOfSkipForward.value = max(0.1f, min(30f, v))
        }


        private fun updateSkipBackwardSpan(d:Float) {
            val v = playerSpanOfSkipBackward.value + d
            playerSpanOfSkipBackward.value = max(0.1f, min(30f, v))
        }

        private fun editAddress() {
            viewModelScope.launch {
                if(AddressDialog.show()) {
                    secureArchiveAddress.value = Settings.SecureArchive.address
                }
            }
        }

        private fun editDeviceName() {
            viewModelScope.launch {
                val name = TextDialog.getText("Device Name", deviceName.value)
                if(name!=null) {
                    deviceName.value = name
                }
            }
        }

        fun save() {
            Settings.apply {
                Settings.Camera.tapAction = cameraTapAction.value
                Settings.Camera.hidePanelOnStart = cameraHidePanelOnStart.value
                Settings.Player.spanOfSkipForward = (playerSpanOfSkipForward.value*1000).toLong()
                Settings.Player.spanOfSkipBackward = (playerSpanOfSkipBackward.value*1000).toLong()
                Settings.Security.enablePassword = securityEnablePassword.value
                Settings.Security.password = securityPassword.value
                Settings.Security.clearAllOnPasswordError = securityClearAllOnPasswordError.value
                Settings.Security.numberOfIncorrectPassword = securityNumberOfIncorrectPassword.value
                Settings.SecureArchive.address = secureArchiveAddress.value
                Settings.SecureArchive.deviceName = deviceName.value
            }
            UtImmortalSimpleTask.run { TcClient.registerOwnerToSecureArchive() }
        }
        fun reset() {
            cameraTapAction.value = Settings.Camera.DEF_TAP_ACTION
            cameraHidePanelOnStart.value = Settings.Camera.DEF_HIDE_PANEL_ON_START
            playerSpanOfSkipForward.value = Settings.Player.DEF_SPAN_OF_SKIP_FORWARD.toFloat()/1000f
            playerSpanOfSkipBackward.value = Settings.Player.DEF_SPAN_OF_SKIP_BACKWARD.toFloat()/1000f
            securityEnablePassword.value = Settings.Security.DEF_ENABLE_PASSWORD
            securityPassword.value = Settings.Security.DEF_PASSWORD
            securityClearAllOnPasswordError.value =
                Settings.Security.DEF_CLEAR_ALL_ON_PASSWORD_ERROR
            securityNumberOfIncorrectPassword.value =
                Settings.Security.DEF_NUMBER_OF_INCORRECT_PASSWORD
        }
    }
    override fun preCreateBodyView() {
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.DONE)
        setLimitWidth(400)
        scrollable = true
        cancellable = false
        heightOption = HeightOption.AUTO_SCROLL
    }

    lateinit var controls:DialogSettingBinding
    val viewModel: SettingViewModel by lazy { SettingViewModel.instanceFor(this) }
    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogSettingBinding.inflate(inflater.layoutInflater)
        return logger.chronos { controls.root.also { _->
                binder
                    .textBinding(controls.passwordText, viewModel.passwordText)
                    .textBinding(controls.allowWrongPasswordText, viewModel.allowWrongPasswordText)
                    .textBinding(controls.skipForwardText, viewModel.skipForwardText)
                    .textBinding(controls.skipBackwardText, viewModel.skipBackwardText)
                    .checkBinding(controls.enablePasswordCheck, viewModel.securityEnablePassword)
                    .checkBinding(controls.blockPasswordErrorCheck, viewModel.securityClearAllOnPasswordError)
                    .checkBinding(controls.hidePanelOnCameraCheck, viewModel.cameraHidePanelOnStart)
                    .sliderBinding(controls.sliderSkipForward, viewModel.playerSpanOfSkipForward)
                    .sliderBinding(controls.sliderSkipBackward, viewModel.playerSpanOfSkipBackward)
                    .multiVisibilityBinding(arrayOf(controls.passwordGroup, controls.passwordCriteriaGroup), viewModel.securityEnablePassword, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                    .visibilityBinding(controls.passwordCountGroup, combine(viewModel.securityClearAllOnPasswordError,viewModel.securityEnablePassword) { c,s-> c&&s })
                    .textBinding(controls.secureArchiveAddressText, viewModel.secureArchiveAddressForDisplay)
                    .textBinding(controls.deviceName, viewModel.deviceName)
                    .bindCommand(viewModel.commandNip, controls.allowErrorPlus, +1)
                    .bindCommand(viewModel.commandNip, controls.allowErrorMinus, -1)
                    .bindCommand(viewModel.commandSkipBackward, controls.skipBackwardPlus, +0.1f)
                    .bindCommand(viewModel.commandSkipBackward, controls.skipBackwardMinus, -0.1f)
                    .bindCommand(viewModel.commandSkipForward, controls.skipForwardPlus, +0.1f)
                    .bindCommand(viewModel.commandSkipForward, controls.skipForwardMinus, -0.1f)
                    .bindCommand(viewModel.commandEditAddress, controls.editSecureArchiveAddressButton)
                    .bindCommand(viewModel.commandDeviceName, controls.editDeviceNameButton)
                    .materialRadioButtonGroupBinding(controls.radioCameraAction, viewModel.cameraTapAction,
                        SettingViewModel.CameraTapAction.TapActionResolver
                    )
                    .observe(viewModel.securityEnablePassword) {
                        if(it&&viewModel.securityPassword.value.isEmpty()) {
                            setPassword()
                        }
                    }
                    .bindCommand(LiteUnitCommand(::changePassword), controls.changePwdButton)
                    .bindCommand(LiteUnitCommand(::resetAll), controls.resetButton)
                    .add(UtClickRepeater(controls.skipBackwardMinus))
                    .add(UtClickRepeater(controls.skipBackwardPlus))
                    .add(UtClickRepeater(controls.skipForwardMinus))
                    .add(UtClickRepeater(controls.skipForwardPlus))
            }
        }
    }

    private fun resetAll() {
        viewModel.reset()
    }

    private fun setPassword() {
        CoroutineScope(Dispatchers.Main).launch {
            val pwd = PasswordDialog.newPassword()
            if(pwd.isNullOrEmpty()) {
                viewModel.securityEnablePassword.value = false
            } else {
                viewModel.securityPassword.value = pwd
            }
        }
    }
    private fun changePassword() {
        CoroutineScope(Dispatchers.Main).launch {
            val pwd = PasswordDialog.newPassword()
            if(!pwd.isNullOrEmpty()) {
                viewModel.securityPassword.value = pwd
            }
        }
    }

    override fun onPositive() {
        viewModel.save()
        super.onPositive()
    }
    companion object {
        val logger = UtLog("Setting", null, this::class.java)
        fun show(application: Application) {
            UtImmortalSimpleTask.run(this::class.java.name) {
                if(!PasswordDialog.checkPassword()) {
                    return@run false
                }
                SettingViewModel.createBy(taskName, application)
                showDialog(taskName) { SettingDialog() }
                true
            }
        }
    }
}
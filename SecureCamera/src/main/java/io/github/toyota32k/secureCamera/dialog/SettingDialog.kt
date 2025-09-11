package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
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
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.application
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.client.TcClient
import io.github.toyota32k.secureCamera.databinding.DialogSettingBinding
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.gesture.UtClickRepeater
import io.github.toyota32k.utils.lifecycle.asConstantLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

class SettingDialog : UtDialogEx() {
    class SettingViewModel : UtDialogViewModel(), IUtPropOwner {
        companion object {
            const val minNumberOfIncorrectPassword:Int = 2
            const val maxNumberOfIncorrectPassword:Int = 10
            fun clipNumberOfIncorrectPassword(v:Int):Int {
                return min(maxNumberOfIncorrectPassword,max(minNumberOfIncorrectPassword, v))
            }

//            fun createBy(taskName:String, application: Application): SettingViewModel {
//                return logger.chronos { UtImmortalTaskManager.taskOf(taskName)?.task?.createViewModel(application) ?: throw IllegalStateException("no task") }
//            }
//            fun instanceFor(dlg: SettingDialog): SettingViewModel {
//                return logger.chronos { ViewModelProvider(dlg.immortalTaskContext, ViewModelProvider.NewInstanceFactory())[SettingViewModel::class.java] }
//            }
        }
        enum class CameraTapAction(val value:Int, @param:IdRes val selfieId:Int) {
            NONE(Settings.Camera.TAP_NONE, R.id.radio_selfie_action_none),
            VIDEO(Settings.Camera.TAP_VIDEO, R.id.radio_selfie_action_video),
            PHOTO(Settings.Camera.TAP_PHOTO, R.id.radio_selfie_action_photo)
            ;
//            object TapActionResolver : IIDValueResolver<Int> {
//                override fun id2value(id: Int): Int? {
//                    return enumValues<CameraTapAction>().find { it.id==id }?.value
//                }
//
//                override fun value2id(v: Int): Int {
//                    return enumValues<CameraTapAction>().find { it.value==v }?.id ?: NONE.id
//                }
//            }
            object SelfieActionResolver : IIDValueResolver<Int> {
                override fun id2value(id: Int): Int? {
                    return enumValues<CameraTapAction>().find { it.selfieId==id }?.value
                }

                override fun value2id(v: Int): Int {
                    return enumValues<CameraTapAction>().find { it.value==v }?.selfieId ?: NONE.selfieId
                }
            }
        }

        // span (ms) --> log(span)
        private fun spanToLogSpan(span:Float):Float {
            return log10(span.toDouble()).toFloat()
        }
        // log(span) --> span (ms)
        private fun logSpanToSpan(logSpan:Float):Float {
            return 10f.pow(logSpan)
        }
        private fun roundSpanInMSec(span:Float):Long {
            return (span/100).roundToLong() * 100L
        }
        private fun roundSpanInSec(span:Float):Float {
            return (span/100).roundToLong() / 10f
        }
        fun formatSpanLabel(logSpan:Float, format:String="%.1f sec"):String {
            return format.format(Locale.US, roundSpanInSec(logSpanToSpan(logSpan)))
        }


//        val cameraTapAction: MutableStateFlow<Int> = MutableStateFlow(Settings.Camera.tapAction)
        val selfieAction: MutableStateFlow<Int> = MutableStateFlow(Settings.Camera.selfieAction)
        val preferHDR: MutableStateFlow<Boolean> = MutableStateFlow(Settings.Camera.preferHDR)
        val cameraHidePanelOnStart: MutableStateFlow<Boolean> = MutableStateFlow(Settings.Camera.hidePanelOnStart)
        val playerSpanOfSkipForward: MutableStateFlow<Float> = MutableStateFlow(spanToLogSpan(Settings.Player.spanOfSkipForward.toFloat()))
        val playerSpanOfSkipBackward: MutableStateFlow<Float> = MutableStateFlow(spanToLogSpan(Settings.Player.spanOfSkipBackward.toFloat()))
        val securityEnablePassword: MutableStateFlow<Boolean> = MutableStateFlow(Settings.Security.enablePassword)
        val securityPassword: MutableStateFlow<String> = MutableStateFlow(Settings.Security.password)
        val securityNumberOfIncorrectPassword: MutableStateFlow<Int> = MutableStateFlow(Settings.Security.numberOfIncorrectPassword)
        val securityClearAllOnPasswordError = MutableStateFlow(Settings.Security.clearAllOnPasswordError)

        val passwordText: Flow<String> = securityPassword.map { if(it.isBlank()) application.getString(R.string.password_not_set) else application.getString(R.string.password_set) }
        val allowWrongPasswordText: Flow<String> = securityNumberOfIncorrectPassword.map { application.getString(R.string.max_pwd_error_label).format(Locale.US, clipNumberOfIncorrectPassword(it)) }
        val skipForwardText = playerSpanOfSkipForward.map { formatSpanLabel(it, application.getString(R.string.skip_forward_by)) }
        val skipBackwardText = playerSpanOfSkipBackward.map { formatSpanLabel(it,application.getString(R.string.skip_backward_by)) }

        val secureArchiveAddress = MutableStateFlow(Settings.SecureArchive.primaryAddress)
        val secureArchive2ndAddress = MutableStateFlow(Settings.SecureArchive.secondaryAddress)
        val secureArchiveAddressForDisplay = secureArchiveAddress.map { it.ifEmpty { "(n/a)" } }
        val secureArchive2ndAddressForDisplay = secureArchive2ndAddress.map { it.ifEmpty { "(n/a)" } }

        val deviceName = MutableStateFlow(Settings.SecureArchive.deviceName)

        val commandNip = LiteCommand(this::updateNip)
        val commandSkipForward = LiteCommand(this::updateSkipForwardSpan)
        val commandSkipBackward = LiteCommand(this::updateSkipBackwardSpan)
        val commandEditAddress = LiteCommand(this::editAddress)
        val commandDeviceName = LiteUnitCommand(this::editDeviceName)

        private fun updateNip(diff:Int) {
            val before = securityNumberOfIncorrectPassword.value
            val after = min(maxNumberOfIncorrectPassword, max(minNumberOfIncorrectPassword, before+diff))
            if(before!=after) {
                securityNumberOfIncorrectPassword.mutable.value = after
            }
        }
        private fun updateSpan(currentLogSpan:Float,diff:Float):Float {
            val current = logSpanToSpan(currentLogSpan)
            val newValue = if(diff>0) {
                when {
                    current < 1000f -> current + 100f
                    else -> ceil((current + 1000f)/1000f)*1000f
                }
            } else {
                when {
                    current <= 2000f -> current - 100f
                    else -> floor((current - 1000f)/1000f)*1000f
                }
            }
            val span = max(100f, min( 60000f, newValue))
            return spanToLogSpan(span)
        }

        private fun updateSkipForwardSpan(d:Float) {
            playerSpanOfSkipForward.value = updateSpan(playerSpanOfSkipForward.value, d)
        }


        private fun updateSkipBackwardSpan(d:Float) {
            playerSpanOfSkipBackward.value = updateSpan(playerSpanOfSkipBackward.value, d)
        }

        private fun editAddress(n:Int) {
            viewModelScope.launch {
                val saa = if(n==0) secureArchiveAddress else secureArchive2ndAddress
                val address = AddressDialog.show(saa.value)
                if(address!=null) {
                    saa.value = address
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
//                Settings.Camera.tapAction = cameraTapAction.value
                Settings.Camera.selfieAction = selfieAction.value
                Settings.Camera.preferHDR = preferHDR.value
                Settings.Camera.hidePanelOnStart = cameraHidePanelOnStart.value
                Settings.Player.spanOfSkipForward = roundSpanInMSec(logSpanToSpan(playerSpanOfSkipForward.value))
                Settings.Player.spanOfSkipBackward = roundSpanInMSec(logSpanToSpan(playerSpanOfSkipBackward.value))
                Settings.Security.enablePassword = securityEnablePassword.value
                Settings.Security.password = securityPassword.value
                Settings.Security.clearAllOnPasswordError = securityClearAllOnPasswordError.value
                Settings.Security.numberOfIncorrectPassword = securityNumberOfIncorrectPassword.value
                Settings.SecureArchive.primaryAddress = secureArchiveAddress.value
                Settings.SecureArchive.secondaryAddress = secureArchive2ndAddress.value
                Settings.SecureArchive.deviceName = deviceName.value
            }
            UtImmortalTask.launchTask {
                TcClient.registerOwnerToSecureArchive()
            }
        }
        fun reset() {
//            cameraTapAction.value = Settings.Camera.DEF_TAP_ACTION
            selfieAction.value = Settings.Camera.DEF_SELFIE_ACTION
            preferHDR.value = Settings.Camera.DEF_PREFER_HDR
            cameraHidePanelOnStart.value = Settings.Camera.DEF_HIDE_PANEL_ON_START
            playerSpanOfSkipForward.value = spanToLogSpan(Settings.Player.DEF_SPAN_OF_SKIP_FORWARD.toFloat())
            playerSpanOfSkipBackward.value = spanToLogSpan(Settings.Player.DEF_SPAN_OF_SKIP_BACKWARD.toFloat())
            securityEnablePassword.value = Settings.Security.DEF_ENABLE_PASSWORD
            securityPassword.value = Settings.Security.DEF_PASSWORD
            securityClearAllOnPasswordError.value =
                Settings.Security.DEF_CLEAR_ALL_ON_PASSWORD_ERROR
            securityNumberOfIncorrectPassword.value =
                Settings.Security.DEF_NUMBER_OF_INCORRECT_PASSWORD
        }
    }

    override fun preCreateBodyView() {
        title = context.getString(R.string.system_settings)
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
        widthOption = WidthOption.LIMIT(500)
        scrollable = true
        cancellable = false
        gravityOption = GravityOption.CENTER
        heightOption = if(isPhone) {
            HeightOption.FULL
        } else {
            HeightOption.AUTO_SCROLL
        }
    }

    private lateinit var controls:DialogSettingBinding
    private val viewModel: SettingViewModel by lazy { getViewModel() }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogSettingBinding.inflate(inflater.layoutInflater)
        controls.sliderSkipForward.setLabelFormatter { viewModel.formatSpanLabel(it) }
        controls.sliderSkipBackward.setLabelFormatter { viewModel.formatSpanLabel(it) }

        return logger.chronos { controls.root.also { _->
                binder
                    .textBinding(controls.passwordText, viewModel.passwordText)
                    .textBinding(controls.allowWrongPasswordText, viewModel.allowWrongPasswordText)
                    .textBinding(controls.skipForwardText, viewModel.skipForwardText)
                    .textBinding(controls.skipBackwardText, viewModel.skipBackwardText)
                    .textBinding(controls.deviceId, Settings.SecureArchive.clientId.asConstantLiveData())
                    .checkBinding(controls.enablePasswordCheck, viewModel.securityEnablePassword)
                    .checkBinding(controls.blockPasswordErrorCheck, viewModel.securityClearAllOnPasswordError)
                    .checkBinding(controls.hidePanelOnCameraCheck, viewModel.cameraHidePanelOnStart)
                    .sliderBinding(controls.sliderSkipForward, viewModel.playerSpanOfSkipForward)
                    .sliderBinding(controls.sliderSkipBackward, viewModel.playerSpanOfSkipBackward)
                    .multiVisibilityBinding(arrayOf(controls.passwordGroup, controls.passwordCriteriaGroup), viewModel.securityEnablePassword, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                    .visibilityBinding(controls.passwordCountGroup, combine(viewModel.securityClearAllOnPasswordError,viewModel.securityEnablePassword) { c,s-> c&&s })
                    .textBinding(controls.secureArchiveAddressText, viewModel.secureArchiveAddressForDisplay)
                    .textBinding(controls.secureArchive2ndAddressText, viewModel.secureArchive2ndAddressForDisplay)
                    .textBinding(controls.deviceName, viewModel.deviceName)
                    .bindCommand(viewModel.commandNip, controls.allowErrorPlus, +1)
                    .bindCommand(viewModel.commandNip, controls.allowErrorMinus, -1)
                    .bindCommand(viewModel.commandSkipBackward, controls.skipBackwardPlus, +100f)
                    .bindCommand(viewModel.commandSkipBackward, controls.skipBackwardMinus, -100f)
                    .bindCommand(viewModel.commandSkipForward, controls.skipForwardPlus, +100f)
                    .bindCommand(viewModel.commandSkipForward, controls.skipForwardMinus, -100f)
                    .bindCommand(viewModel.commandEditAddress, controls.editSecureArchiveAddressButton, 0)
                    .bindCommand(viewModel.commandEditAddress, controls.editSecureArchive2ndAddressButton, 1)
                    .bindCommand(viewModel.commandDeviceName, controls.editDeviceNameButton)
//                    .materialRadioButtonGroupBinding(controls.radioCameraAction, viewModel.cameraTapAction,
//                        SettingViewModel.CameraTapAction.TapActionResolver
//                    )
                    .materialRadioButtonGroupBinding(controls.radioSelfieAction, viewModel.selfieAction,
                        SettingViewModel.CameraTapAction.SelfieActionResolver
                    )
                    .checkBinding(controls.enableHdrCheck, viewModel.preferHDR)

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
        suspend fun show() {
            UtImmortalTask.awaitTask (this::class.java.name) {
                if(!PasswordDialog.checkPassword()) {
                    return@awaitTask
                }
                createViewModel<SettingViewModel>()
                showDialog(taskName) { SettingDialog() }
            }
        }
    }
}
package io.github.toyota32k.secureCamera.settings

import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.bindit.*
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.*
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogSettingBinding
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.bindCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.lang.Integer.max
import java.lang.Integer.min
import java.util.*

class SettingDialog : UtDialog() {
    class SettingViewModel(application: Application) : AndroidViewModel(application), IUtImmortalTaskMutableContextSource, IUtPropOwner {
        override lateinit var immortalTaskContext: IUtImmortalTaskContext

        companion object {
            const val minNumberOfIncorrectPassword:Int = 2
            const val maxNumberOfIncorrectPassword:Int = 10
            fun clipNumberOfIncorrectPassword(v:Int):Int {
                return min(maxNumberOfIncorrectPassword,max(minNumberOfIncorrectPassword, v))
            }

            fun createBy(taskName:String, application: Application):SettingViewModel {
                return UtImmortalTaskManager.taskOf(taskName)?.task?.createViewModel(application) ?: throw IllegalStateException("no task")
            }
            fun instanceFor(dlg:SettingDialog):SettingViewModel {
                return ViewModelProvider(dlg.immortalTaskContext, ViewModelProvider.NewInstanceFactory())[SettingViewModel::class.java]
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
                    return enumValues<CameraTapAction>().find { it.value==v }?.id ?: CameraTapAction.NONE.id
                }
            }
        }

        val cameraTapAction: MutableStateFlow<Int> = MutableStateFlow(Settings.Camera.tapAction)
        val cameraHidePanelOnStart: StateFlow<Boolean> = MutableStateFlow(Settings.Camera.hidePanelOnStart)
        val playerSpanOfSkipForward: MutableStateFlow<Float> = MutableStateFlow(Settings.Player.spanOfSkipForward.toFloat()/1000f)
        val playerSpanOfSkipBackward: MutableStateFlow<Float> = MutableStateFlow(Settings.Player.spanOfSkipBackward.toFloat()/1000f)
        val securityEnablePassword: StateFlow<Boolean> = MutableStateFlow(Settings.Security.enablePassword)
        val securityPassword: StateFlow<String> = MutableStateFlow(Settings.Security.password)
        val securityNumberOfIncorrectPassword: StateFlow<Int> = MutableStateFlow(Settings.Security.numberOfIncorrectPassword)

        val passwordText: Flow<String> = securityPassword.map { if(it.isBlank()) application.getString(R.string.password_not_set) else application.getString(R.string.password_set) }
        val allowWrongPasswordText: Flow<String> = securityNumberOfIncorrectPassword.map { application.getString(R.string.max_pwd_error_label).format(Locale.US, clipNumberOfIncorrectPassword(it)) }
        val skipForwardText = playerSpanOfSkipForward.map { application.getString(R.string.skip_forward_by).format(Locale.US, it) }
        val skipBackwardText = playerSpanOfSkipBackward.map { application.getString(R.string.skip_backward_by).format(Locale.US, it) }

        private fun updateNip(diff:Int) {
            val before = securityNumberOfIncorrectPassword.value + diff
            val after = min(maxNumberOfIncorrectPassword, max(minNumberOfIncorrectPassword, before))
            if(before!=after) {
                securityNumberOfIncorrectPassword.mutable.value = after
            }
        }
        val commandNip = LiteCommand(this::updateNip)
    }
    override fun preCreateBodyView() {
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.DONE)
        setLimitWidth(400)
        scrollable = true
        heightOption = HeightOption.AUTO_SCROLL
    }

    lateinit var controls:DialogSettingBinding
    val viewModel: SettingViewModel by lazy { SettingViewModel.instanceFor(this) }
    val binder = Binder()
    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogSettingBinding.inflate(inflater.layoutInflater)
        return inflater.inflate(R.layout.dialog_setting).also { dlg->
            binder
                .owner(this)
                .textBinding(controls.passwordText, viewModel.passwordText)
                .textBinding(controls.allowWrongPasswordText, viewModel.allowWrongPasswordText)
                .textBinding(dlg.findViewById(R.id.skip_forward_text), viewModel.skipForwardText)
                .textBinding(controls.skipBackwardText, viewModel.skipBackwardText)
                .sliderBinding(dlg.findViewById(R.id.slider_skip_forward), viewModel.playerSpanOfSkipForward)
                .sliderBinding(controls.sliderSkipBackward, viewModel.playerSpanOfSkipBackward)
                .bindCommand(viewModel.commandNip, controls.allowErrorPlus, +1)
                .bindCommand(viewModel.commandNip, controls.allowErrorPlus, -1)
                .materialRadioButtonGroupBinding(controls.radioCameraAction, viewModel.cameraTapAction, SettingViewModel.CameraTapAction.TapActionResolver)

        }
    }

    companion object {
        fun show(application: Application) {
            UtImmortalSimpleTask.run(this::class.java.name) {
                SettingViewModel.createBy(taskName, application)
                showDialog(taskName) { SettingDialog() }
                true
            }
        }
    }
}
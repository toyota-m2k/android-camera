package io.github.toyota32k.secureCamera.settings

import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.bindit.textBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.*
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogSettingBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.lang.Integer.max
import java.lang.Integer.min
import java.util.*

class SettingDialog : UtDialogEx() {
    class SettingViewModel(application: Application) : AndroidViewModel(application), IUtImmortalTaskMutableContextSource {
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
        val cameraTapAction: StateFlow<Int> = MutableStateFlow(Settings.Camera.tapAction)
        val cameraHidePanelOnStart: StateFlow<Boolean> = MutableStateFlow(Settings.Camera.hidePanelOnStart)
        val playerSpanOfSkipForward: StateFlow<Float> = MutableStateFlow(Settings.Player.spanOfSkipForward.toFloat()/1000f)
        val playerSpanOfSkipBackward: StateFlow<Float> = MutableStateFlow(Settings.Player.spanOfSkipBackward.toFloat()/1000f)
        val securityEnablePassword: StateFlow<Boolean> = MutableStateFlow(Settings.Security.enablePassword)
        val securityPassword: StateFlow<String> = MutableStateFlow(Settings.Security.password)
        val securityNumberOfIncorrectPassword: StateFlow<Int> = MutableStateFlow(Settings.Security.numberOfIncorrectPassword)

        val passwordText: Flow<String> = securityPassword.map { if(it.isBlank()) application.getString(R.string.password_not_set) else application.getString(R.string.password_set) }
        val allowWrongPasswordText: Flow<String> = securityNumberOfIncorrectPassword.map { application.getString(R.string.max_pwd_error_label).format(Locale.US, clipNumberOfIncorrectPassword(it)) }
        val skipForwardText = playerSpanOfSkipForward.map { application.getString(R.string.skip_forward_by).format(Locale.US, it) }
        val skipBackwardText = playerSpanOfSkipBackward.map { application.getString(R.string.skip_backward_by).format(Locale.US, it) }
    }
    override fun preCreateBodyView() {
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.DONE)
        setLimitWidth(400)
        scrollable = true
        heightOption = HeightOption.AUTO_SCROLL
    }

    lateinit var controls:DialogSettingBinding
    lateinit var viewModel: SettingViewModel by lazy { }
    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogSettingBinding.inflate(inflater.layoutInflater)
        return inflater.inflate(R.layout.dialog_setting).also { dlg->
            binder.textBinding(controls.passwordText, view)
        }
    }
}
package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.bindit.LiteUnitCommand
import io.github.toyota32k.bindit.editTextBinding
import io.github.toyota32k.bindit.textBinding
import io.github.toyota32k.bindit.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.*
import io.github.toyota32k.secureCamera.MainActivity
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogPasswordBinding
import io.github.toyota32k.secureCamera.settings.HashGenerator
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.bindCommand
import io.github.toyota32k.utils.onTrue
import kotlinx.coroutines.flow.*

class PasswordDialog : UtDialogEx() {
    class PasswordViewModel : ViewModel(), IUtImmortalTaskMutableContextSource {
        override lateinit var immortalTaskContext: IUtImmortalTaskContext
        enum class Mode {
            NEW_PASSWORD,       // パスワード登録用（入力後、確認のためもう一回がでるやつ）
            CHECK_PASSWORD,     // パスワード照合用（n回までリトライ）
        }

        var mode: Mode = Mode.NEW_PASSWORD
        lateinit var passwordToCheck:String

        val password = MutableStateFlow("")
        val passwordConf = MutableStateFlow("")
        val message = MutableStateFlow("")

        enum class CheckPasswordResult {
            OK,
            NG,
            BLOCKED,
        }
        var checkResult: CheckPasswordResult = CheckPasswordResult.NG

        val ready : StateFlow<Boolean> = combine(password, passwordConf) { p, c ->
            when(mode) {
                Mode.NEW_PASSWORD -> p.isNotEmpty() && p==c
                Mode.CHECK_PASSWORD ->p.isNotEmpty()
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

        fun checkPasswords():Boolean {
            if(mode!= Mode.CHECK_PASSWORD) throw IllegalStateException("entering new password")
            if(password.value.isEmpty()) return false
            return passwordToCheck == HashGenerator.hash(password.value)
        }

        fun getPassword():String? {
            if(mode!= Mode.NEW_PASSWORD) throw IllegalStateException("checking passwords")
            if(password.value.isEmpty()) return null
            return HashGenerator.hash(password.value)
        }

        companion object {
            fun createForNewPassword(taskName:String): PasswordViewModel {
                return UtImmortalTaskManager.taskOf(taskName)?.task?.createViewModel() ?: throw IllegalStateException("no task")
            }

            fun createForCheckPassword(taskName:String, hashedPassword:String): PasswordViewModel {
                return UtImmortalTaskManager.taskOf(taskName)?.task?.createViewModel<PasswordViewModel>()?.apply {
                    mode = Mode.CHECK_PASSWORD
                    passwordToCheck = hashedPassword
                } ?: throw IllegalStateException("no task")
            }

            fun instanceFor(dlg: PasswordDialog): PasswordViewModel {
                return ViewModelProvider(dlg.immortalTaskContext, ViewModelProvider.NewInstanceFactory())[PasswordViewModel::class.java]
            }
        }
    }

    override fun preCreateBodyView() {
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.DONE)
        gravityOption = GravityOption.CENTER
        setLimitWidth(400)
        heightOption = HeightOption.COMPACT
        title = requireActivity().getString(R.string.password)
        enableFocusManagement()
            .setInitialFocus(R.id.password)
            .register(R.id.password)
            .register(R.id.password_confirm)
    }

    lateinit var controls: DialogPasswordBinding
    val viewModel: PasswordViewModel by lazy { PasswordViewModel.instanceFor(this) }


    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogPasswordBinding.inflate(inflater.layoutInflater)
        return controls.root.also { _ ->
            if(viewModel.mode== PasswordViewModel.Mode.NEW_PASSWORD) {
                controls.passwordInputLayout.hint = getString(R.string.password_hint_new)
                controls.confirmPasswordInputLayout.visibility = View.VISIBLE
            }
            val commandDone = LiteUnitCommand(::onPositive)
            binder
                .editTextBinding(controls.password, viewModel.password)
                .editTextBinding(controls.passwordConfirm, viewModel.passwordConf)
                .dialogRightButtonEnable(viewModel.ready)
                .textBinding(controls.message, viewModel.message)
                .visibilityBinding(controls.message, viewModel.message.map { it.isNotEmpty() })
                .dialogRightButtonEnable(viewModel.ready)
                .bindCommand(commandDone, controls.password)
                .bindCommand(commandDone, controls.passwordConfirm)
        }
    }

    override fun onPositive() {
        if(viewModel.mode == PasswordViewModel.Mode.CHECK_PASSWORD) {
            if(viewModel.password.value.isEmpty()) {
                controls.password.requestFocus()
                return
            }
            if(!checkPassword()) {
                return
            }
        } else {
            if(!viewModel.ready.value) {
                if (viewModel.password.value.isEmpty()) {
                    controls.password.requestFocus()
                } else {
                    controls.passwordConfirm.requestFocus()
                }
            }
        }
        super.onPositive()
    }

    private fun checkPassword():Boolean {
        if(!viewModel.ready.value) return false
        if(viewModel.mode!= PasswordViewModel.Mode.CHECK_PASSWORD) throw java.lang.IllegalStateException("${viewModel.mode}")
        if(viewModel.checkPasswords()) {
            viewModel.checkResult = PasswordViewModel.CheckPasswordResult.OK
            Settings.Security.incorrectCount = 0    // check ok なら 失敗カウンタをクリア
            return true
        }

        var msg = getString(R.string.password_incorrect)
        if(Settings.Security.numberOfIncorrectPassword >1) {
            Settings.Security.incorrectCount++
            if(Settings.Security.incorrectCount >= Settings.Security.numberOfIncorrectPassword) {
                // Blocked
                onPasswordBlocked()
                return true
            }
            msg = msg + " (${Settings.Security.incorrectCount}/${Settings.Security.numberOfIncorrectPassword})"
        }
        viewModel.message.value = msg
        return false
    }

    private fun onPasswordBlocked() {
        viewModel.checkResult = PasswordViewModel.CheckPasswordResult.BLOCKED
        // データを消す
        MainActivity.resetAll(true)
    }

    companion object {
        val logger = UtLog("PWD", null, PasswordDialog::class.java)

        suspend fun checkPassword():Boolean {
            if(!Settings.Security.enablePassword) return true
            return UtImmortalSimpleTask.runAsync("checkPassword") {
                val vm =
                    PasswordViewModel.createForCheckPassword(taskName, Settings.Security.password)
                showDialog(taskName) { PasswordDialog() }.status.ok.onTrue {
                    if(vm.checkResult== PasswordViewModel.CheckPasswordResult.BLOCKED) {
                        showConfirmMessageBox(getString(R.string.password), getString(R.string.password_error))
                    }
                }
            }
        }

        suspend fun newPassword():String? {
            return UtImmortalSimpleTask.executeAsync("newPassword") {
                val vm = PasswordViewModel.createForNewPassword(taskName)
                if(showDialog(taskName) { PasswordDialog() }.status.ok) {
                   vm.getPassword()
                } else null
            }
        }
    }
}

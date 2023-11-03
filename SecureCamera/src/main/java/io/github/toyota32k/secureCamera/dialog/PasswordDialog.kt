package io.github.toyota32k.secureCamera.dialog

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.ReliableUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.editTextBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.*
import io.github.toyota32k.secureCamera.MainActivity
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.databinding.DialogPasswordBinding
import io.github.toyota32k.secureCamera.settings.HashGenerator
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class PasswordDialog : UtDialogEx() {
    class PasswordViewModel : ViewModel(), IUtImmortalTaskMutableContextSource {
        override lateinit var immortalTaskContext: IUtImmortalTaskContext
        enum class Mode {
            NEW_PASSWORD,       // パスワード登録用（入力後、確認のためもう一回がでるやつ）
            CHECK_PASSWORD,     // パスワード照合用（n回までリトライ）
            SA_AUTH,            // SecureArchive Auth
        }

        var mode: Mode = Mode.NEW_PASSWORD
        lateinit var passwordToCheck:String

        val password = MutableStateFlow("")
        val passwordConf = MutableStateFlow("")
        val message = MutableStateFlow("")
//        val completeCommand = ReliableUnitCommand()

        enum class CheckPasswordResult {
            OK,
            NG,
            BLOCKED,
        }
        var checkResult: CheckPasswordResult = CheckPasswordResult.NG

        val ready : StateFlow<Boolean> = combine(password, passwordConf) { p, c ->
            when(mode) {
                Mode.NEW_PASSWORD -> p.isNotEmpty() && p==c
                Mode.CHECK_PASSWORD,Mode.SA_AUTH-> p.isNotEmpty()
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

//        fun authenticate() {
//            UtImmortalSimpleTask.run("remote execute") {
//                if(password.value.isEmpty()) return@run false
//                if(Authentication.authWithPassword(password.value)) {
//                    completeCommand.invoke()
//                    true
//                } else false
//            }
//        }
        suspend fun authenticate():Boolean {
            if(password.value.isEmpty()) return false
            return Authentication.authWithPassword(password.value)
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
            fun createForAuthentication(taskName:String): PasswordViewModel {
                return UtImmortalTaskManager.taskOf(taskName)?.task?.createViewModel<PasswordViewModel>()?.apply {
                    mode = Mode.SA_AUTH
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
        title = requireActivity().getString(if(viewModel.mode == PasswordViewModel.Mode.SA_AUTH) R.string.authentication else R.string.password)
        enableFocusManagement()
            .setInitialFocus(R.id.password)
            .register(R.id.password)
            .register(R.id.password_confirm)
    }

    lateinit var controls: DialogPasswordBinding
    val viewModel: PasswordViewModel by lazy { PasswordViewModel.instanceFor(this) }


    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogPasswordBinding.inflate(inflater.layoutInflater)
        if(viewModel.mode!=PasswordViewModel.Mode.NEW_PASSWORD) {
            controls.password.imeOptions = EditorInfo.IME_ACTION_DONE
        }
        return controls.root.also { _ ->
            if(viewModel.mode== PasswordViewModel.Mode.NEW_PASSWORD) {
                controls.passwordInputLayout.hint = getString(R.string.password_hint_new)
                controls.confirmPasswordInputLayout.visibility = View.VISIBLE
            }
            val commandDone = LiteUnitCommand(::action)
            binder
                .editTextBinding(controls.password, viewModel.password)
                .editTextBinding(controls.passwordConfirm, viewModel.passwordConf)
                .dialogRightButtonEnable(viewModel.ready)
                .textBinding(controls.message, viewModel.message)
                .visibilityBinding(controls.message, viewModel.message.map { it.isNotEmpty() })
                .dialogRightButtonEnable(viewModel.ready)
                .bindCommand(commandDone, controls.password)
                .bindCommand(commandDone, controls.passwordConfirm)
//                .bindCommand(viewModel.completeCommand, ::onPositive)
        }
    }

    private val busy = AtomicBoolean(false)
    private fun action() {
        if(busy.getAndSet(true)) {
            return
        }
        lifecycleScope.launch {
            try {
                if (viewModel.password.value.isEmpty()) {
                    controls.password.requestFocus()
                    return@launch
                }
                when (viewModel.mode) {
                    PasswordViewModel.Mode.NEW_PASSWORD -> {
                        if (!viewModel.ready.value) {
                            controls.passwordConfirm.requestFocus()
                            return@launch
                        }
                    }

                    PasswordViewModel.Mode.CHECK_PASSWORD -> {
                        if (!checkPassword()) {
                            return@launch
                        }
                    }

                    PasswordViewModel.Mode.SA_AUTH -> {
                        if(!viewModel.authenticate()) {
                            return@launch
                        }
                    }
                }
            } finally {
                busy.set(false)
            }
            super.onPositive()
        }
    }

    override fun onPositive() {
        action()
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
                val vm = PasswordViewModel.createForCheckPassword(taskName, Settings.Security.password)
                if(showDialog(taskName) { PasswordDialog() }.status.ok) {
                    when (vm.checkResult) {
                        PasswordViewModel.CheckPasswordResult.NG -> false
                        PasswordViewModel.CheckPasswordResult.OK -> true
                        PasswordViewModel.CheckPasswordResult.BLOCKED -> {
                            showConfirmMessageBox(getString(R.string.password),getString(R.string.password_error))
                            true
                        }
                    }
                } else false
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

        suspend fun authenticate():Boolean {
            return UtImmortalSimpleTask.runAsync("auth") {
                PasswordViewModel.createForAuthentication(taskName)
                showDialog(taskName) { PasswordDialog() }.status.ok
            }
        }
    }
}

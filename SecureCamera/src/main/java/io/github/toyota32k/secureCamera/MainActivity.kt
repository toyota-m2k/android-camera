package io.github.toyota32k.secureCamera

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.mortal.UtMortalActivity
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.getStringOrNull
import io.github.toyota32k.dialog.task.showOkCancelMessageBox
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.databinding.ActivityMainBinding
import io.github.toyota32k.secureCamera.dialog.ColorVariationDialog
import io.github.toyota32k.secureCamera.dialog.PasswordDialog
import io.github.toyota32k.secureCamera.dialog.SettingDialog
import io.github.toyota32k.secureCamera.dialog.SlotDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.settings.SlotSettings
import io.github.toyota32k.secureCamera.utils.PackageUtil
import io.github.toyota32k.utils.android.hideActionBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : UtMortalActivity() {
    override val logger = UtLog("MAIN")

    class MainViewModel : ViewModel() {
        val busy = MutableStateFlow<Boolean>(false)
    }

    private val binder = Binder()
    private val viewModel by viewModels<MainViewModel>()
    private lateinit var controls: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
//        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        Settings.Design.applyToActivity(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()  // 最近(2024/3/28現在)のAndroid Studioのテンプレートが書き出すコード（１）。。。タブレットでステータスバーなどによってクライアント領域が不正になる現象が回避できるっぽい。、

//        setTheme(R.style.Theme_TryCamera_M3_DynamicColor)
//        setTheme(R.style.Theme_TryCamera_M3_Cherry_NoActionBar)

        controls = ActivityMainBinding.inflate(layoutInflater)
        setContentView(controls.root)

        // 最近(2024/3/28現在)のAndroid Studioのテンプレートが書き出すコード（２）
        ViewCompat.setOnApplyWindowInsetsListener(controls.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

//        this.title = "${PackageUtil.appName(this)} v${PackageUtil.getVersion(this)}"
        @SuppressLint("SetTextI18n")
        controls.appName.text = "${PackageUtil.appName(this)} v${PackageUtil.getVersion(this)} ${if(BuildConfig.DEBUG) "(d)" else ""}"
//        setContentView(R.layout.activity_main)

        hideActionBar()
        binder.owner(this)
            .bindCommand(LiteUnitCommand(::startCamera), controls.cameraButton )
            .bindCommand(LiteUnitCommand(::startPlayer), controls.playerButton )
            .bindCommand(LiteUnitCommand(::startServer), controls.serverButton )
            .bindCommand(LiteUnitCommand(::clearAllData), controls.clearAllButton)
            .bindCommand(LiteUnitCommand(::setting), controls.settingsButton)
            .bindCommand(LiteUnitCommand(::colorVariation), controls.colorsButton)
            .bindCommand(LiteUnitCommand(::setupSlots), controls.slotButton)
            .multiEnableBinding(arrayOf(controls.cameraButton, controls.playerButton, controls.serverButton, controls.settingsButton), viewModel.busy, boolConvert = BoolConvert.Inverse)
            .textBinding(controls.slotName, SlotSettings.currentSlotFlow.map { it.safeSlotName })
    }

    private fun setupSlots() {
        viewModel.busy.value = true
        lifecycleScope.launch {
            try {
                SlotDialog.show()
            } finally {
                viewModel.busy.value = false
            }
        }
    }

    private fun startCamera() {
        startActivity(Intent(this, CameraActivity::class.java))
    }

    private fun startPlayer() {
        viewModel.busy.value = true
        lifecycleScope.launch {
            if(PasswordDialog.checkPassword()) {
                startActivity(Intent(this@MainActivity, PlayerActivity::class.java))
            } else {
                logger.error("Incorrect Password")
            }
            viewModel.busy.value = false
        }
    }

    private fun startServer() {
        viewModel.busy.value = true
        lifecycleScope.launch {
            if(PasswordDialog.checkPassword()) {
                startActivity(Intent(this@MainActivity, ServerActivity::class.java))
            }
            viewModel.busy.value = false
        }
    }

    private fun setting() {
        viewModel.busy.value = true
        lifecycleScope.launch {
            SettingDialog.show()
            viewModel.busy.value = false
        }
    }

    private fun colorVariation() {
        ColorVariationDialog.show()
    }

    companion object {
        private fun clearAllData() {
            UtImmortalTask.launchTask("ClearAll") {
                if(showOkCancelMessageBox(getStringOrNull(R.string.clear_all), getStringOrNull(R.string.msg_confirm))) {
                    resetAll(false)
                }
            }
        }

        fun resetAll(resetSettings:Boolean=false) {
            UtImmortalTaskManager.application.apply {
                for (name in fileList()) {
                    deleteFile(name)
                }
                if(resetSettings) {
                    Settings.reset()
                }
            }
        }

    }

}
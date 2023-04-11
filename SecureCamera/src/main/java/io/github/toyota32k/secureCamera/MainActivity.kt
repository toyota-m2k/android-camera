package io.github.toyota32k.secureCamera

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.LiteUnitCommand
import io.github.toyota32k.dialog.UtStandardString
import io.github.toyota32k.dialog.task.*
import io.github.toyota32k.secureCamera.databinding.ActivityMainBinding
import io.github.toyota32k.secureCamera.dialog.PasswordDialog
import io.github.toyota32k.secureCamera.settings.SettingDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.bindCommand
import kotlinx.coroutines.launch

class MainActivity : UtMortalActivity() {
    override val logger = UtLog("MAIN")

    class MainViewModel : ViewModel() {
    }

    private val binder = Binder()
    private val viewModel by viewModels<MainViewModel>()
    private lateinit var controls: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.initialize(this)
        controls = ActivityMainBinding.inflate(layoutInflater)
        setContentView(controls.root)
        UtStandardString.setContext(applicationContext)
//        setContentView(R.layout.activity_main)

        binder.owner(this)
            .bindCommand(LiteUnitCommand(::startCamera), controls.cameraButton )
            .bindCommand(LiteUnitCommand(::startPlayer), controls.playerButton )
            .bindCommand(LiteUnitCommand(::clearAllData), controls.clearAllButton)
            .bindCommand(LiteUnitCommand(::setting), controls.settingsButton)
    }

    private fun startCamera() {
        startActivity(Intent(this, CameraActivity::class.java))
    }

    private fun startPlayer() {
        lifecycleScope.launch {
            if(PasswordDialog.checkPassword()) {
                startActivity(Intent(this@MainActivity, PlayerActivity::class.java))
            }
        }
    }
    private fun setting() {
        SettingDialog.show(application)
//        startActivity(Intent(this, OssLicensesMenuActivity::class.java))
    }

    companion object {
        private fun clearAllData() {
            UtImmortalSimpleTask.run("ClearAll") {
                if(showOkCancelMessageBox(getString(R.string.clear_all), getString(R.string.msg_confirm))) {
                    resetAll(false)
                }
                true
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
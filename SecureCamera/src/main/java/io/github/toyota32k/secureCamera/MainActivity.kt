package io.github.toyota32k.secureCamera

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
//import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import io.github.toyota32k.secureCamera.databinding.ActivityMainBinding
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.LiteUnitCommand
import io.github.toyota32k.dialog.UtMessageBox
import io.github.toyota32k.dialog.UtStandardString
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.dialog.task.showOkCancelMessageBox
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.bindCommand

class MainActivity : UtMortalActivity() {
    override val logger = UtLog("MAIN")

    class MainViewModel : ViewModel() {
    }

    private val binder = Binder()
    private val viewModel by viewModels<MainViewModel>()
    private lateinit var controls: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controls = ActivityMainBinding.inflate(layoutInflater)
        setContentView(controls.root)
        UtStandardString.setContext(applicationContext)
//        setContentView(R.layout.activity_main)

        binder.owner(this)
            .bindCommand(LiteUnitCommand {startActivity(Intent(this, CameraActivity::class.java))}, controls.cameraButton )
            .bindCommand(LiteUnitCommand {startActivity(Intent(this, PlayerActivity::class.java))}, controls.playerButton )
            .bindCommand(LiteUnitCommand(::clearAll), controls.clearAllButton)
            .bindCommand(LiteUnitCommand(::showLicense), controls.settingsButton)
    }

    private fun clearAll() {
        UtImmortalSimpleTask.run("ClearAll") {
            if(showOkCancelMessageBox(getString(R.string.clear_all), getString(R.string.msg_confirm))) {
                withOwner { owner ->
                    owner.asContext().apply {
                        for (name in fileList()) {
                            deleteFile(name)
                        }
                    }
                }
            }
            true
        }
    }

    private fun showLicense() {
//        startActivity(Intent(this, OssLicensesMenuActivity::class.java))
    }
}
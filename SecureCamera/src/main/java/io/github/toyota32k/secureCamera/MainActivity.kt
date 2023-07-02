package io.github.toyota32k.secureCamera

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.UtDialogConfig
import io.github.toyota32k.dialog.UtStandardString
import io.github.toyota32k.dialog.task.*
import io.github.toyota32k.secureCamera.databinding.ActivityMainBinding
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.dialog.PasswordDialog
import io.github.toyota32k.secureCamera.settings.SettingDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.UtLog
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
        UtDialogConfig.solidBackgroundOnPhone = false   // phone の場合も、ダイアログの背景を灰色にしない
        UtDialogConfig.defaultGuardColorOfCancellableDialog = UtDialog.GuardColor.SEE_THROUGH.color
        Settings.initialize(this.application)
        MetaDB.initialize(this)
        controls = ActivityMainBinding.inflate(layoutInflater)
        setContentView(controls.root)
        UtStandardString.setContext(applicationContext)
//        setContentView(R.layout.activity_main)

        binder.owner(this)
            .bindCommand(LiteUnitCommand(::startCamera), controls.cameraButton )
            .bindCommand(LiteUnitCommand(::startPlayer), controls.playerButton )
            .bindCommand(LiteUnitCommand(::startServer), controls.serverButton )
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

    private fun startServer() {
        lifecycleScope.launch {
            if(PasswordDialog.checkPassword()) {
                startActivity(Intent(this@MainActivity, ServerActivity::class.java))
            }
        }
    }
//    @RequiresApi(Build.VERSION_CODES.R)
    private fun setting() {
//        fun Matrix.mapPoint(p: PointF) {
//            val points = floatArrayOf(p.x, p.y)
//            mapPoints(points)
//            p.x = points[0]
//            p.y = points[1]
//        }
//
//        fun Matrix.mapPoint(x: Float, y: Float): Pair<Float, Float> {
//            val points = floatArrayOf(x, y)
//            mapPoints(points)
//            return Pair(points[0], points[1])
//        }
//
//        val s0 = 1f
//        val s1 = 4f
//        val m1 = Matrix().apply {
//            postScale(s1, s1, 0f, 0f)
//        }
//
//        val p0 = PointF(1500f, 750f)
//
//        val p1 = PointF(p0)
//        m1.mapPoint(p1)
//        logger.debug("$p1")
//
//        var tx = -(p1.x - p0.x)/s1
//        var ty = -(p1.y - p0.y)/s1
//
//        val m2 = Matrix().apply {
//            postTranslate(tx,ty)
//            postScale(s1, s1, 0f, 0f)
//        }
//        val p2 = PointF(p0)
//        m2.mapPoint(p2)
//
//        logger.debug("$p2")
//
//        val rect1 = RectF(0f, 0f, 2000f, 1000f)
//        m2.mapRect(rect1)
//        logger.debug("$rect1")
//
//        val p3 = PointF(500f, 250f)
//        val s3 = 10f
//        val m3 = Matrix().apply {
//            postTranslate(tx,ty)
//            postScale(s3, s3, 0f, 0f)
//        }
//        val p4 = PointF(p3)
//        m3.mapPoint(p4)
//
//        val tx2 = tx - (p4.x - p3.x)/s3
//        val ty2 = ty - (p4.y - p3.y)/s3
//
//        val m4 = Matrix().apply {
//            postTranslate(tx2,ty2)
//            postScale(s3, s3, 0f, 0f)
//        }
//
//        val p5 = PointF(p3)
//        m4.mapPoint(p5)
//
//        logger.debug("$p5")




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
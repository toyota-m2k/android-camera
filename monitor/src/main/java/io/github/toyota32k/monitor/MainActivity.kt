package io.github.toyota32k.monitor

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.launch


class MainActivity : UtMortalActivity() {
    override val logger = UtLog("MAIN")
    val permissionsBroker = UtPermissionBroker(this)
    val cameraCreator: io.github.toyota32k.camera.CameraCreator by lazy {
        io.github.toyota32k.camera.CameraCreator(
            this
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        lifecycleScope.launch {
            if(permissionsBroker.requestPermission(Manifest.permission.CAMERA)) {
                val me = UtImmortalTaskManager.mortalInstanceSource.getOwner().asActivity() as MainActivity
                me.startCamera()
            }
        }
    }

    private suspend fun startCamera() {
        try {
            cameraCreator.createCamera(
                this,
                findViewById<PreviewView>(R.id.previewView),
                frontCamera = true,
                io.github.toyota32k.camera.CameraExtensions.Mode.NONE
            )

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            supportActionBar?.hide()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.hide(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_IMMERSIVE
                                // Set the content to appear under the system bars so that the
                                // content doesn't resize when the system bars hide and show.
                                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        )
            }
            val c = cameraCreator.getCapabilities()
                .fold(StringBuffer().append("Capabilities: ")) { acc, mode ->
                    acc.append(mode.toString()).append(", ")
                }.toString()
            logger.debug(c)
        } catch (e: Throwable) {
            logger.error(e)
        }
    }
}
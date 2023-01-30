package io.github.toyota32k.tryCamera

import android.Manifest
import android.os.Bundle
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.camera.CameraCreator
import io.github.toyota32k.camera.CameraExtensions
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
            cameraCreator.createCamera(this, findViewById<PreviewView>(R.id.previewView), false, io.github.toyota32k.camera.CameraExtensions.Mode.NONE)
//            val c = cameraCreator.getCapabilities().fold(StringBuffer()){acc,mode-> acc.append(mode.toString()).append(", ")}.toString()
//            logger.debug(c)
        } catch(e:Throwable) {
            logger.error(e)
        }
    }
}
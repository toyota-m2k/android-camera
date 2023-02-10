package io.github.toyota32k.monitor

import android.Manifest
import android.os.Bundle
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.bindit.*
import io.github.toyota32k.camera.CameraCreator
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.utils.ConstantLiveData
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.bindCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch


class MainActivity : UtMortalActivity() {
    override val logger = UtLog("MAIN")

    class MainViewModel : ViewModel() {
        val frontCameraSelected = MutableStateFlow(true)
    }

    val permissionsBroker = UtPermissionBroker(this)
    val cameraCreator: io.github.toyota32k.camera.CameraCreator by lazy {
        io.github.toyota32k.camera.CameraCreator(
            this
        )
    }
    val binder = Binder()
    val viewModel by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        lifecycleScope.launch {
            if(permissionsBroker.requestPermission(Manifest.permission.CAMERA)) {
                val me = UtImmortalTaskManager.mortalInstanceSource.getOwner().asActivity() as MainActivity
                me.startCamera(viewModel.frontCameraSelected.value)
            }
        }

        binder
            .owner(this)
            .headlessNonnullBinding(viewModel.frontCameraSelected) { changeCamera(it) }
            .actionBarVisibilityBinding(ConstantLiveData(false), interlockWithStatusBar = true)
            .bindCommand(LiteUnitCommand(this::settingDialog), findViewById<PreviewView>(R.id.previewView))


    }

    private fun settingDialog() {
        SettingDialog.show(viewModel.frontCameraSelected)
    }

    private fun changeCamera(front:Boolean) {
        currentCamera?.apply {
            if(frontCamera!=front) {
                lifecycleScope.launch { startCamera(front) }
            }
        }
    }

    var currentCamera:CameraCreator.CurrentCamera? = null

    private suspend fun startCamera(front: Boolean ) {
        try {
            currentCamera = cameraCreator.createCamera(
                this,
                findViewById<PreviewView>(R.id.previewView),
                frontCamera = front,
                io.github.toyota32k.camera.CameraExtensions.Mode.NONE
            )

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
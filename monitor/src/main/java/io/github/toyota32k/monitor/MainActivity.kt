package io.github.toyota32k.monitor

//import io.github.toyota32k.camera.CameraManager0
import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.activity.viewModels
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.actionBarVisibilityBinding
import io.github.toyota32k.bindit.headlessNonnullBinding
import io.github.toyota32k.camera.TcCamera
import io.github.toyota32k.camera.TcCameraManager
import io.github.toyota32k.camera.gesture.CameraGestureManager
import io.github.toyota32k.camera.gesture.ICameraGestureOwner
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : UtMortalActivity(), ICameraGestureOwner {
    override val logger = UtLog("MAIN")

    class MainViewModel : ViewModel() {
        val frontCameraSelected = MutableStateFlow(true)
        val showStatusBar = MutableLiveData<Boolean>(false)
    }

    private val permissionsBroker = UtPermissionBroker(this)
    private val cameraMamager: TcCameraManager by lazy { TcCameraManager.initialize(this) }
    private var currentCamera: TcCamera? = null
//    private val currentCamera:CameraManager0.CurrentCamera?
//        get() = cameraMamager.currentCamera
    private val binder = Binder()
    private val viewModel by viewModels<MainViewModel>()
    lateinit var cameraGestureManager:CameraGestureManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById<PreviewView>(R.id.previewView)
        previewView?.isClickable = true
        previewView?.isLongClickable = true

        gestureScope.launch {
            if(permissionsBroker.requestPermission(Manifest.permission.CAMERA)) {
                cameraMamager.prepare()
                val me = UtImmortalTaskManager.mortalInstanceSource.getOwner().asActivity() as MainActivity
                me.startCamera(viewModel.frontCameraSelected.value)
            }
        }


        binder
            .owner(this)
            .headlessNonnullBinding(viewModel.frontCameraSelected) { changeCamera(it) }
            .actionBarVisibilityBinding(viewModel.showStatusBar, interlockWithStatusBar = true)
//            .bindCommand(LongClickUnitCommand(this::settingDialog), previewView!!)
        cameraGestureManager = CameraGestureManager(this, true, true, customAction =  this::settingDialog)
        window.addFlags(FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        viewModel.showStatusBar.value = false
    }

    private fun settingDialog() {
        SettingDialog.show(viewModel.frontCameraSelected)
    }

    private fun changeCamera(front:Boolean) {
        val camera = currentCamera ?: return
        if(camera.frontCamera!=front) {
            lifecycleScope.launch { startCamera(front) }
        }
    }

//    var currentCamera:CameraCreator.CurrentCamera? = null

    private fun startCamera(front: Boolean) {
        try {
            val modes = cameraMamager.cameraExtensions.capabilitiesOf(front).fold(StringBuffer()) {acc,mode->
                if(acc.isNotEmpty()) {
                    acc.append(",")
                }
                acc.append(mode.toString())
                acc
            }.insert(0,"capabilities = ").toString()
            logger.debug(modes)

            currentCamera = cameraMamager.CameraBuilder()
                .frontCamera(front)
                .standardPreview(findViewById<PreviewView>(R.id.previewView))
                .build(this)

        } catch (e: Throwable) {
            logger.error(e)
        }
    }

    // ICameraGestureOwner
    override val context: Context
        get() = this
    override val gestureScope: CoroutineScope
        get() = this.lifecycleScope
    override var previewView: PreviewView? = null
        private set
    override val camera: Camera?
        get() = currentCamera?.camera
}
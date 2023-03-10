package io.github.toyota32k.monitor

//import io.github.toyota32k.camera.CameraManager0
import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.activity.viewModels
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.actionBarVisibilityBinding
import io.github.toyota32k.bindit.headlessNonnullBinding
import io.github.toyota32k.camera.lib.TcCamera
import io.github.toyota32k.camera.lib.TcCameraManager
import io.github.toyota32k.camera.lib.gesture.CameraGestureManager
import io.github.toyota32k.camera.lib.gesture.ICameraGestureOwner
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.monitor.databinding.ActivityMainBinding
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : UtMortalActivity(), ICameraGestureOwner {
    override val logger = UtLog("MAIN")

    class MainViewModel : ViewModel() {
        val frontCameraSelected = MutableStateFlow(true)
        val showStatusBar = MutableLiveData(false)
    }

    private val permissionsBroker = UtPermissionBroker(this)
    private val cameraManager: TcCameraManager by lazy { TcCameraManager.initialize(this) }
    private var currentCamera: TcCamera? = null
    private val binder = Binder()
    private val viewModel by viewModels<MainViewModel>()
    lateinit var cameraGestureManager: CameraGestureManager
    lateinit var controls:ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controls = ActivityMainBinding.inflate(layoutInflater)
        setContentView(controls.root)
        hideActionBar()
        hideStatusBar()
//        controls.previewView.apply {
//            isClickable = true
//            isLongClickable = true
//        }

        gestureScope.launch {
            if(permissionsBroker.requestPermission(Manifest.permission.CAMERA)) {
                cameraManager.prepare()
                val me = UtImmortalTaskManager.mortalInstanceSource.getOwner().asActivity() as MainActivity
                me.startCamera(viewModel.frontCameraSelected.value)
            }
        }


        binder
            .owner(this)
            .headlessNonnullBinding(viewModel.frontCameraSelected) { changeCamera(it) }
//            .actionBarVisibilityBinding(viewModel.showStatusBar, interlockWithStatusBar = true)
//            .bindCommand(LongClickUnitCommand(this::settingDialog), previewView!!)
        cameraGestureManager = CameraGestureManager.Builder()
            .enableZoomGesture()
            .enableFocusGesture()
            .longTapCustomAction(this::settingDialog)
            .build(this)
        window.addFlags(FLAG_KEEP_SCREEN_ON)
    }

    private fun hideActionBar() {
        supportActionBar?.hide()
    }
    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, controls.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.showStatusBar.value = false
    }

    private fun settingDialog():Boolean {
        SettingDialog.show(viewModel.frontCameraSelected)
        return true
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
            val modes = cameraManager.cameraExtensions.capabilitiesOf(front).fold(StringBuffer()) { acc, mode->
                if(acc.isNotEmpty()) {
                    acc.append(",")
                }
                acc.append(mode.toString())
                acc
            }.insert(0,"capabilities = ").toString()
            logger.debug(modes)

            currentCamera = cameraManager.CameraBuilder()
                .frontCamera(front)
                .standardPreview(controls.previewView)
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
    override val previewView: PreviewView
        get() = controls.previewView
    override val camera: Camera?
        get() = currentCamera?.camera
}
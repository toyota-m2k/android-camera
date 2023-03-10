package io.github.toyota32k.secureCamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.camera.core.Camera
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.view.PreviewView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.bindit.*
import io.github.toyota32k.lib.camera.TcCamera
import io.github.toyota32k.lib.camera.TcCameraManager
import io.github.toyota32k.lib.camera.TcLib
import io.github.toyota32k.lib.camera.gesture.CameraGestureManager
import io.github.toyota32k.lib.camera.gesture.ICameraGestureOwner
import io.github.toyota32k.lib.camera.usecase.ITcUseCase
import io.github.toyota32k.lib.camera.usecase.TcImageCapture
import io.github.toyota32k.lib.camera.usecase.TcVideoCapture
import io.github.toyota32k.dialog.broker.UtMultiPermissionsBroker
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.secureCamera.databinding.ActivityCameraBinding
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.bindCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class CameraActivity : UtMortalActivity(), ICameraGestureOwner {
    override val logger = UtLog("CAMERA")
    class CameraViewModel : ViewModel() {
        val frontCameraSelected = MutableStateFlow(true)
        val showControlPanel = MutableStateFlow(true)
        val fullControlPanel = MutableStateFlow(true)
        val recordingState = MutableStateFlow(TcVideoCapture.RecordingState.NONE)

        val expandPanelCommand = LiteCommand<Boolean> { fullControlPanel.value = it }
        val showPanelCommand = LiteCommand<Boolean> { showControlPanel.value = it }

        @ExperimentalZeroShutterLag // region UseCases
        val imageCapture by lazy { TcImageCapture.Builder().zeroLag().build() }
        // val videoCapture by lazy { TcVideoCapture.Builder().useFixedPoolExecutor().build() }

        // ???????????????????????????bindToLifecycle????????? VideoCapture ??????unbindAll()??????????????????????????????????????????????????????????????????
        // > IllegalStateException: Surface was requested when the Recorder had been initialized with state IDLING
        // ?????????????????????????????????????????????????????????????????????TcVideoCapture ?????????????????????????????????
        // ????????????????????????????????????????????????????????????????????????????????????????????????
        private var mVideoCapture: TcVideoCapture? = null
        val videoCapture: TcVideoCapture
            get() = mVideoCapture ?: TcVideoCapture.Builder().useFixedPoolExecutor().recordingStateFlow(recordingState).build().apply { mVideoCapture = this }

        /**
         * VideoCapture????????????????????????
         */
        fun resetVideoCaptureOnFlipCamera() {
            mVideoCapture?.dispose()
            mVideoCapture = null
        }

        override fun onCleared() {
            super.onCleared()
            videoCapture.dispose()
        }

        val takePictureCommand = LiteUnitCommand {
            viewModelScope.launch {
                val bitmap = imageCapture.takePicture() ?: return@launch
                val file = newImageFile()
                file.outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                    it.flush()
                }
            }
        }

        fun newVideoFile(): File {
            return File(TcLib.applicationContext.filesDir, ITcUseCase.defaultFileName("mov-", ".mp4"))
        }
        fun newImageFile(): File {
            return File(TcLib.applicationContext.filesDir, ITcUseCase.defaultFileName("img-", ".jpeg"))
        }

        @SuppressLint("MissingPermission")
        val takeVideoCommand = LiteUnitCommand {
            when (recordingState.value) {
                TcVideoCapture.RecordingState.NONE -> videoCapture.takeVideoInFile(newVideoFile())
                TcVideoCapture.RecordingState.STARTED -> videoCapture.pause()
                TcVideoCapture.RecordingState.PAUSING -> videoCapture.resume()
            }
        }
        val finalizeVideoCommand = LiteUnitCommand {
            if(recordingState.value != TcVideoCapture.RecordingState.NONE) {
                videoCapture.stop()
            }
        }
    }

    private val permissionsBroker = UtMultiPermissionsBroker(this)
    private val cameraManager: TcCameraManager by lazy { TcCameraManager.initialize(this) }
    private var currentCamera: TcCamera? = null
    private val binder = Binder()
    private val viewModel by viewModels<CameraViewModel>()
    lateinit var cameraGestureManager: CameraGestureManager

    private lateinit var controls: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controls = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(controls.root)
        hideActionBar()
        hideStatusBar()

//        controls.previewView.apply {
//            isClickable = true
//            isLongClickable = true
//        }

        binder
            .owner(this)
            .headlessNonnullBinding(viewModel.frontCameraSelected) { changeCamera(it) }
            .visibilityBinding(controls.controlPanel, viewModel.showControlPanel)
            .multiVisibilityBinding(arrayOf(controls.flipCameraButton, controls.closeButton), combine(viewModel.fullControlPanel,viewModel.recordingState) {full,state-> full && state== TcVideoCapture.RecordingState.NONE}, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.expandButton, combine(viewModel.fullControlPanel,viewModel.recordingState) {full, state-> !full && state== TcVideoCapture.RecordingState.NONE}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.collapseButton, combine(viewModel.fullControlPanel,viewModel.recordingState) {full, state-> full && state== TcVideoCapture.RecordingState.NONE}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.videoRecButton, viewModel.recordingState.map { it!= TcVideoCapture.RecordingState.STARTED}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.videoPauseButton, viewModel.recordingState.map { it== TcVideoCapture.RecordingState.STARTED}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.videoStopButton, viewModel.recordingState.map { it!= TcVideoCapture.RecordingState.NONE}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .bindCommand(viewModel.expandPanelCommand, controls.expandButton, true)
            .bindCommand(viewModel.expandPanelCommand, controls.collapseButton, false)
            .bindCommand(viewModel.showPanelCommand, controls.closeButton, false)
            .bindCommand(viewModel.takePictureCommand, controls.photoButton)
            .bindCommand(viewModel.takeVideoCommand, controls.videoRecButton, controls.videoPauseButton)
            .bindCommand(viewModel.finalizeVideoCommand, controls.videoStopButton)
            .bindCommand(LiteUnitCommand(this::toggleCamera), controls.flipCameraButton)

        cameraGestureManager = CameraGestureManager.Builder()
            .enableFocusGesture()
            .enableZoomGesture()
            .longTapCustomAction {
                viewModel.showControlPanel.value = !viewModel.showControlPanel.value
                true
            }
            .build(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gestureScope.launch {
            if(permissionsBroker.Request()
                    .add(Manifest.permission.CAMERA)
                    .add(Manifest.permission.RECORD_AUDIO)
                    .execute()) {
                cameraManager.prepare()
                val me = UtImmortalTaskManager.mortalInstanceSource.getOwner().asActivity() as? CameraActivity ?: return@launch
                me.startCamera(viewModel.frontCameraSelected.value)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            window?.insetsController?.hide(
//                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
//            )
//        } else {
//            @Suppress("DEPRECATION")
//            window?.decorView?.systemUiVisibility =
//                (
//                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//                        or View.SYSTEM_UI_FLAG_FULLSCREEN
//                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
//        }
    }

    private fun toggleCamera() {
        val current = currentCamera?.frontCamera ?: return
        changeCamera(!current)
    }
    private fun changeCamera(front:Boolean) {
        val camera = currentCamera ?: return
        if(camera.frontCamera!=front) {
            lifecycleScope.launch {
                startCamera(front)
            }
        }
    }

    private fun startCamera(front: Boolean) {
        try {
            viewModel.resetVideoCaptureOnFlipCamera()
            val modes = cameraManager.cameraExtensions.capabilitiesOf(front).fold(StringBuffer()) { acc, mode->
                if(acc.isNotEmpty()) {
                    acc.append(",")
                }
                acc.append(mode.toString())
                acc
            }.insert(0,"capabilities = ").toString()
            logger.debug(modes)

            @ExperimentalZeroShutterLag // region UseCases
            currentCamera = cameraManager.CameraBuilder()
                .frontCamera(front)
                .standardPreview(previewView)
                .imageCapture(viewModel.imageCapture)
                .videoCapture(viewModel.videoCapture)
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
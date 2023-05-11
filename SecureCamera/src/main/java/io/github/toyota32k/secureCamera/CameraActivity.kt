package io.github.toyota32k.secureCamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
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
import io.github.toyota32k.lib.camera.gesture.ICameraGestureOwner
import io.github.toyota32k.lib.camera.usecase.ITcUseCase
import io.github.toyota32k.lib.camera.usecase.TcImageCapture
import io.github.toyota32k.lib.camera.usecase.TcVideoCapture
import io.github.toyota32k.dialog.broker.UtMultiPermissionsBroker
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.lib.camera.TcCameraManipulator
import io.github.toyota32k.secureCamera.ScDef.PHOTO_EXTENSION
import io.github.toyota32k.secureCamera.ScDef.PHOTO_PREFIX
import io.github.toyota32k.secureCamera.ScDef.VIDEO_EXTENSION
import io.github.toyota32k.secureCamera.ScDef.VIDEO_PREFIX
import io.github.toyota32k.secureCamera.databinding.ActivityCameraBinding
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.utils.Direction
import io.github.toyota32k.secureCamera.utils.hideActionBar
import io.github.toyota32k.secureCamera.utils.hideStatusBar
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.UtObservableFlag
import io.github.toyota32k.utils.bindCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
//        val fullControlPanel = MutableStateFlow(true)
        val recordingState = MutableStateFlow(TcVideoCapture.RecordingState.NONE)

//        val expandPanelCommand = LiteCommand<Boolean> { fullControlPanel.value = it }
//        val showPanelCommand = LiteCommand<Boolean> { showControlPanel.value = it }

        @ExperimentalZeroShutterLag // region UseCases
        val imageCapture by lazy { TcImageCapture.Builder().zeroLag().build() }
        // val videoCapture by lazy { TcVideoCapture.Builder().useFixedPoolExecutor().build() }

        // 一旦カメラに接続（bindToLifecycle）した VideoCapture は、unbindAll()しても、別のカメラに接続し直すと例外が出る。
        // > IllegalStateException: Surface was requested when the Recorder had been initialized with state IDLING
        // これを回避するため、カメラを切り替える場合は、TcVideoCapture を作り直すことにする。
        // つまり、録画中にカメラを切り替える操作は（システム的に）不可能。
        private var mVideoCapture: TcVideoCapture? = null
        val videoCapture: TcVideoCapture
            get() = mVideoCapture ?: TcVideoCapture.Builder().useFixedPoolExecutor().recordingStateFlow(recordingState).build().apply { mVideoCapture = this }

        /**
         * VideoCaptureの再作成を予約。
         */
        fun resetVideoCaptureOnFlipCamera() {
            mVideoCapture?.dispose()
            mVideoCapture = null
        }

        override fun onCleared() {
            super.onCleared()
            videoCapture.dispose()
        }

        val pictureTakingStatus = MutableStateFlow<Boolean>(false)
        val takePictureCommand = LiteUnitCommand()

        fun takePicture(logger:UtLog) {
            viewModelScope.launch {
                pictureTakingStatus.value = true
                try {
                    val bitmap = imageCapture.takePicture() ?: return@launch
                    val file = newImageFile()
                    file.outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                        it.flush()
                    }
                } catch(e:Throwable) {
                    logger.error(e)
                } finally {
                    delay(200)
                    pictureTakingStatus.value = false
                }
            }
        }

        fun newVideoFile(): File {
            return File(TcLib.applicationContext.filesDir, ITcUseCase.defaultFileName(VIDEO_PREFIX, VIDEO_EXTENSION))
        }
        fun newImageFile(): File {
            return File(TcLib.applicationContext.filesDir, ITcUseCase.defaultFileName(PHOTO_PREFIX, PHOTO_EXTENSION))
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
    private val cameraManipulator : TcCameraManipulator by lazy { TcCameraManipulator(this, TcCameraManipulator.FocusActionBy.LongTap, rapidTap = false) }

    private lateinit var controls: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controls = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(controls.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

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
            .visibilityBinding(controls.miniRecIndicator, combine(viewModel.showControlPanel,viewModel.recordingState) {panel,rec-> !panel && rec==TcVideoCapture.RecordingState.STARTED}, boolConvert = BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.miniShutterIndicator, viewModel.pictureTakingStatus, boolConvert = BoolConvert.Straight, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.flipCameraButton, viewModel.recordingState.map { it==TcVideoCapture.RecordingState.NONE}, boolConvert = BoolConvert.Straight, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
//            .multiVisibilityBinding(arrayOf(controls.flipCameraButton, controls.closeButton), combine(viewModel.fullControlPanel,viewModel.recordingState) {full,state-> full && state== TcVideoCapture.RecordingState.NONE}, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.expandButton, combine(viewModel.fullControlPanel,viewModel.recordingState) {full, state-> !full && state== TcVideoCapture.RecordingState.NONE}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.collapseButton, combine(viewModel.fullControlPanel,viewModel.recordingState) {full, state-> full && state== TcVideoCapture.RecordingState.NONE}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.videoRecButton, viewModel.recordingState.map { it!= TcVideoCapture.RecordingState.STARTED}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.videoPauseButton, viewModel.recordingState.map { it== TcVideoCapture.RecordingState.STARTED}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.videoStopButton, viewModel.recordingState.map { it!= TcVideoCapture.RecordingState.NONE}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .bindCommand(viewModel.expandPanelCommand, controls.expandButton, true)
//            .bindCommand(viewModel.expandPanelCommand, controls.collapseButton, false)
//            .bindCommand(viewModel.showPanelCommand, controls.closeButton, false)
            .bindCommand(viewModel.takePictureCommand, controls.photoButton) { takePicture() }
            .bindCommand(viewModel.takeVideoCommand, controls.videoRecButton, controls.videoPauseButton)
            .bindCommand(viewModel.finalizeVideoCommand, controls.videoStopButton)
            .bindCommand(LiteUnitCommand(this::toggleCamera), controls.flipCameraButton)

//        cameraGestureManager = CameraGestureManager.Builder()
//            .enableFocusGesture()
//            .enableZoomGesture()
//            .longTapCustomAction {
//                viewModel.showControlPanel.value = !viewModel.showControlPanel.value
//                true
//            }
//            .build(this)

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        logger.debug("${newConfig.orientation}")
    }
    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if(isFinishing) {
            cameraManager.unbind()
        }
    }

//    private fun hideActionBar() {
//        supportActionBar?.hide()
//    }
//    private fun hideStatusBar() {
//        WindowCompat.setDecorFitsSystemWindows(window, false)
//        WindowInsetsControllerCompat(window, controls.root).let { controller ->
//            controller.hide(WindowInsetsCompat.Type.systemBars())
//            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//        }
//    }

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
                .apply {
                    cameraManipulator.attachCamera(this@CameraActivity, camera, controls.previewView) {
                        onFlickVertical {
                            viewModel.showControlPanel.value = it.direction == Direction.Start
                        }
                        onFlickHorizontal {
                            viewModel.showControlPanel.value = it.direction == Direction.Start
                        }
//                        onDoubleTap {
//                            if(!viewModel.showControlPanel.value) {
//                                viewModel.takeVideoCommand.invoke()
//                            }
//                        }
                        onTap {
                            if(!viewModel.showControlPanel.value) {
                                when(Settings.Camera.tapAction) {
                                    Settings.Camera.TAP_PHOTO -> {
                                        takePicture(it.x, it.y)
                                    }
                                    Settings.Camera.TAP_VIDEO -> {
                                        viewModel.takeVideoCommand.invoke()
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }


        } catch (e: Throwable) {
            logger.error(e)
        }
    }

    private fun takePicture(x:Float=-1f, y:Float=-1f) {
        if(x<0||y<0) {
            // 画面中央に表示
            controls.miniShutterIndicator.x = controls.root.width/2f - controls.miniShutterIndicator.width / 2f
            controls.miniShutterIndicator.y = controls.root.height/2f - controls.miniShutterIndicator.height / 2f
        } else {
            // 指定位置に表示
            controls.miniShutterIndicator.x = x - controls.miniShutterIndicator.width / 2
            controls.miniShutterIndicator.y = y - controls.miniShutterIndicator.height / 2
        }
        viewModel.takePicture(logger)
    }

    override fun handleKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
        if(keyCode==KeyEvent.KEYCODE_VOLUME_UP && event?.action==KeyEvent.ACTION_DOWN) {
            takePicture()
            return true
        }
        return super.handleKeyEvent(keyCode, event)
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
package io.github.toyota32k.secureCamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.camera.core.Camera
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.anim.VisibilityAnimation
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.headlessNonnullBinding
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.sliderBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.broker.UtMultiPermissionsBroker
import io.github.toyota32k.dialog.mortal.UtMortalActivity
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.lib.camera.TcCamera
import io.github.toyota32k.lib.camera.TcCameraManager
import io.github.toyota32k.lib.camera.TcCameraManipulator
import io.github.toyota32k.lib.camera.TcLib
import io.github.toyota32k.lib.camera.gesture.ICameraGestureOwner
import io.github.toyota32k.lib.camera.usecase.ITcUseCase
import io.github.toyota32k.lib.camera.usecase.TcImageCapture
import io.github.toyota32k.lib.camera.usecase.TcVideoCapture
import io.github.toyota32k.secureCamera.ScDef.PHOTO_EXTENSION
import io.github.toyota32k.secureCamera.ScDef.PHOTO_PREFIX
import io.github.toyota32k.secureCamera.ScDef.VIDEO_EXTENSION
import io.github.toyota32k.secureCamera.ScDef.VIDEO_PREFIX
import io.github.toyota32k.secureCamera.databinding.ActivityCameraBinding
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.utils.setSecureMode
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.disposableObserve
import io.github.toyota32k.utils.dp
import io.github.toyota32k.utils.gesture.Direction
import io.github.toyota32k.utils.hideActionBar
import io.github.toyota32k.utils.hideStatusBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class CameraActivity : UtMortalActivity(), ICameraGestureOwner {
    override val logger = UtLog("CAMERA")
    class CameraViewModel : ViewModel() {
        private var dbOpened:Boolean = false
        init {
            dbOpened = MetaDB.open()
        }

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
            if (dbOpened) {
                MetaDB.close()
            }
        }

        val pictureTakingStatus = MutableStateFlow(false)
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
                    MetaDB.register(file.name)
                } catch(e:Throwable) {
                    logger.error(e)
                } finally {
                    delay(200)
                    pictureTakingStatus.value = false
                }
            }
        }

        private fun newVideoFile(): File {
            return File(TcLib.applicationContext.filesDir, ITcUseCase.defaultFileName(VIDEO_PREFIX, VIDEO_EXTENSION))
        }
        private fun newImageFile(): File {
            return File(TcLib.applicationContext.filesDir, ITcUseCase.defaultFileName(PHOTO_PREFIX, PHOTO_EXTENSION))
        }

        @SuppressLint("MissingPermission")
        val takeVideoCommand = LiteUnitCommand {
            val recording = when (recordingState.value) {
                TcVideoCapture.RecordingState.NONE -> {
                    val file = newVideoFile()
                    videoCapture.takeVideoInFile(file) {
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(1000)
                            val len = file.length()
                            MetaDB.register(file.name)
                            if(len != file.length()) {
                                MetaDB.register(file.name)
                            }
                        }
                    }
                    true
                }
                TcVideoCapture.RecordingState.STARTED -> {
                    videoCapture.pause()
                    false
                }
                TcVideoCapture.RecordingState.PAUSING -> {
                    videoCapture.resume()
                    true
                }
            }
            if(recording && Settings.Camera.hidePanelOnStart) {
                // hidePanelOnStart == true の場合は、録画開始から５秒後にコントロールパネルを非表示にする
                viewModelScope.launch {
                    delay(5000)
                    if(recordingState.value == TcVideoCapture.RecordingState.STARTED) {
                        showControlPanel.value = false
                    }
                }
            }
        }
        val finalizeVideoCommand = LiteUnitCommand {
            if(recordingState.value != TcVideoCapture.RecordingState.NONE) {
                videoCapture.stop()
            }
        }

        val exposureCompensationAvailable = MutableStateFlow(false)
        val exposureCompensationIndex = MutableStateFlow(0f)
        val showExposureSlider = MutableStateFlow(false)
        val commandExposure = LiteCommand<Float>()
        var exposureMin = 0f
        var exposureMax = 0f
    }

    private val permissionsBroker = UtMultiPermissionsBroker().apply { register(this@CameraActivity) }
    private val cameraManager: TcCameraManager by lazy { TcCameraManager.initialize(this) }
    private var currentCamera: TcCamera? = null
    private val binder = Binder()
    private val exposeBinder = Binder()
    private val viewModel by viewModels<CameraViewModel>()
    private val cameraManipulator : TcCameraManipulator by lazy { TcCameraManipulator(this, TcCameraManipulator.FocusActionBy.Tap, rapidTap = true) }

    private lateinit var controls: ActivityCameraBinding

    private lateinit var focusIndicatorAnimation: VisibilityAnimation
    override fun onCreate(savedInstanceState: Bundle?) {
        Settings.Design.applyToActivity(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

//        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
//        setTheme(R.style.Theme_TryCamera_M3_DynamicColor_NoActionBar)
//        setTheme(R.style.Theme_TryCamera_M3_Cherry_NoActionBar)

        controls = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(controls.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.camera)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        hideActionBar()
        hideStatusBar()

//        controls.previewView.apply {
//            isClickable = true
//            isLongClickable = true
//        }

        val torchCommand = LiteCommand<Boolean> {
            cameraManager.torch.put(it)
        }
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
            .visibilityBinding(controls.videoRecButton, viewModel.recordingState.map { it == TcVideoCapture.RecordingState.NONE }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.videoStopButton, viewModel.recordingState.map { it != TcVideoCapture.RecordingState.NONE}, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.videoPauseButton, viewModel.recordingState.map { it == TcVideoCapture.RecordingState.STARTED }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.videoResumeButton, viewModel.recordingState.map { it == TcVideoCapture.RecordingState.PAUSING }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .bindCommand(viewModel.expandPanelCommand, controls.expandButton, true)
//            .bindCommand(viewModel.expandPanelCommand, controls.collapseButton, false)
//            .bindCommand(viewModel.showPanelCommand, controls.closeButton, false)
            .visibilityBinding(controls.lampOnButton, cameraManager.torch.isTouchOn, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.lampOffButton, cameraManager.torch.isTouchOn, boolConvert = BoolConvert.Inverse, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .multiEnableBinding(arrayOf(controls.lampOnButton,controls.lampOffButton), cameraManager.torch.isTorchAvailable, alphaOnDisabled = 0.2f)
            .bindCommand(viewModel.takePictureCommand, controls.photoButton) { takePicture() }
            .bindCommand(viewModel.takeVideoCommand, controls.videoRecButton, controls.videoPauseButton, controls.videoResumeButton)
            .bindCommand(viewModel.finalizeVideoCommand, controls.videoStopButton)
            .bindCommand(LiteUnitCommand(this::toggleCamera), controls.flipCameraButton)
            .bindCommand(torchCommand, controls.lampOnButton, false)
            .bindCommand(torchCommand, controls.lampOffButton, true)
            .clickBinding(controls.exposureButton) { showExposureSlider() }
            .clickBinding(controls.sliderGuardView) { hideExposureSlider() }
            .multiVisibilityBinding(arrayOf(controls.exposurePanel, controls.sliderGuardView), viewModel.showExposureSlider, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .add(exposeBinder)

//        cameraGestureManager = CameraGestureManager.Builder()
//            .enableFocusGesture()
//            .enableZoomGesture()
//            .longTapCustomAction {
//                viewModel.showControlPanel.value = !viewModel.showControlPanel.value
//                true
//            }
//            .build(this)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SECURE)

        focusIndicatorAnimation = VisibilityAnimation(300).apply {
            addView(controls.focusIndicator)
        }

        gestureScope.launch {
            if(permissionsBroker.Request()
                    .add(Manifest.permission.CAMERA)
                    .add(Manifest.permission.RECORD_AUDIO)
                    .execute()) {
                cameraManager.prepare()

                logger.debug("Supported Qualities ------------------------------------")
                cameraManager.supportedQualityList(isFront = true).forEach {
                    logger.debug("FRONT: $it")
                }
                cameraManager.supportedQualityList(isFront = false).forEach {
                    logger.debug("BACK: $it")
                }

                logger.debug("Supported Modes ------------------------------------")
                cameraManager.cameraExtensions.capabilitiesOf( isFront=true).forEach {
                    logger.debug("FRONT: $it")
                }
                cameraManager.cameraExtensions.capabilitiesOf( isFront=false).forEach {
                    logger.debug("BACK: $it")
                }



                val me = UtImmortalTaskManager.mortalInstanceSource.getOwner().asActivity() as? CameraActivity ?: return@launch
                me.startCamera(viewModel.frontCameraSelected.value)
            }
        }
    }

    private fun showExposureSlider() {
        val camera = currentCamera ?: return
        if(!viewModel.exposureCompensationAvailable.value) return
        exposeBinder.reset()
        viewModel.exposureMin = camera.exposureIndex.min.toFloat()
        viewModel.exposureMax = camera.exposureIndex.max.toFloat()
        controls.exposureSlider.apply {
            value = 0f
            valueFrom = viewModel.exposureMin
            valueTo = viewModel.exposureMax
        }
        exposeBinder
            .owner(this)
            .sliderBinding(controls.exposureSlider, viewModel.exposureCompensationIndex, mode= BindingMode.TwoWay)
            .bindCommand(viewModel.commandExposure, ::onExposureButtonTapped)
            .bindCommand(viewModel.commandExposure, controls.exposurePlus,1f)
            .bindCommand(viewModel.commandExposure, controls.exposureMinus,-1f)
            .add(viewModel.exposureCompensationIndex.disposableObserve(this) { index->
                lifecycleScope.launch {
                    camera.exposureIndex.setIndex(index.roundToInt())
                }
            })
        viewModel.showExposureSlider.value = true
        controls.exposureSlider
    }
    private fun hideExposureSlider() {
        viewModel.showExposureSlider.value = false
        exposeBinder.reset()
    }
    private fun onExposureButtonTapped(diff:Float) {
        val min = viewModel.exposureMin
        val max = viewModel.exposureMax
        val newValue = (viewModel.exposureCompensationIndex.value+diff).coerceIn(min,max)
//        viewModel.exposureCompensationIndex.value = newValue

        // +/- ボタンからスライダーの値をセットしたときにも、値ラベルを表示したいが、標準のAPIとして機能が公開されていないので、
        // タッチイベントをエミュレートして表示させる。
        controls.exposureSlider.apply {
            // Sliderは左右に 24dp 程度のマージンがあるので、これを差し引いて x座標を求める必要がある。
            val margin = 24.dp.px(this@CameraActivity)
            val x = margin + (this.width-margin*2) * (newValue - min) / (max-min)
            val y = this.height / 2f
            val tick = System.currentTimeMillis()
            val motionEvent = MotionEvent.obtain(tick,tick, MotionEvent.ACTION_MOVE, x, y, 0)
            this.onTouchEvent(motionEvent)
            motionEvent.recycle()
        }

    }


//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//        logger.debug("${newConfig.orientation}")
//    }

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
            currentCamera = cameraManager.createCamera(this) { builder->
                    builder
                    .frontCamera(front)
                    .standardPreview(previewView)
                    .imageCapture(viewModel.imageCapture)
                    .videoCapture(viewModel.videoCapture)
                }
                .apply {
                    viewModel.exposureCompensationAvailable.value = exposureIndex.isSupported
                    cameraManipulator.attachCamera(this@CameraActivity, camera, controls.previewView) {
                        onFlickVertical {
                            viewModel.showControlPanel.value = it.direction == Direction.Start
                        }
                        onFlickHorizontal {
                            viewModel.showControlPanel.value = it.direction == Direction.Start
                        }
                        onLongTap { window.setSecureMode() }
                        onFocusedAt {
                            logger.debug("onFocusedAt: (${it.x}, ${it.y})")
                            val w = controls.focusIndicator.width
                            val h = controls.focusIndicator.height
                            controls.focusIndicator.x = it.x - w/2
                            controls.focusIndicator.y = it.y - h/2
                            lifecycleScope.launch {
                                focusIndicatorAnimation.advanceAndBack(3000)
                            }
                        }
                    }
                }
        } catch (e: Throwable) {
            logger.error(e)
        }
    }

    private fun execSelfieAction(x:Float=-1f, y:Float=-1f ) {
        val action = Settings.Camera.selfieAction
        when(action) {
            Settings.Camera.TAP_PHOTO -> {
                takePicture(x, y)
            }
            Settings.Camera.TAP_VIDEO -> {
                viewModel.takeVideoCommand.invoke()
            }
            else -> {}
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if(keyCode==KeyEvent.KEYCODE_VOLUME_UP && event?.action==KeyEvent.ACTION_DOWN) {
            execSelfieAction()
            return true
        }
        return super.onKeyDown(keyCode, event)
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
package io.github.toyota32k.SecureCamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import androidx.activity.viewModels
import androidx.camera.core.Camera
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.view.PreviewView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.bindit.*
import io.github.toyota32k.camera.TcCamera
import io.github.toyota32k.camera.TcCameraManager
import io.github.toyota32k.camera.TcLib
import io.github.toyota32k.camera.gesture.CameraGestureManager
import io.github.toyota32k.camera.gesture.ICameraGestureOwner
import io.github.toyota32k.camera.usecase.ITcUseCase
import io.github.toyota32k.camera.usecase.TcImageCapture
import io.github.toyota32k.camera.usecase.TcVideoCapture
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.bindCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

class CameraActivity : UtMortalActivity(), ICameraGestureOwner {
    override val logger = UtLog("CAMERA")
    class CameraViewModel : ViewModel() {
        val frontCameraSelected = MutableStateFlow(true)
        val showControlPanel = MutableStateFlow(false)
        val fullControlPanel = MutableStateFlow(false)

        val expandPanelCommand = LiteCommand<Boolean> { fullControlPanel.value = it }
        val showPanelCommand = LiteCommand<Boolean> { showControlPanel.value = it }

        @ExperimentalZeroShutterLag // region UseCases
        val imageCapture by lazy { TcImageCapture.Builder().zeroLag().build() }
        val videoCapture by lazy { TcVideoCapture.Builder().useFixedPoolExecutor().build() }

        override fun onCleared() {
            super.onCleared()
            videoCapture.close()
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
            when (videoCapture.recordingState.value) {
                TcVideoCapture.RecordingState.NONE -> videoCapture.takeVideoInFile(newVideoFile())
                TcVideoCapture.RecordingState.STARTED -> videoCapture.pause()
                TcVideoCapture.RecordingState.PAUSING -> videoCapture.resume()
            }
        }
    }

    private val permissionsBroker = UtMultiPermissionsBroker(this)
    private val cameraManager: TcCameraManager by lazy { TcCameraManager.initialize(this) }
    private var currentCamera: TcCamera? = null
    //    private val currentCamera:CameraManager0.CurrentCamera?
//        get() = cameraMamager.currentCamera
    private val binder = Binder()
    private val viewModel by viewModels<CameraViewModel>()
    lateinit var cameraGestureManager: CameraGestureManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        hideActionBar()
        hideStatusBar()

        previewView = findViewById(R.id.previewView)
        previewView?.isClickable = true
        previewView?.isLongClickable = true

        val flipButton:ImageButton = findViewById(R.id.flip_camera_button)
        val closeButton:ImageButton = findViewById(R.id.close_button)
        val expandButton:ImageButton = findViewById(R.id.expand_button)
        val collapseButton:ImageButton = findViewById(R.id.collapse_button)
        val photoButton:ImageButton = findViewById(R.id.photo_button)
        val videoButton:ImageButton = findViewById(R.id.video_button)

        binder
            .owner(this)
            .headlessNonnullBinding(viewModel.frontCameraSelected) { changeCamera(it) }
            .visibilityBinding(findViewById(R.id.control_panel), viewModel.showControlPanel)
            .multiVisibilityBinding(arrayOf(flipButton, closeButton), viewModel.fullControlPanel, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(expandButton, viewModel.fullControlPanel,BoolConvert.Inverse, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(collapseButton, viewModel.fullControlPanel, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .bindCommand(viewModel.expandPanelCommand, expandButton, true)
            .bindCommand(viewModel.expandPanelCommand, collapseButton, false)
            .bindCommand(viewModel.showPanelCommand, closeButton, false)
            .bindCommand(viewModel.takePictureCommand, photoButton)
            .bindCommand(viewModel.takeVideoCommand, videoButton)

        cameraGestureManager = CameraGestureManager(this, true, true) {
            viewModel.showPanelCommand.invoke(true)
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window?.insetsController?.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window?.decorView?.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_IMMERSIVE
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    private fun changeCamera(front:Boolean) {
        val camera = currentCamera ?: return
        if(camera.frontCamera!=front) {
            lifecycleScope.launch { startCamera(front) }
        }
    }

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

            @ExperimentalZeroShutterLag // region UseCases
            currentCamera = cameraManager.CameraBuilder()
                .frontCamera(front)
                .standardPreview(findViewById<PreviewView>(R.id.previewView))
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
    override var previewView: PreviewView? = null
        private set
    override val camera: Camera?
        get() = currentCamera?.camera
}
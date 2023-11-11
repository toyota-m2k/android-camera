package io.github.toyota32k.lib.camera

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.lifecycle.LifecycleOwner
import io.github.toyota32k.lib.camera.usecase.TcImageCapture
import io.github.toyota32k.lib.camera.usecase.TcVideoCapture

class TcCameraManager() {
    companion object {
        // region SINGLETON

        private var sInstance: TcCameraManager? = null
        fun initialize(context:Context): TcCameraManager {
            TcLib.initialize(context)
            return sInstance ?: TcCameraManager().apply { sInstance = this }
        }
        fun dispose() {
            sInstance = null
        }

        val instance: TcCameraManager
            get() = sInstance ?: throw IllegalStateException("UtCameraManager must be initialized before using.")

        // endregion
    }

    private val application: Application = TcLib.applicationContext

    // region Initialization

    private var isReady:Boolean = false
    suspend fun prepare() {
        if(!isReady) {
            prepareCameraProvider()
            prepareCameraExtensions()
        }
    }


    // region CameraProvider

    lateinit var cameraProvider: ProcessCameraProvider
        private set

    private suspend fun prepareCameraProvider() {
        cameraProvider = ProcessCameraProvider.getInstance(application).await()
    }

    // endregion

    // region ExtensionsManager

    lateinit var cameraExtensions: TcCameraExtensions
        private set

    private suspend fun prepareCameraExtensions() {
        cameraExtensions = TcCameraExtensions(application, cameraProvider).prepare()
    }

    // endregion

    // region QualitySelector

    fun supportedQualitySelectorForCamera(cameraInfo:CameraInfo?, dr:DynamicRange=DynamicRange.SDR):List<Quality> {
//        return QualitySelector.getSupportedQualities(cameraInfo ?: return emptyList() )
        return Recorder.getVideoCapabilities(cameraInfo!!).getSupportedQualities(dr)
    }
    fun unionList():List<Quality> {
        val f = supportedQualitySelectorForCamera(getCameraInfo(true))
        val b = supportedQualitySelectorForCamera(getCameraInfo(false))
        return f.union(b).toList()

    }
    fun supportedQualitySelector(isFrontOrBack:Boolean?=null):List<Quality> {
        val supported = when(isFrontOrBack) {
            true-> {supportedQualitySelectorForCamera(getCameraInfo(true)).toSet() }
            false-> {supportedQualitySelectorForCamera(getCameraInfo(false)).toSet() }
            null->{supportedQualitySelectorForCamera(getCameraInfo(false)).intersect(supportedQualitySelectorForCamera(getCameraInfo(true)))}
        }
        return arrayListOf (Quality.UHD, Quality.FHD, Quality.HD, Quality.SD).filter { supported.contains(it) }
    }

    // endregion

    // region CameraSelector (and the camera specific properties)

    fun getCameraSelector(frontCamera: Boolean, extensionMode: TcCameraExtensions.Mode): CameraSelector {
        val baseCameraSelector = if(frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        return cameraExtensions.applyExtensionTo(extensionMode, baseCameraSelector)
    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    fun getCameraInfo(frontCamera: Boolean): CameraInfo? {
        val target = if(frontCamera) CameraMetadata.LENS_FACING_FRONT else CameraMetadata.LENS_FACING_BACK
        return cameraProvider.availableCameraInfos.filter {
            Camera2CameraInfo
                .from(it)
                .getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == target
        }.firstOrNull()
    }


    // endregion

    // region Camera creation

    fun createCamera(lifecycleOwner: LifecycleOwner, frontCamera: Boolean, extensionMode: TcCameraExtensions.Mode = TcCameraExtensions.Mode.NONE, vararg useCases: UseCase): TcCamera {
        if(useCases.isEmpty()) throw java.lang.IllegalArgumentException("no use cases.")

//        val cameraProvider = cameraProvider
        val cameraSelector = getCameraSelector(frontCamera, extensionMode)


        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases
            )
        return TcCamera(camera, frontCamera, cameraSelector)
    }

    /**
     * カメラ-UseCase のバインドを解除する。
     * バインドしたままアクティビティを閉じてしまうと、UseCase側で例外がでた（実害はなさそうだが）ので、
     * Activity#onDestroy で isFinishing の場合に unbindを呼んでみる。
     */
    fun unbind() {
        cameraProvider.unbindAll()
    }

    inner class CameraBuilder {
        private var mFrontCamera:Boolean = false
        private var mExtensionMode: TcCameraExtensions.Mode = TcCameraExtensions.Mode.NONE
        private var mPreview: Preview? = null
        private var mImageCapture: TcImageCapture? = null
        private var mVideoCapture: TcVideoCapture? = null

        fun frontCamera(frontCamera: Boolean): CameraBuilder {
            mFrontCamera = frontCamera
            return this
        }
        fun extensionMode(mode: TcCameraExtensions.Mode): CameraBuilder {
            mExtensionMode = mode
            return this
        }
        fun preview(preview: Preview): CameraBuilder {
            mPreview = preview
            return this
        }
        fun standardPreview(previewView: PreviewView): CameraBuilder {
            return preview(Preview.Builder().build().apply {
                // Connect the preview to receive the surface the camera outputs the frames
                // to. This will allow displaying the camera frames in either a TextureView
                // or SurfaceView. The SurfaceProvider can be obtained from the PreviewView.
                setSurfaceProvider(previewView.surfaceProvider)
            })
        }
        fun imageCapture(imageCapture: TcImageCapture): CameraBuilder {
            mImageCapture = imageCapture
            return this
        }

        fun videoCapture(videoCapture: TcVideoCapture): CameraBuilder {
            mVideoCapture = videoCapture
            return this
        }

        fun build(lifecycleOwner: LifecycleOwner): TcCamera {
            val cs = createCamera(lifecycleOwner, mFrontCamera, mExtensionMode, *(arrayOf(mPreview, mImageCapture?.useCase, mVideoCapture?.useCase).filterNotNull().toTypedArray()))
            return TcCamera(cs.camera, cs.frontCamera, cs.cameraSelector, mPreview,mImageCapture, mVideoCapture)
        }

    }

    // endregion
}
package io.github.toyota32k.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

class CameraManager0(context: Context) {
    private val applicationContext = context.applicationContext

    // region Currently Selected Camera
    class CurrentCamera(val camera:Camera, val frontCamera:Boolean)

    var currentCamera:CurrentCamera? = null
        private set

    //

    private var mCameraProvider:ProcessCameraProvider? = null
    private suspend fun getCameraProvider():ProcessCameraProvider {
        return mCameraProvider ?: ProcessCameraProvider.getInstance(applicationContext).await(applicationContext).also { mCameraProvider = it }
    }
    private var mCameraExtensions:TcCameraExtensions? = null
    private suspend fun getCameraExtensions():TcCameraExtensions {
        return mCameraExtensions ?: TcCameraExtensions(applicationContext, getCameraProvider()).prepare().also { mCameraExtensions = it }
    }

    suspend fun getCapabilitiesOfCamera(frontCamera: Boolean):List<TcCameraExtensions.Mode> {
        return getCameraExtensions().capabilitiesOf(if(frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA)
    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    suspend fun getCameraInfo(frontCamera: Boolean):CameraInfo? {
        val target = if(frontCamera) CameraMetadata.LENS_FACING_FRONT else CameraMetadata.LENS_FACING_BACK
        return getCameraProvider().availableCameraInfos.filter {
            Camera2CameraInfo
                .from(it)
                .getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == target
        }.firstOrNull()
    }

    suspend fun createPreviewCamera(lifecycleOwner:LifecycleOwner, previewView: PreviewView, frontCamera:Boolean=false, extensionMode: TcCameraExtensions.Mode = TcCameraExtensions.Mode.NONE):CurrentCamera {
        val cameraProvider = getCameraProvider()
        val cameraSelector = getCameraSelector(frontCamera, extensionMode)

        cameraProvider.availableCameraInfos

        // Bind image capture and preview use cases with the extension enabled camera
        // selector.
        // val imageCapture = ImageCapture.Builder().build()

        // Returns an instance of the camera bound to the lifecycle
        // Use this camera object to control various operations with the camera
        // Example: flash, zoom, focus metering etc.
        cameraProvider.unbindAll()
        return CurrentCamera(cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
//            imageCapture,
            getStandardPreview(previewView)
        ), frontCamera).also { currentCamera = it }
    }

    fun getStandardPreview(previewView: PreviewView):Preview {
        return Preview.Builder().build().apply {
            // Connect the preview to receive the surface the camera outputs the frames
            // to. This will allow displaying the camera frames in either a TextureView
            // or SurfaceView. The SurfaceProvider can be obtained from the PreviewView.
            setSurfaceProvider(previewView.surfaceProvider)
        }
    }
    @androidx.camera.core.ExperimentalZeroShutterLag
    fun getStandardImageCapture(highSpeed:Boolean):ImageCapture {
        return ImageCapture.Builder().setCaptureMode(if(highSpeed) ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG else ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }

    suspend fun createCamera(lifecycleOwner:LifecycleOwner, frontCamera: Boolean, extensionMode: TcCameraExtensions.Mode= TcCameraExtensions.Mode.NONE, vararg useCases:UseCase):CurrentCamera {
        if(useCases.isEmpty()) throw java.lang.IllegalArgumentException("no use cases.")

        val cameraProvider = getCameraProvider()
        val cameraSelector = getCameraSelector(frontCamera, extensionMode)


        cameraProvider.unbindAll()
        return CurrentCamera(cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            *useCases
        ), frontCamera).also { currentCamera = it }
    }


    private suspend fun getCameraSelector(frontCamera: Boolean, extensionMode: TcCameraExtensions.Mode):CameraSelector {
        val baseCameraSelector = if(frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        return getCameraExtensions().applyExtensionTo(extensionMode, baseCameraSelector)
    }


//    private suspend fun prepareCameraExtensions(cameraProvider: CameraProvider, cameraSelector: CameraSelector): CameraExtensions {
//        return CameraExtensions(applicationContext, cameraProvider, cameraSelector).prepare()
//    }
//    private suspend fun getCameraSelector(baseCameraSelector:CameraSelector, extensionMode: CameraExtensions.Mode):CameraSelector {
//        return if(extensionMode== CameraExtensions.Mode.NONE) {
//            baseCameraSelector
//        } else {
//            prepareCameraExtensions(getCameraProvider(), baseCameraSelector).applyExtension(extensionMode)
//        }
//    }
}
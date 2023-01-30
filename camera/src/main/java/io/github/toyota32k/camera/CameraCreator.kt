package io.github.toyota32k.camera

import android.content.Context
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

class CameraCreator(context: Context) {
    lateinit var camera:Camera
        private set
    private val applicationContext = context.applicationContext

    private var cameraProvider_:ProcessCameraProvider? = null
    private suspend fun prepareCameraProvider():ProcessCameraProvider {
        return cameraProvider_ ?: ProcessCameraProvider.getInstance(applicationContext).prepare(applicationContext).also { cameraProvider_ = it }
    }

    private var cameraExtensions_: CameraExtensions? = null
    private suspend fun prepareCameraExtensions(cameraProvider: CameraProvider, cameraSelector: CameraSelector): CameraExtensions {
        return cameraExtensions_ ?: CameraExtensions(applicationContext, cameraProvider, cameraSelector).prepare().also { cameraExtensions_ = it }
    }
    private suspend fun getCameraSelector(baseCameraSelector:CameraSelector, extensionMode: CameraExtensions.Mode):CameraSelector {
        return if(extensionMode== CameraExtensions.Mode.NONE) {
            baseCameraSelector
        } else {
            prepareCameraExtensions(prepareCameraProvider(), baseCameraSelector).applyExtension(extensionMode)
        }
    }

    suspend fun getCapabilities(frontCamera: Boolean=false):List<CameraExtensions.Mode> {
        return prepareCameraExtensions(prepareCameraProvider(), if(frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA).capabilities
    }

    suspend fun createCamera(lifecycleOwner:LifecycleOwner, previewView: PreviewView, frontCamera:Boolean=false, extensionMode: CameraExtensions.Mode = CameraExtensions.Mode.NONE):Camera {
        val cameraProvider = prepareCameraProvider()
        val baseCameraSelector = if(frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val cameraSelector = getCameraSelector(baseCameraSelector, extensionMode)

        // Bind image capture and preview use cases with the extension enabled camera
        // selector.
        val imageCapture = Builder().build()
        val preview = Preview.Builder().build()
        // Connect the preview to receive the surface the camera outputs the frames
        // to. This will allow displaying the camera frames in either a TextureView
        // or SurfaceView. The SurfaceProvider can be obtained from the PreviewView.
        preview.setSurfaceProvider(previewView.surfaceProvider)

        // Returns an instance of the camera bound to the lifecycle
        // Use this camera object to control various operations with the camera
        // Example: flash, zoom, focus metering etc.
        cameraProvider.unbindAll()
        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            imageCapture,
            preview
        )
        return camera
    }
}
package io.github.toyota32k.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

class UtCameraManager(context: Context) {
    companion object {
        // region SINGLETON

        private var sInstance:UtCameraManager? = null
        fun initialize(context:Context) {
            if(sInstance==null) {
                sInstance = UtCameraManager(context)
            }
        }
        fun dispose() {
            sInstance = null
        }

        val instance:UtCameraManager
            get() = sInstance ?: throw IllegalStateException("UtCameraManager must be initialized before using.")

        // endregion
    }

    val applicationContext: Context = context.applicationContext

    // region CameraProvider

    private var mCameraProvider: ProcessCameraProvider? = null
    suspend fun getCameraProvider(): ProcessCameraProvider {
        return mCameraProvider ?: ProcessCameraProvider.getInstance(applicationContext).await(applicationContext).also { mCameraProvider = it }
    }

    // endregion

    // region ExtensionsManager

    private var mCameraExtensions:UtCameraExtensions? = null
    suspend fun getCameraExtensions():UtCameraExtensions {
        return mCameraExtensions ?: UtCameraExtensions(applicationContext, getCameraProvider()).prepare().also { mCameraExtensions = it }
    }

    // endregion

    // region CameraSelector (and the camera specific properties)

    suspend fun getCameraSelector(frontCamera: Boolean, extensionMode: UtCameraExtensions.Mode): CameraSelector {
        val baseCameraSelector = if(frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        return getCameraExtensions().applyExtensionTo(extensionMode, baseCameraSelector)
    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    suspend fun getCameraInfo(frontCamera: Boolean): CameraInfo? {
        val target = if(frontCamera) CameraMetadata.LENS_FACING_FRONT else CameraMetadata.LENS_FACING_BACK
        return getCameraProvider().availableCameraInfos.filter {
            Camera2CameraInfo
                .from(it)
                .getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == target
        }.firstOrNull()
    }

    // endregion

    // region Camera creation

    suspend fun createCamera(lifecycleOwner: LifecycleOwner, frontCamera: Boolean, extensionMode: UtCameraExtensions.Mode= UtCameraExtensions.Mode.NONE, vararg useCases: UseCase): UtCameraInfo {
        if(useCases.isEmpty()) throw java.lang.IllegalArgumentException("no use cases.")

        val cameraProvider = getCameraProvider()
        val cameraSelector = getCameraSelector(frontCamera, extensionMode)


        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases
            )
        return UtCameraInfo(camera, frontCamera, cameraSelector)
    }

    class Builder {
        private var mFrontCamera:Boolean = false
        private var mExtensionMode:UtCameraExtensions.Mode = UtCameraExtensions.Mode.NONE
        private var mPreview:Preview? = null
        private var mImageCapture:ImageCapture? = null
//        private var mVideoCapture: VideoCapture

        fun frontCamera(frontCamera: Boolean):Builder {
            mFrontCamera = frontCamera
            return this
        }
        fun extensionMode(mode:UtCameraExtensions.Mode):Builder {
            mExtensionMode = mode
            return this
        }
        fun preview(preview: Preview):Builder {
            mPreview = preview
            return this
        }
        fun standardPreview(previewView: PreviewView):Builder {
            return preview(Preview.Builder().build().apply {
                // Connect the preview to receive the surface the camera outputs the frames
                // to. This will allow displaying the camera frames in either a TextureView
                // or SurfaceView. The SurfaceProvider can be obtained from the PreviewView.
                setSurfaceProvider(previewView.surfaceProvider)
            })
        }
        fun imageCapture(imageCapture: ImageCapture):Builder {
            mImageCapture = imageCapture
            return this
        }
        @androidx.camera.core.ExperimentalZeroShutterLag
        fun standardImageCapture(highSpeed:Boolean):Builder {
            return imageCapture(ImageCapture.Builder().setCaptureMode(if(highSpeed) ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG else ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build())
        }


    }

    // endregion
}
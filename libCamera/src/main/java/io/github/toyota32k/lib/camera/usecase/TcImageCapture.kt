package io.github.toyota32k.lib.camera.usecase

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CaptureMode
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.core.content.ContextCompat
import io.github.toyota32k.lib.camera.TcAspect
import io.github.toyota32k.lib.camera.TcImageQualityHint
import io.github.toyota32k.lib.camera.TcImageResolution
import io.github.toyota32k.lib.camera.TcLib
import io.github.toyota32k.lib.camera.utils.ImageUtils
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ImageCaptureCallback(private val continuation: Continuation<Bitmap>): OnImageCapturedCallback() {
    override fun onCaptureSuccess(imageProxy: ImageProxy) {
        imageProxy.use {
            val bitmap = ImageUtils.imageToBitmap(imageProxy, 0f)
            if (bitmap != null) {
                continuation.resume(bitmap)
            } else {
                continuation.resumeWithException(IllegalStateException("cannot convert image to bitmap."))
            }
        }
    }

    override fun onError(exception: ImageCaptureException) {
        TcLib.logger.error(exception)
        continuation.resumeWithException(exception)
    }
}

/**
 * カメラからキャプチャしてBitmapとして取得
 * @return 取得したBitmap
 */
suspend fun ImageCapture.take(): Bitmap {
    return suspendCoroutine<Bitmap> {cont->
        takePicture(ContextCompat.getMainExecutor(TcLib.applicationContext), ImageCaptureCallback(cont))
    }
}


class ImageSavedCallback(private val continuation: Continuation<Uri>) : ImageCapture.OnImageSavedCallback {
    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
        val uri = outputFileResults.savedUri
        if(uri!=null) {
            continuation.resume(uri)
        } else {
            continuation.resumeWithException(IllegalStateException("no uri"))
        }
    }

    override fun onError(exception: ImageCaptureException) {
        TcLib.logger.error(exception)
        continuation.resumeWithException(exception)
    }
}

/**
 * カメラからキャプチャして MediaStore.VOLUME_EXTERNAL_PRIMARY にJPEGファイルとして保存
 * @param displayName   ファイル名 (emptyなら、img-撮影日時.jpeg
 * @return 保存したファイルのuri
 */
@RequiresApi(Build.VERSION_CODES.Q)
suspend fun ImageCapture.takeInMediaStore(displayName:String): Uri {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
    }
    val option = ImageCapture.OutputFileOptions.Builder(
        TcLib.applicationContext.contentResolver,
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        contentValues).build()

    return suspendCoroutine<Uri> {cont->
        takePicture(option, ContextCompat.getMainExecutor(TcLib.applicationContext), ImageSavedCallback(cont))
    }
}

class TcImageCapture(val imageCapture: ImageCapture) : ITcStillCamera {
    companion object {
        val builder: IBuilder get() = Builder()
        fun isHdrJpegSupported(cameraInfo:CameraInfo):Boolean {
            return ImageCapture.getImageCaptureCapabilities(cameraInfo).supportedOutputFormats.contains(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
        }
    }

//    @ExperimentalZeroShutterLag // region UseCases
//    constructor(highSpeed:Boolean) : this(ImageCapture.Builder().setCaptureMode(if(highSpeed) ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG else ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build())
    interface IBuilder {
        fun zeroLag(): IBuilder
        fun minimizeLatency(): IBuilder
        fun maximizeQuality(): IBuilder
        fun qualityHint(hint: TcImageQualityHint?): IBuilder
        fun preferAspectRatio(aspect: TcAspect) : IBuilder
        fun dynamicRange(range: DynamicRange) : IBuilder
        fun outputHdrJpeg(hdr:Boolean):IBuilder
        fun jpegQuality(q:Int):IBuilder
        fun resolution(strategy: ResolutionStrategy):IBuilder
        fun resolution(resolution: TcImageResolution):IBuilder
        fun build(): TcImageCapture
    }

    private class Builder : IBuilder {
        @CaptureMode
        private var mMode: Int? = null
        private var mDynamicRange: DynamicRange = DynamicRange.SDR
        private var mOutputHdrJpeg:Boolean = false
        private var mAspect: TcAspect = TcAspect.Default
        private var mQualityHint: TcImageQualityHint? = null
        private var mJpegQuality:Int = 0     // <= 0 :default
        private var mResolutionStrategy: ResolutionStrategy? = null

        @ExperimentalZeroShutterLag // region UseCases
        override fun zeroLag() = apply {
            mMode = ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
        }
        override fun minimizeLatency() = apply {
            mMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
        override fun maximizeQuality() = apply {
            mMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
        override fun qualityHint(hint: TcImageQualityHint?) = apply {
            mQualityHint = hint
        }
        override fun preferAspectRatio(aspect: TcAspect) = apply {
            mAspect = aspect
        }
        override fun dynamicRange(range: DynamicRange) = apply {
            mDynamicRange = range
        }
        override fun outputHdrJpeg(hdr: Boolean) =  apply {
            mOutputHdrJpeg = hdr
        }

        override fun jpegQuality(q: Int): IBuilder = apply {
            mJpegQuality = q
        }

        override fun resolution(strategy: ResolutionStrategy) = apply {
            mResolutionStrategy = strategy
        }

        override fun resolution(resolution: TcImageResolution) = apply {
            mResolutionStrategy = resolution.toResolutionStrategy()
        }

        override fun build(): TcImageCapture {
            val mode = mMode ?: if(mQualityHint == TcImageQualityHint.PreferQuality) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            val resolutionSelector = if (mQualityHint != null || mAspect.aspectStrategy != null) {
                val builder = ResolutionSelector.Builder()
                mQualityHint?.let { hint->
                    builder.setAllowedResolutionMode(hint.value)
                }
                mAspect.aspectStrategy?.let { strategy->
                    builder.setAspectRatioStrategy(strategy)
                }
                mResolutionStrategy?.let { strategy->
                    builder.setResolutionStrategy(strategy)
                }
                builder.build()
            } else {
                null
            }
            return TcImageCapture(
                ImageCapture.Builder()
                    .setCaptureMode(mode)
                    .apply {
                        if (mDynamicRange != DynamicRange.SDR) {
                            @SuppressLint("RestrictedApi")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                setDynamicRange(mDynamicRange)
                            } else {
                                TcLib.logger.warn("Dynamic range setting is not supported on this OS version.")
                            }
                        }
                        if (mOutputHdrJpeg) {
                            setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
                        }
                        if (mJpegQuality in 1..100) {
                            setJpegQuality(mJpegQuality)
                        }
                        if (resolutionSelector!=null) {
                            setResolutionSelector(resolutionSelector)
                        }
                    }
                    .build())
        }
    }

    override val useCase: UseCase
        get() = imageCapture

    override suspend fun takePicture(): Bitmap? {
        TcLib.logger.chronos {
            return try {
                imageCapture.take()
            } catch (e: Throwable) {
                TcLib.logger.error(e)
                null
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    override suspend fun takePictureInMediaStore(fileName: String): Uri? {
        return try {
            imageCapture.takeInMediaStore(fileName.ifBlank { ITcUseCase.defaultFileName("img-", ".jpeg") })
        } catch (e:Throwable) {
            TcLib.logger.error(e)
            null
        }
    }
}

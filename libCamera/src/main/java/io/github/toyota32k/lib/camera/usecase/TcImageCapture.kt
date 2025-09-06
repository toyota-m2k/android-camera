package io.github.toyota32k.lib.camera.usecase

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CaptureMode
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.core.content.ContextCompat
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
        @OptIn(ExperimentalImageCaptureOutputFormat::class)
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
        fun dynamicRange(range: DynamicRange) : IBuilder
        fun outputHdrJpeg(hdr:Boolean):IBuilder
        fun build(): TcImageCapture
    }
    private class Builder : IBuilder {
        @CaptureMode
        private var mMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        private var mDynamicRange: DynamicRange = DynamicRange.SDR
        private var mOutputHdrJpeg:Boolean = false

        @ExperimentalZeroShutterLag // region UseCases
        override fun zeroLag(): IBuilder {
            mMode = ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
            return this
        }
        override fun minimizeLatency(): IBuilder {
            mMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            return this
        }
        override fun maximizeQuality(): IBuilder {
            mMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            return this
        }
        override fun dynamicRange(range: DynamicRange): IBuilder {
            mDynamicRange = range
            return this
        }
        override fun outputHdrJpeg(hdr: Boolean): IBuilder {
            mOutputHdrJpeg = hdr
            return this
        }

        @OptIn(ExperimentalImageCaptureOutputFormat::class)
        override fun build(): TcImageCapture {
            return TcImageCapture(
                ImageCapture.Builder()
                    .setCaptureMode(mMode)
                    .apply {
                        if (mDynamicRange != DynamicRange.SDR) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                @SuppressLint("RestrictedApi")
                                setDynamicRange(mDynamicRange)
                            } else {
                                TcLib.logger.warn("Dynamic range setting is not supported on this OS version.")
                            }
                        }
                        if (mOutputHdrJpeg) {
                            setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
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

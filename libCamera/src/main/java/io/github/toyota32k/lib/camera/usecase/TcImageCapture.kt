package io.github.toyota32k.lib.camera.usecase

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CaptureMode
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.core.content.ContextCompat
import io.github.toyota32k.lib.camera.TcLib
import io.github.toyota32k.lib.camera.utils.ImageUtils
import java.util.*
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
    @ExperimentalZeroShutterLag // region UseCases
    constructor(highSpeed:Boolean) : this(ImageCapture.Builder().setCaptureMode(if(highSpeed) ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG else ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build())

    class Builder {
        @CaptureMode
        private var mode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        @ExperimentalZeroShutterLag // region UseCases
        fun zeroLag(): Builder {
            mode = ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
            return this
        }
        fun minimizeLatency(): Builder {
            mode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            return this
        }
        fun maximizeQuality(): Builder {
            mode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            return this
        }
        fun build(): TcImageCapture {
            return TcImageCapture(ImageCapture.Builder().setCaptureMode(mode).build())
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

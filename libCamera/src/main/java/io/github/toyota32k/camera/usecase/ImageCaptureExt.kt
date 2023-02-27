package io.github.toyota32k.camera.usecase

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import io.github.toyota32k.camera.CameraLib
import io.github.toyota32k.camera.utils.ImageUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ImageCaptureCallback(private val continuation: Continuation<Bitmap>): OnImageCapturedCallback() {
    override fun onCaptureSuccess(imageProxy: ImageProxy) {
        val bitmap = ImageUtils.imageToBitmap(imageProxy, 0f)
        if(bitmap!=null) {
            continuation.resume(bitmap)
        } else {
            continuation.resumeWithException(IllegalStateException("cannot convert image to bitmap."))
        }
    }

    override fun onError(exception: ImageCaptureException) {
        CameraLib.logger.error(exception)
        continuation.resumeWithException(exception)
    }
}

suspend fun ImageCapture.take(): Bitmap {
    return suspendCoroutine<Bitmap> {cont->
        takePicture(ContextCompat.getMainExecutor(CameraLib.applicationContext), ImageCaptureCallback(cont))
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
        CameraLib.logger.error(exception)
        continuation.resumeWithException(exception)
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
suspend fun ImageCapture.takeInFile(displayName:String): Uri {
    val name = displayName.ifEmpty { "img-${ SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())}" }
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
    }
    val option = ImageCapture.OutputFileOptions.Builder(
        CameraLib.applicationContext.contentResolver,
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        contentValues).build()

    return suspendCoroutine<Uri> {cont->
        takePicture(option, ContextCompat.getMainExecutor(CameraLib.applicationContext), ImageSavedCallback(cont))
    }
}

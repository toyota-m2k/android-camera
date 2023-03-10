package io.github.toyota32k.camera.lib.usecase

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.UseCase
import androidx.camera.video.OutputOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

interface ITcUseCase {
    val useCase: UseCase

    companion object {
        fun defaultFileName(prefix: String, extension: String): String {
            return "$prefix${
                SimpleDateFormat(
                    "yyyyMMddHHmmss",
                    Locale.US
                ).format(Date())
            }$extension"
        }
    }
}

interface ITcStillCamera : ITcUseCase {
    suspend fun takePicture():Bitmap?
    suspend fun takePictureInMediaStore(fileName:String=""): Uri?
}

interface ITcVideoCamera : ITcUseCase {
    fun takeVideo(options: OutputOptions)
    fun takeVideoWithoutAudio(options:OutputOptions)
    fun takeVideoInMediaStore(fileName:String)
    fun takeVideoWithoutAudioInMediaStore(fileName:String)
    fun takeVideoInFile(file: File)
    fun takeVideoWithoutAudioInFile(file:File)
    fun takeVideoInFile(uri: Uri)
    fun takeVideoWithoutAudioInFile(uri:Uri)
    fun pause()
    fun resume()
    fun stop()
    fun close()
}
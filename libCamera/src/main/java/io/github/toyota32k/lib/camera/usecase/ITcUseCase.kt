package io.github.toyota32k.lib.camera.usecase

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
        val dateFormatForFilename = SimpleDateFormat("yyyy.MM.dd-HH:mm:ss",Locale.US)
        fun defaultFileName(prefix: String, extension: String, date:Date?=null): String {
            return "$prefix${dateFormatForFilename.format(date?:Date())}$extension"
        }
    }
}

interface ITcStillCamera : ITcUseCase {
    suspend fun takePicture():Bitmap?
    suspend fun takePictureInMediaStore(fileName:String=""): Uri?
}

interface ITcVideoCamera : ITcUseCase {
    fun takeVideo(options: OutputOptions, onFinalized:(()->Unit)?=null)
    fun takeVideoWithoutAudio(options:OutputOptions, onFinalized:(()->Unit)?=null)
    fun takeVideoInMediaStore(fileName:String, onFinalized:(()->Unit)?=null)
    fun takeVideoWithoutAudioInMediaStore(fileName:String, onFinalized:(()->Unit)?=null)
    fun takeVideoInFile(file: File, onFinalized:(()->Unit)?=null)
    fun takeVideoWithoutAudioInFile(file:File, onFinalized:(()->Unit)?=null)
    fun takeVideoInFile(uri: Uri, onFinalized:(()->Unit)?=null)
    fun takeVideoWithoutAudioInFile(uri:Uri, onFinalized:(()->Unit)?=null)
    fun pause()
    fun resume()
    fun stop()
    fun dispose()
}
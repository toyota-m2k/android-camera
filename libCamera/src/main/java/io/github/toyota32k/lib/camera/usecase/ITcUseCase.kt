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
    fun dispose()
}
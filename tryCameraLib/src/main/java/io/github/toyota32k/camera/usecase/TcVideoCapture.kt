package io.github.toyota32k.camera.usecase

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.RequiresPermission
import androidx.camera.core.UseCase
import androidx.camera.video.*
import androidx.camera.video.OutputOptions.FILE_SIZE_UNLIMITED
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import io.github.toyota32k.camera.TcLib
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

//fun VideoCapture<Recorder>.record(context: Context) {
//    val option = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).build()
//    output.prepareRecording(context, option)
//}

class TcVideoCapture(val videoCapture: VideoCapture<Recorder>) : Consumer<VideoRecordEvent>, ITcVideoCamera, IUtPropOwner {
    companion object {
        val logger: UtLog = TcLib.logger
    }
    private val context: Context
        get() = TcLib.applicationContext
    private val contentResolver: ContentResolver
        get() = context.contentResolver

    override val useCase: UseCase
        get() = videoCapture

    // region Status / Properties

    var recording:Recording? = null
        private set(v) {
            field = v
            recordingState.mutable.value = RecordingState.STARTED
        }

    enum class RecordingState {
        NONE,
        STARTED,
        PAUSING,
    }
    val recordingState: StateFlow<RecordingState> = MutableStateFlow(RecordingState.NONE)

    // endregion

    // region Construction

    class Builder {
        val autoQualitySelector:QualitySelector
            get() = QualitySelector.fromOrderedList(
                listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))

        private var mQualitySelector: QualitySelector? = null
        private var mExecutor: Executor? = null
        fun qualitySelector(qualitySelector: QualitySelector):Builder {
            mQualitySelector = qualitySelector
            return this
        }
        fun executor(executor: Executor):Builder {
            mExecutor = executor
            return this
        }
        fun useFixedPoolExecutor():Builder {
            mExecutor = Executors.newFixedThreadPool(2)
            return this
        }
        fun build():TcVideoCapture {
            val recorder = Recorder.Builder()
                .apply { mExecutor?.apply { setExecutor(this) } }
                .setQualitySelector( mQualitySelector?: autoQualitySelector )
                .build()
            return TcVideoCapture(VideoCapture.withOutput(recorder))
        }
    }

    // endregion

    // region Consumer<VideoRecordEvent>

    override fun accept(t: VideoRecordEvent?) {
        logger.debug("$t")
    }

    // endregion

    // region Start Recording

    /**
     * VideoOutput#prepareRecording には３つのオーバーロードがあって、OutputOptions の型によって呼び分けないといけない。
     */
    private fun Recorder.prepareRecording(options:OutputOptions):PendingRecording {
        return when(options) {
            is MediaStoreOutputOptions -> prepareRecording(context, options)
            is FileOutputOptions -> prepareRecording(context, options)
            is FileDescriptorOutputOptions -> prepareRecording(context, options)
            else -> throw java.lang.IllegalArgumentException("unsupported type: ${options::class.java.name}")
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun takeVideo(options:OutputOptions) {
        close()
        videoCapture
            .output
            .prepareRecording(options)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context), this)
            .apply { recording = this }
    }
    override fun takeVideoWithoutAudio(options:OutputOptions) {
        close()
        videoCapture
            .output
            .prepareRecording(options)
            .start(ContextCompat.getMainExecutor(context), this)
            .apply { recording = this }
    }

    /**
     * MediaStoreOutputOptions を作成
     *
     */
    private fun createMediaStoreOutputOptions(fileName: String, fileSizeLimit:Long=FILE_SIZE_UNLIMITED.toLong()):MediaStoreOutputOptions {
        val name = fileName.ifBlank { ITcUseCase.defaultFileName("mov-", ".mp4") }
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
        }
        return MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .setFileSizeLimit(fileSizeLimit)
            .build()
    }

    /**
     * 動画を撮影してメディアストア内に保存する
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun takeVideoInMediaStore(fileName:String) {
        takeVideo(createMediaStoreOutputOptions(fileName))
    }

    /**
     * 動画を撮影してメディアストア内に保存する
     */
    override fun takeVideoWithoutAudioInMediaStore(fileName:String) {
        takeVideoWithoutAudio(createMediaStoreOutputOptions(fileName))
    }

    private fun createFileOutputOptions(file:File, fileSizeLimit:Long=FILE_SIZE_UNLIMITED.toLong()):FileOutputOptions {
        return FileOutputOptions.Builder(file)
            .setFileSizeLimit(fileSizeLimit)
            .build()
    }

    /**
     * 動画を撮影してFileに保存する。
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun takeVideoInFile(file: File) {
        takeVideo(createFileOutputOptions(file))
    }
    /**
     * 動画を撮影してFileに保存する。
     */
    override fun takeVideoWithoutAudioInFile(file:File) {
        takeVideoWithoutAudio(createFileOutputOptions(file))
    }

    private fun crateFileDescriptionOutputOptions(uri:Uri, fileSizeLimit:Long=FILE_SIZE_UNLIMITED.toLong()):FileDescriptorOutputOptions {
        return FileDescriptorOutputOptions.Builder(contentResolver.openFileDescriptor(uri, "rwt")?:throw java.lang.IllegalStateException("cannot open uri"))
            .setFileSizeLimit(fileSizeLimit)
            .build()
    }

    /**
     * 動画を撮影してUriに保存する。
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun takeVideoInFile(uri: Uri) {
        takeVideo(crateFileDescriptionOutputOptions(uri))
    }
    /**
     * 動画を撮影してUriに保存する。
     */
    override fun takeVideoWithoutAudioInFile(uri:Uri) {
        takeVideoWithoutAudio(crateFileDescriptionOutputOptions(uri))
    }

    // endregion

    // region Controlling "Recording"

    override fun pause() {
        recording?.pause() ?: throw java.lang.IllegalStateException("not recording")
        recordingState.mutable.value = RecordingState.PAUSING
    }

    override fun resume() {
        recording?.resume() ?: throw java.lang.IllegalStateException("not recording")
        recordingState.mutable.value = RecordingState.STARTED
    }

    override fun stop() {
        recording?.stop() ?: throw java.lang.IllegalStateException("not recording")
        close()
    }

    override fun close() {
        recording?.close()
        recording = null
        recordingState.mutable.value = RecordingState.NONE
    }

    // endregion

}
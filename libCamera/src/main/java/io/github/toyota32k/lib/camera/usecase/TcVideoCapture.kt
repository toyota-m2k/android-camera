package io.github.toyota32k.lib.camera.usecase

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
import io.github.toyota32k.lib.camera.TcCameraManipulator
import io.github.toyota32k.lib.camera.TcLib
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

//fun VideoCapture<Recorder>.record(context: Context) {
//    val option = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).build()
//    output.prepareRecording(context, option)
//}

/**
 * TcVideoCaptureクラス
 * androidx.camera.video.VideoCapture<Recorder> の構築、利用（イベントハンドリング、状態管理、動画の撮影）を簡素化するためのクラス
 * // レコーディング状態（RecordingState）監視用フローを準備（オプショナル）
 * val flow = MutableStateFlow<TcVideoCapture.RecordingState>(TcVideoCapture.RecordingState.NONE)
 *
 * // TcVideoCaptureを構築
 * val capture = TcVideoCapture.create { builder->
 *                  builder
 *                  .recordingStateFlow(flow)
 *                  .build()
 * // カメラに接続（カメラインスタンスを構築）... TcCameraManagerを使う例は、TcCameraManager.ktを参照
 * val cameraProvider = ProcessCameraProvider.getInstance(application).await()
 * val camera = cameraProvider.bindToLifecycle(
 *     lifecycleOwner,
 *     cameraSelector,
 *     capture
 *     )
 * // レコーディング開始
 * capture.takeVideoInMediaStore("my-video") {
 *    UtMessageBox.showMessage("recoded.")
 * }
 *
 */
class TcVideoCapture(val videoCapture: VideoCapture<Recorder>, private var recordingState:MutableStateFlow<RecordingState>?) : Consumer<VideoRecordEvent>,
    ITcVideoCamera, IUtPropOwner {
    companion object {
        val logger: UtLog = TcLib.logger
        val builder:Builder get() = Builder()

        fun create(setupMe:(builder:IBuilder)->Unit):TcVideoCapture {
            return Builder().apply {
                setupMe(this)
            }.build()
        }
        fun default():TcVideoCapture {
            return Builder().build()
        }
    }
    private val context: Context
        get() = TcLib.applicationContext
    private val contentResolver: ContentResolver
        get() = context.contentResolver

    override val useCase: UseCase
        get() = videoCapture

    // region Status / Properties

    var recording:Recording? = null

    enum class RecordingState {
        NONE,
        STARTED,
        PAUSING,
    }

//    val recordingState: StateFlow<RecordingState> = recordingState ?: MutableStateFlow(
//        RecordingState.NONE
//    )

    // endregion

    // region Construction
    interface IBuilder {
        fun qualitySelector(qualitySelector: QualitySelector): IBuilder
        fun executor(executor: Executor): IBuilder
        fun useFixedPoolExecutor(): IBuilder
        fun recordingStateFlow(flow:MutableStateFlow<RecordingState>): IBuilder
    }

    class Builder : IBuilder {
        private val autoQualitySelector:QualitySelector
            get() = QualitySelector.fromOrderedList(
                listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))

        private var mQualitySelector: QualitySelector? = null
        private var mExecutor: Executor? = null
        private var mRecordingState:MutableStateFlow<RecordingState>? = null

        override fun qualitySelector(qualitySelector: QualitySelector): Builder {
            mQualitySelector = qualitySelector
            return this
        }
        override fun executor(executor: Executor): Builder {
            mExecutor = executor
            return this
        }
        override fun useFixedPoolExecutor(): Builder {
            mExecutor = Executors.newFixedThreadPool(2)
            return this
        }

        override fun recordingStateFlow(flow:MutableStateFlow<RecordingState>): Builder {
            mRecordingState = flow
            return this
        }

        fun build(): TcVideoCapture {
            val executor = mExecutor ?: Executors.newFixedThreadPool(2)
            val recorder = Recorder.Builder()
                .setExecutor(executor)
                .setQualitySelector( mQualitySelector?: autoQualitySelector )
                .build()
            return TcVideoCapture(VideoCapture.withOutput(recorder), mRecordingState)
        }
    }

    // endregion

    // region Consumer<VideoRecordEvent>
    override fun accept(value: VideoRecordEvent) {
        val state = recordingState ?: return
        when(value) {
            is VideoRecordEvent.Start -> state.value = RecordingState.STARTED
            is VideoRecordEvent.Pause -> state.value = RecordingState.PAUSING
            is VideoRecordEvent.Resume -> state.value = RecordingState.STARTED
            is VideoRecordEvent.Finalize -> state.value = RecordingState.NONE
            else -> return
        }
        logger.debug("$value")
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

    private var onFinalized: (()->Unit)? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun takeVideo(options:OutputOptions, onFinalized:(()->Unit)?) {
        stop()
        this.onFinalized = onFinalized
        videoCapture
            .output
            .prepareRecording(options)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context), this)
            .apply { recording = this }
    }
    override fun takeVideoWithoutAudio(options:OutputOptions, onFinalized:(()->Unit)?) {
        stop()
        this.onFinalized = onFinalized
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
    override fun takeVideoInMediaStore(fileName:String, onFinalized:(()->Unit)?) {
        takeVideo(createMediaStoreOutputOptions(fileName), onFinalized)
    }

    /**
     * 動画を撮影してメディアストア内に保存する
     */
    override fun takeVideoWithoutAudioInMediaStore(fileName:String, onFinalized:(()->Unit)?) {
        takeVideoWithoutAudio(createMediaStoreOutputOptions(fileName), onFinalized)
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
    override fun takeVideoInFile(file: File, onFinalized:(()->Unit)?) {
        takeVideo(createFileOutputOptions(file), onFinalized)
    }
    /**
     * 動画を撮影してFileに保存する。
     */
    override fun takeVideoWithoutAudioInFile(file:File, onFinalized:(()->Unit)?) {
        takeVideoWithoutAudio(createFileOutputOptions(file), onFinalized)
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
    override fun takeVideoInFile(uri: Uri, onFinalized:(()->Unit)?) {
        takeVideo(crateFileDescriptionOutputOptions(uri),onFinalized)
    }
    /**
     * 動画を撮影してUriに保存する。
     */
    override fun takeVideoWithoutAudioInFile(uri:Uri, onFinalized:(()->Unit)?) {
        takeVideoWithoutAudio(crateFileDescriptionOutputOptions(uri),onFinalized)
    }

    // endregion

    // region Controlling "Recording"

    override fun pause() {
        recording?.pause() ?: throw java.lang.IllegalStateException("not recording")
    }

    override fun resume() {
        recording?.resume() ?: throw java.lang.IllegalStateException("not recording")
    }

    override fun stop() {
        recording?.stop() // ?: throw java.lang.IllegalStateException("not recording")
        recording = null
        onFinalized?.invoke()
        onFinalized = null
    }

//    override fun close() {
//        recording?.close()
//        recording = null
//    }

    override fun dispose() {
        val state = recordingState
        recordingState = null
        stop()
        state?.value = RecordingState.NONE
    }

    // endregion

}
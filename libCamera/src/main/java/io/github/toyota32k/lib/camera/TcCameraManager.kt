package io.github.toyota32k.lib.camera

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.lifecycle.LifecycleOwner
import io.github.toyota32k.lib.camera.TcFacing.Companion.cameraSelector
import io.github.toyota32k.lib.camera.usecase.TcImageCapture
import io.github.toyota32k.lib.camera.usecase.TcVideoCapture
import io.github.toyota32k.lib.camera.usecase.TcVideoCapture.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executor

/**
 * カメラの統括マネージャー
 * 主な目的は、Preview, VideoCapture, ImageCapture などの UseCase にバインドされたカメラインスタンス(TcCamera)を作成することである。
 * また、カメラの動作設定に必要な情報へのアクセス手段も提供
 * - カメラがサポートする Quality のリスト
 * - カメラがサポートする拡張モード(ExtensionMode)
 *
 * 使用例
 *
 * // TcCameraManagerの準備
 * val manager = TcCameraManager.initialize(application).prepare()
 *
 * // Video撮影用UseCase
 * val videoCapture = TcVideoCapture.default()
 *
 * // カメラを作成して、UseCaseにバインドする
 * val camera:TcCamera = manager.createCamera(activity) { builder->
 *  builder
 *  .frontCamera(false) // リアカメラ
 *  .standardPreview(previewView)   // PreviewViewでプレビューする
 *  .videoCapture(videoCapture) // ビデオ録画用UseCase
 * }
 *
 * // レコーディング開始(TcVideoCaptureを使用）
 * videoCapture.takeVideoInMediaStore("my-video") {
 *    UtMessageBox.showMessage("recoded.")
 * }
 *
 */
class TcCameraManager() {
    companion object {
        // region SINGLETON

        private var sInstance: TcCameraManager? = null

        /**
         * TcCameraManagerのシングルトンインスタンスを作成
         * この時点では、インスタンスを作っただけなので、cameraProvider, cameraExtensionsなどを使う前には prepare()を呼ぶ必要がある。
         *
         * @param context: ApplicationContext可
         * @return TcCameraManagerインスタンス
         */
        fun initialize(context:Context): TcCameraManager {
            TcLib.initialize(context)
            return sInstance ?: TcCameraManager().apply { sInstance = this }
        }
        fun dispose() {
            sInstance = null
        }

        val instance: TcCameraManager
            get() = sInstance ?: throw IllegalStateException("UtCameraManager must be initialized before using.")

        // endregion
    }

    // region Initialization

    private val application: Application = TcLib.applicationContext

    private var isReady:Boolean = false

    /**
     * TcCameraManagerの初期化
     * suspend関数を使うので initialize()と２段階にしている。
     * - CameraProviderの準備
     * - CameraExtensionsの準備
     */
    suspend fun prepare():TcCameraManager {
        if(!isReady) {
            isReady = true
            prepareCameraProvider()
            prepareCameraExtensions()
        }
        return this
    }

    // endregion

    // region CameraProvider

    lateinit var cameraProvider: ProcessCameraProvider
        private set

    private suspend fun prepareCameraProvider() {
        cameraProvider = ProcessCameraProvider.getInstance(application).await()
    }

    // endregion

    // region ExtensionsManager

    lateinit var cameraExtensions: TcCameraExtensions
        private set

    private suspend fun prepareCameraExtensions() {
        cameraExtensions = TcCameraExtensions(application, cameraProvider).prepare()
    }

    // endregion

    // region CameraSelector / CameraInfo

//    val TcFacing.cameraInfo : CameraInfo?
//        get() = getCameraInfo(cameraSelector)

    /**
     * フロントカメラ、または、リアカメラの情報(CameraInfo）を取得
     */
//    fun getCameraInfo(facing:TcFacing): CameraInfo? {
//        return facing.cameraInfo
//    }

    fun getCameraInfo(cameraSelector: CameraSelector):CameraInfo? {
        return cameraProvider.getCameraInfo(cameraSelector)
    }

    val hasFrontCamera:Boolean
        get() = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    val hasRearCamera:Boolean
        get() = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)

    // endregion

    // region QualitySelector

    /**
     * cameraInfoで指定されたカメラがサポートしているQualityのリストを取得する。
     */
    fun supportedQualityListForCamera(cameraInfo:CameraInfo?, dr:DynamicRange):List<Quality> {
        if(cameraInfo==null) return emptyList()
        return Recorder.getVideoCapabilities(cameraInfo).getSupportedQualities(dr)
    }

    /**
     * フロントカメラとリアカメラのサポートしているQualityリストのunionを取得する。（使い道なさそう）
     */
//    fun supportedQualityListForBothCamera():List<Quality> {
//        val f = supportedQualityListForCamera(getCameraInfo(true))
//        val b = supportedQualityListForCamera(getCameraInfo(false))
//        return f.union(b).toList()
//
//    }

    /**
     * CameraSelectorと DynamicRange を指定して、その組み合わせがサポートしているQualityのリストを取得
     * （基本形）
     */
    fun supportedQualityList(cameraSelector: CameraSelector, dr:DynamicRange?=null):List<Quality> {
        val supported = supportedQualityListForCamera(getCameraInfo(cameraSelector), dr ?: DynamicRange.SDR).toSet()
        return arrayListOf (Quality.UHD, Quality.FHD, Quality.HD, Quality.SD).filter { supported.contains(it) }
    }

    /**
     * CameraSelectorを指定して、それがサポートしているQualityのリストを取得
     * preferHDR = true なら、そのカメラがHDRをサポートしていれば、そのDynamicRangeについてQualityリストを取得
     * preferHDR = false なら、SDRでのQualityリストを取得
     */
    fun supportedQualityList(cameraSelector: CameraSelector, preferHDR:Boolean):List<Quality> {
        val dr = if(preferHDR) supportedHDR(cameraSelector) else null
        return supportedQualityList(cameraSelector, dr)
    }

    /**
     * フロントカメラ/リアカメラ と DynamicRange を指定して、その組み合わせがサポートしているQualityのリストを取得
     */
    fun supportedQualityList(facing: TcFacing, dr:DynamicRange?=null):List<Quality> {
        return supportedQualityList(facing.cameraSelector, dr)
    }

    /**
     * フロントカメラ/リアカメラを指定して、それがサポートしているQualityのリストを取得
     * preferHDR = true なら、そのカメラがHDRをサポートしていれば、そのDynamicRangeについてQualityリストを取得
     * preferHDR = false なら、SDRでのQualityリストを取得
     */
    fun supportedQualityList(facing:TcFacing, preferHDR:Boolean):List<Quality> {
        return supportedQualityList(facing.cameraSelector, preferHDR)
    }


    // endregion

    // region DynamicRange

    /**
     * cameraSelectorで指定したカメラがサポートしているDynamicRangeのリストを取得
     */
    fun supportedDynamicRangeList(cameraSelector: CameraSelector):Set<DynamicRange> {
        return Recorder.getVideoCapabilities(getCameraInfo(cameraSelector) ?: return emptySet()).supportedDynamicRanges
    }
    /**
     * TcCameraFacingで指定したカメラ（フロント/バック）がサポートしているDynamicRangeのリストを取得
     */
    fun supportedDynamicRangeList(facing: TcFacing):Set<DynamicRange> {
        return supportedDynamicRangeList(facing.cameraSelector)
    }

    /**
     * cameraSelectorで指定したカメラがサポートしているDynamicRangeのうち、HDR系のものを優先して取得する。
     * 優先順序： HLG > HDR10 > HDR10+ > その他 10bit Depth のもの > null（SDR)
     * HDRをサポートしていない場合は null を返す。
     */
    fun supportedHDR(cameraSelector: CameraSelector):DynamicRange? {
        val drs = supportedDynamicRangeList(cameraSelector)
        return when {
            drs.contains(DynamicRange.HLG_10_BIT) -> DynamicRange.HLG_10_BIT
            drs.contains(DynamicRange.HDR10_10_BIT) -> DynamicRange.HDR10_10_BIT
            drs.contains(DynamicRange.HDR10_PLUS_10_BIT) -> DynamicRange.HDR10_PLUS_10_BIT
            else -> drs.firstOrNull { it.bitDepth == 10 }
        }
    }

    /**
     * TcCameraFacingで指定したカメラ（フロント/バック）がサポートしているDynamicRangeのうち、HDR系のものを優先して取得する。
     * 優先順序： HLG > HDR10 > HDR10+ > その他 10bit Depth のもの > null（SDR)
     * HDRをサポートしていない場合は null を返す。
     */
    fun supportedHDR(facing:TcFacing):DynamicRange? {
        return supportedHDR(facing.cameraSelector)
    }

    /**
     * 優先的に選択する DynamicRangeをカメラごとに記憶しておくマップ
     */
    private val dynamicRanges = mutableMapOf<CameraSelector,DynamicRange>()

    /**
     * カメラごとに、優先的に選択する DynamicRangeを設定する。
     */
    fun requestHDR(cameraSelector: CameraSelector, enableHDR:Boolean) {
        if(enableHDR) {
            val hdr = supportedHDR(cameraSelector)
            if (hdr != null) {
                dynamicRanges[cameraSelector] = hdr
                return
            }
        }
        dynamicRanges.remove(cameraSelector)
    }
    /**
     * @param isFront  true:フロントカメラ / false:リアカメラ
     * @param enableHDR true: 可能なら HDR モードを選択 / false: SDRモード
     */
    fun requestHDR(facing: TcFacing, enableHDR:Boolean) {
        requestHDR(facing.cameraSelector, enableHDR)
    }
    fun dynamicRangeOf(cameraSelector: CameraSelector):DynamicRange? {
        return dynamicRanges[cameraSelector]
    }
    fun dynamicRangeOf(isFront:Boolean):DynamicRange? {
        return dynamicRangeOf(if(isFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA)
    }

    // endregion

    // region Torch (Flash Light)

    val torch = TcTorch()

    // endregion

    // region Camera creation

    private fun rawCreateCamera(lifecycleOwner: LifecycleOwner, cameraSelector: CameraSelector, vararg useCases: UseCase): Camera {
        if(useCases.isEmpty()) throw java.lang.IllegalArgumentException("no use cases.")

        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases
            )
        return camera
    }

    fun createCamera(lifecycleOwner:LifecycleOwner, setupMe:(builder:ICameraBuilder)->Unit):TcCamera {
        return CameraBuilder().apply {
            setupMe(this)
        }.build(lifecycleOwner)
    }

    /**
     * カメラ-UseCase のバインドを解除する。
     * バインドしたままアクティビティを閉じてしまうと、UseCase側で例外がでた（実害はなさそうだが）ので、
     * Activity#onDestroy で isFinishing の場合に unbindを呼んでみる。
     */
    fun unbind() {
        torch.detach()
        cameraProvider.unbindAll()
    }

    /**
     * TcCamera構築用ビルダー i/f
     */
    interface ICameraBuilder {
        /**
         * フロントカメラ/リアカメラのどちらを使うか指定
         * @param selectCamera   true:フロントカメラ / false:リアカメラ（デフォルト）
         */
        fun selectCamera(facing: TcFacing):ICameraBuilder
        /**
         * カメラを直接指定（front/rear以外のカメラを指定する場合に使用
         */
        fun selectCamera(cameraSelector: CameraSelector):ICameraBuilder
        /**
         * 拡張モードを指定
         * 利用可能な拡張モードは、TcCameraManager#cameraExtensionsから取得できる。
         *
         * @param mode 拡張モード（デフォルト：NONE）
         */
        fun extensionMode(mode: TcCameraExtensions.Mode): ICameraBuilder

        /**
         * Preview UseCaseを設定（通常はstandardPreview()を使用）
         *
         * @param preview Previewインスタンス
         */
        fun preview(preview: Preview): ICameraBuilder

        /**
         * PreviewViewを previewとして設定
         *
         * @param   previewView PreviewView
         */
        fun standardPreview(previewView:PreviewView): ICameraBuilder

        /**
         * 静止画キャプチャー用UseCase を設定
         */
        fun imageCapture(imageCapture: TcImageCapture): ICameraBuilder

        /**
         * 動画録画用 UseCase を設定
         */
        fun videoCapture(videoCapture: TcVideoCapture): ICameraBuilder

        /**
         * 指定したカメラが存在しないときにbuild()で例外をスローするか？
         * デフォルトは false（適当にフォールバックする）
         */
        fun errorIfNotHasCamera(errorIfNotHasCamera:Boolean): ICameraBuilder
    }

    /**
     * TcCamera構築用ビルダー実装
     */
    private inner class CameraBuilder : ICameraBuilder {
        private var mCameraSelector: CameraSelector? = null
        private var mExtensionMode: TcCameraExtensions.Mode = TcCameraExtensions.Mode.NONE
        private var mPreview: Preview? = null
        private var mPreviewView: PreviewView? = null
        private var mImageCapture: TcImageCapture? = null
        private var mVideoCapture: TcVideoCapture? = null
        private var mErrorIfNotHasCamera:Boolean = false    // true: build()で例外を投げる / false: front or rear にフォールバックする

        override fun selectCamera(facing: TcFacing): CameraBuilder = apply {
            mCameraSelector = facing.cameraSelector
        }

        override fun selectCamera(cameraSelector: CameraSelector): ICameraBuilder = apply {
            mCameraSelector = cameraSelector
        }

        override fun extensionMode(mode: TcCameraExtensions.Mode): CameraBuilder = apply {
            mExtensionMode = mode
        }

        override fun preview(preview: Preview): CameraBuilder = apply {
            mPreview = preview
            mPreviewView = null
        }
        override fun standardPreview(previewView: PreviewView): CameraBuilder = apply {
            mPreview = null
            mPreviewView = previewView
        }
        override fun imageCapture(imageCapture: TcImageCapture): CameraBuilder = apply {
            mImageCapture = imageCapture
        }

        override fun videoCapture(videoCapture: TcVideoCapture): CameraBuilder = apply {
            mVideoCapture = videoCapture
        }


        override fun errorIfNotHasCamera(errorIfNotHasCamera: Boolean): CameraBuilder = apply {
            mErrorIfNotHasCamera = errorIfNotHasCamera
        }

        fun build(lifecycleOwner: LifecycleOwner): TcCamera {
            var cameraSelector:CameraSelector = mCameraSelector ?: CameraSelector.DEFAULT_BACK_CAMERA
            if (!cameraProvider.hasCamera(cameraSelector)) {
                if(hasFrontCamera) {
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                } else if(hasRearCamera) {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    throw IllegalStateException("no camera.")
                }
            }
            if(mExtensionMode!=TcCameraExtensions.Mode.NONE) {
                cameraSelector = cameraExtensions.applyExtensionTo(mExtensionMode, cameraSelector)
            }
            // Previewの準備
            val preview = mPreview ?: mPreviewView?.let { pv ->
                Preview.Builder().apply {
                    @SuppressLint("RestrictedApi")
                    val hdr = dynamicRangeOf(cameraSelector)
                    if (hdr!=null) {
                        setDynamicRange(hdr)
                    }
                }.build().apply {
                    surfaceProvider = pv.surfaceProvider
                }
            }
            val camera = rawCreateCamera(lifecycleOwner, cameraSelector, *(arrayOf(preview, mImageCapture?.useCase, mVideoCapture?.useCase).filterNotNull().toTypedArray()))
            return TcCamera(camera, cameraSelector, mPreview, mImageCapture, mVideoCapture).apply { torch.attach(this) }
        }
    }

    // endregion

    // region Building VideoCapture UseCase

    interface IVideoCaptureBuilder {
        fun executor(executor: Executor): IVideoCaptureBuilder
        fun useFixedPoolExecutor(): IVideoCaptureBuilder
        fun recordingStateFlow(flow:MutableStateFlow<RecordingState>): IVideoCaptureBuilder
        fun limitResolution(resolution: TcVideoResolution): IVideoCaptureBuilder
        fun preferAspectRatio(aspect: TcAspect): IVideoCaptureBuilder
        fun build(): TcVideoCapture
    }

    private inner class VideoCaptureBuilder(val cameraSelector: CameraSelector) : IVideoCaptureBuilder {
        val innerBuilder = TcVideoCapture.builder
        private var mResolution: TcVideoResolution? = null
        private var mAspect: TcAspect = TcAspect.Default

        override fun executor(executor: Executor): IVideoCaptureBuilder = apply {
            innerBuilder.executor(executor)
        }

        override fun useFixedPoolExecutor(): IVideoCaptureBuilder = apply {
            innerBuilder.useFixedPoolExecutor()
        }

        override fun recordingStateFlow(flow: MutableStateFlow<RecordingState>) = apply {
            innerBuilder.recordingStateFlow(flow)
        }

        override fun limitResolution(resolution: TcVideoResolution) = apply {
            mResolution = resolution
        }

        override fun preferAspectRatio(aspect: TcAspect) = apply {
            innerBuilder.preferAspectRatio(aspect)
        }

        override fun build(): TcVideoCapture {
            val hdr = dynamicRangeOf(cameraSelector)
            when(val resolution = mResolution) {
                null -> {} // 指定なし --> Highest
                TcVideoResolution.LOWEST, TcVideoResolution.HIGHEST -> innerBuilder.limitResolution(resolution)
                else -> {
                    val qualities = supportedQualityList(cameraSelector, hdr)
                    val limitedQuality = qualities.filter {
                        (TcVideoResolution.fromQuality(it) ?: return@filter false).order <= resolution.order
                    }
                    if(limitedQuality.isNotEmpty()) {
                        innerBuilder.resolutionFromQualityList(limitedQuality)
                    } else {
                        innerBuilder.resolutionFromQualityList(qualities)
                    }
                }
            }
            if (hdr!=null) {
                innerBuilder.dynamicRange(hdr)
            }
            return innerBuilder.build()
        }
    }

    /**
     * VideoCapture UseCase構築用ビルダーを取得
     * TcVideoCaptureのビルダーをラップして、カメラのサポートするQualityリストやDynamicRangeを考慮して
     * 適切な解像度設定を行ってから TcVideoCaptureを構築する。
     */
    fun videoCaptureBuilder(cameraSelector: CameraSelector): IVideoCaptureBuilder {
        return VideoCaptureBuilder(cameraSelector)
    }
    fun videoCaptureBuilder(facing: TcFacing): IVideoCaptureBuilder
        = videoCaptureBuilder(facing.cameraSelector)


    // endregion

    // region Building ImageCapture UseCase

    interface IImageCaptureBuilder {
        /**
         * Zero Shutter Lag モード (CAPTURE_MODE_ZERO_SHUTTER_LAG) を指定
         */
        fun zeroLag(): IImageCaptureBuilder
        /**
         * レイテンシー最小化モード (CAPTURE_MODE_MINIMIZE_LATENCY) を指定
         */
        fun minimizeLatency(): IImageCaptureBuilder
        /**
         * 画質最大化モード (CAPTURE_MODE_MAXIMIZE_QUALITY) を指定
         */
        fun maximizeQuality(): IImageCaptureBuilder
        /**
         * 解像度選択のヒント（解像度優先 or キャプチャ速度優先）を指定
         */
        fun qualityHint(hint: TcImageQualityHint?): IImageCaptureBuilder
        /**
         * アスペクト比の優先設定
         */
        fun preferAspectRatio(aspect: TcAspect) : IImageCaptureBuilder

        /**
         * JPEG Quality 1-100 を設定
         * 0 を指定するとデフォルト（CAPTURE_MODE_MINIMIZE_LATENCY, CAPTURE_MODE_MINIMIZE_LATENCY などのモードによってシステムにより決定される）
         */
        fun jpegQuality(q:Int): IImageCaptureBuilder

        /**
         * カメラの解像度を指定
         * 指定しなければ、最高画質で撮影
         */
        fun resolution(strategy: ResolutionStrategy): IImageCaptureBuilder
        /**
         * カメラの解像度を指定
         * 指定しなければ、最高画質で撮影
         */
        fun resolution(resolution: TcImageResolution): IImageCaptureBuilder

        fun build(): TcImageCapture
    }

    private inner class ImageCaptureBuilder(
        val cameraSelector: CameraSelector,
        val innerBuilder: TcImageCapture.IBuilder = TcImageCapture.builder) : IImageCaptureBuilder {

        @OptIn(ExperimentalZeroShutterLag::class)
        override fun zeroLag() = apply {
            if(getCameraInfo(cameraSelector)?.isZslSupported == true) {
                innerBuilder.zeroLag()
            } else {
                innerBuilder.minimizeLatency()
            }
        }

        override fun minimizeLatency() = apply {
            innerBuilder.minimizeLatency()
        }

        override fun maximizeQuality() = apply {
            innerBuilder.maximizeQuality()
        }

        override fun qualityHint(hint: TcImageQualityHint?) = apply {
            innerBuilder.qualityHint(hint)
        }

        override fun preferAspectRatio(aspect: TcAspect) = apply {
            innerBuilder.preferAspectRatio(aspect)
        }

        override fun jpegQuality(q: Int) = apply {
            innerBuilder.jpegQuality(q)
        }

        override fun resolution(strategy: ResolutionStrategy) = apply {
            innerBuilder.resolution(strategy)
        }

        override fun resolution(resolution: TcImageResolution) = apply {
            innerBuilder.resolution(resolution)
        }

        override fun build(): TcImageCapture {
            val hdr = dynamicRangeOf(cameraSelector)
            if (hdr!=null) {
                innerBuilder.dynamicRange(hdr)
                val cameraInfo = getCameraInfo(cameraSelector)
                if (cameraInfo!=null && TcImageCapture.isHdrJpegSupported(cameraInfo)) {
                    innerBuilder.outputHdrJpeg(true)
                }
            }
            return innerBuilder.build()
        }
    }

    fun imageCaptureBuilder(cameraSelector: CameraSelector): IImageCaptureBuilder{
        return ImageCaptureBuilder(cameraSelector)
    }
    fun imageCaptureBuilder(facing: TcFacing): IImageCaptureBuilder
        = imageCaptureBuilder(facing.cameraSelector)

    // endregion
}
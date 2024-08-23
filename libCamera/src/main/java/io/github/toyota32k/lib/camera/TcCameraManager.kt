package io.github.toyota32k.lib.camera

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.lifecycle.LifecycleOwner
import io.github.toyota32k.lib.camera.usecase.TcImageCapture
import io.github.toyota32k.lib.camera.usecase.TcVideoCapture

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

    // region QualitySelector

    /**
     * cameraInfoで指定されたカメラがサポートしているQualityのリストを取得する。
     */
    fun supportedQualityListForCamera(cameraInfo:CameraInfo?, dr:DynamicRange=DynamicRange.SDR):List<Quality> {
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
     * フロントカメラ、または、リアカメラがサポートしているQualityのリストを取得
     */
    fun supportedQualityList(isFront:Boolean?=null):List<Quality> {
        val supported = when(isFront) {
            true-> {supportedQualityListForCamera(getCameraInfo(true)).toSet() }
            false-> {supportedQualityListForCamera(getCameraInfo(false)).toSet() }
            null->{supportedQualityListForCamera(getCameraInfo(false)).intersect(supportedQualityListForCamera(getCameraInfo(true)))}
        }
        return arrayListOf (Quality.UHD, Quality.FHD, Quality.HD, Quality.SD).filter { supported.contains(it) }
    }

    // endregion

    // region CameraSelector (and the camera specific properties)

//    fun getCameraSelector(frontCamera: Boolean, extensionMode: TcCameraExtensions.Mode): CameraSelector {
//        val baseCameraSelector = if(frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
//        return cameraExtensions.applyExtensionTo(extensionMode, baseCameraSelector)
//    }

    /**
     * フロントカメラ、または、リアカメラの情報(CameraInfo）を取得
     */
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    fun getCameraInfo(frontCamera: Boolean): CameraInfo? {
        val target = if(frontCamera) CameraMetadata.LENS_FACING_FRONT else CameraMetadata.LENS_FACING_BACK
        return cameraProvider.availableCameraInfos.filter {
            Camera2CameraInfo
                .from(it)
                .getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == target
        }.firstOrNull()
    }

    val hasFrontCamera:Boolean
        get() = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    val hasRearCamera:Boolean
        get() = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)

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

    fun createCamera(lifecycleOwnber:LifecycleOwner, setupMe:(builder:ICameraBuilder)->Unit):TcCamera {
        return CameraBuilder().apply {
            setupMe(this)
        }.build(lifecycleOwnber)
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

    interface ICameraBuilder {
        /**
         * フロントカメラ/リアカメラのどちらを使うか指定
         * @param frontCamera   true:フロントカメラ / false:リアカメラ（デフォルト）
         */
        fun frontCamera(frontCamera:Boolean):ICameraBuilder
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
    inner class CameraBuilder : ICameraBuilder {
        private var mCameraSelector: CameraSelector? = null
        private var mExtensionMode: TcCameraExtensions.Mode = TcCameraExtensions.Mode.NONE
        private var mPreview: Preview? = null
        private var mImageCapture: TcImageCapture? = null
        private var mVideoCapture: TcVideoCapture? = null
        private var mErrorIfNotHasCamera:Boolean = false    // true: build()で例外を投げる / false: front or rear にフォールバックする

        override fun frontCamera(frontCamera: Boolean): CameraBuilder {
            mCameraSelector = if(frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            return this
        }

        override fun selectCamera(cameraSelector: CameraSelector): ICameraBuilder {
            mCameraSelector = cameraSelector
            return this
        }

        override fun extensionMode(mode: TcCameraExtensions.Mode): CameraBuilder {
            mExtensionMode = mode
            return this
        }
        override fun preview(preview: Preview): CameraBuilder {
            mPreview = preview
            return this
        }
        override fun standardPreview(previewView: PreviewView): CameraBuilder {
            return preview(Preview.Builder().build().apply {
                // Connect the preview to receive the surface the camera outputs the frames
                // to. This will allow displaying the camera frames in either a TextureView
                // or SurfaceView. The SurfaceProvider can be obtained from the PreviewView.
                setSurfaceProvider(previewView.surfaceProvider)
            })
        }
        override fun imageCapture(imageCapture: TcImageCapture): CameraBuilder {
            mImageCapture = imageCapture
            return this
        }

        override fun videoCapture(videoCapture: TcVideoCapture): CameraBuilder {
            mVideoCapture = videoCapture
            return this
        }

        override fun errorIfNotHasCamera(errorIfNotHasCamera: Boolean): CameraBuilder {
            mErrorIfNotHasCamera = errorIfNotHasCamera
            return this
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
            val camera = rawCreateCamera(lifecycleOwner, cameraSelector, *(arrayOf(mPreview, mImageCapture?.useCase, mVideoCapture?.useCase).filterNotNull().toTypedArray()))
            return TcCamera(camera, cameraSelector, mPreview, mImageCapture, mVideoCapture).apply { torch.attach(this) }
        }
    }

    // endregion
}
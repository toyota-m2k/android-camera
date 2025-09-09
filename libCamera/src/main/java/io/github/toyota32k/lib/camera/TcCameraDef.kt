package io.github.toyota32k.lib.camera

import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.video.Quality

enum class TcFacing(val value:Int) {
    FRONT(CameraSelector.LENS_FACING_FRONT),
    BACK(CameraSelector.LENS_FACING_BACK),
    @ExperimentalLensFacing
    EXTERNAL(CameraSelector.LENS_FACING_EXTERNAL),
//    UNKNOWN(CameraSelector.LENS_FACING_UNKNOWN)
    ;
    companion object {
        fun fromValue(value:Int):TcFacing? {
            return entries.firstOrNull { it.value == value }
        }
        fun ofFront(isFront:Boolean):TcFacing {
            return if (isFront) FRONT else BACK
        }
        val TcFacing.cameraSelector :CameraSelector
            get() {
                return when(this) {
                    FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                    BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                    @ExperimentalLensFacing
                    EXTERNAL -> CameraSelector.Builder().requireLensFacing(this.value).build()
//                    else -> throw IllegalStateException("unknown camera facing.")
                }
            }
        val CameraInfo.facing:TcFacing? get() = fromValue(lensFacing)
    }
}

enum class TcResolution(val quality: Quality, val order:Int) {
    UHD(Quality.UHD, 4), FHD(Quality.FHD, 3), HD(Quality.HD, 2), SD(Quality.SD, 1),
    HIGHEST(Quality.HIGHEST, Int.MAX_VALUE), LOWEST(Quality.LOWEST, Int.MIN_VALUE)
    ;
    companion object {
        fun fromQuality(quality: Quality):TcResolution? {
            return entries.firstOrNull { it.quality == quality }
        }
    }
}

enum class TcAspect(val aspectStrategy: AspectRatioStrategy?, val ratio:Int) {
    Default(null, AspectRatio.RATIO_DEFAULT),
    Ratio4_3(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY, AspectRatio.RATIO_4_3),
    Ratio16_9(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY, AspectRatio.RATIO_16_9),
}

enum class TcImageResolutionHint(val value:Int) {
    /**
     * 解像度を優先
     */
    PreferQuality(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE),

    /**
     * キャプチャーレートを優先
     */
    PreferPerformance(ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION),
}
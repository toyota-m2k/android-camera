package io.github.toyota32k.lib.camera

import android.util.Size
import androidx.annotation.OptIn
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
            @OptIn(ExperimentalLensFacing::class)
            get() {
                return when(this) {
                    FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                    BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                    EXTERNAL -> CameraSelector.Builder().requireLensFacing(this.value).build()
//                    else -> throw IllegalStateException("unknown camera facing.")
                }
            }
        val CameraInfo.facing:TcFacing? get() = fromValue(lensFacing)
    }
}

enum class TcVideoResolution(val quality: Quality, val order:Int) {
    UHD(Quality.UHD, 4),
    FHD(Quality.FHD, 3),
    HD(Quality.HD, 2),
    SD(Quality.SD, 1),
    HIGHEST(Quality.HIGHEST, Int.MAX_VALUE),
    LOWEST(Quality.LOWEST, Int.MIN_VALUE)
    ;
    companion object {
        fun fromQuality(quality: Quality):TcVideoResolution? {
            return entries.firstOrNull { it.quality == quality }
        }
    }
}

enum class TcAspect(val aspectStrategy: AspectRatioStrategy?, val ratio:Int) {
    Default(null, AspectRatio.RATIO_DEFAULT),
    Ratio4_3(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY, AspectRatio.RATIO_4_3),
    Ratio16_9(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY, AspectRatio.RATIO_16_9),
    ;
    companion object {
        fun fromRatio(ratio:Int):TcAspect {
            return entries.firstOrNull { it.ratio == ratio } ?: Default
        }
    }
}

enum class TcImageQualityHint(val value:Int) {
    /**
     * 解像度を優先
     * JPEG Quality = 100
     */
    PreferQuality(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE),

    /**
     * キャプチャーレートを優先
     * JPEG Quality = 95
     */
    PreferPerformance(ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION),
}

enum class TcImageResolution(val size:Size, val ratio:TcAspect) {
    HIGHEST(Size(0,0), TcAspect.Default),

    UHD(Size(3840, 2160), TcAspect.Ratio16_9),
    FHD(Size(1920, 1080), TcAspect.Ratio16_9),
    HD(Size(1280, 720), TcAspect.Ratio16_9),

    UXGA(Size(1600, 1200), TcAspect.Ratio4_3),
    SXGA(Size(1280, 1024), TcAspect.Ratio4_3),
    XGA(Size(1024, 768), TcAspect.Ratio4_3),
    SVGA(Size(800, 600), TcAspect.Ratio4_3),
    VGA(Size(640, 480), TcAspect.Ratio4_3),

    LOWEST(Size(0,0), TcAspect.Default),
    ;

    fun toResolutionStrategy() : ResolutionStrategy? {
        return when (this) {
            HIGHEST -> null
            LOWEST -> VGA.toResolutionStrategy()
            else -> ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
        }
    }
}
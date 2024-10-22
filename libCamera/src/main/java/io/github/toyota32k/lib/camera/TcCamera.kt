package io.github.toyota32k.lib.camera

import android.util.Rational
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.core.Preview
import androidx.concurrent.futures.await
import io.github.toyota32k.lib.camera.usecase.TcImageCapture
import io.github.toyota32k.lib.camera.usecase.TcVideoCapture
import java.awt.font.NumericShaper.Range
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TcCameraManager が作成するカメラとその情報を保持するクラス
 */
data class TcCamera(
    val camera: Camera,
    val cameraSelector: CameraSelector,
    val preview: Preview? = null,
    val imageCapture: TcImageCapture? = null,
    val videoCapture: TcVideoCapture? = null,
    ) {
    val cameraInfo: CameraInfo
        get() = camera.cameraInfo

    enum class Position {
        REAR,
        FRONT,
        EXTERNAL,
        UNKNOWN
    }

    val cameraPosition:Position
        get() = when(cameraInfo.lensFacing) {
            CameraSelector.LENS_FACING_UNKNOWN-> Position.UNKNOWN
            CameraSelector.LENS_FACING_BACK -> Position.REAR
            CameraSelector.LENS_FACING_FRONT -> Position.FRONT
            else -> Position.EXTERNAL
        }
    val frontCamera:Boolean get() = cameraPosition == Position.FRONT
    val rearCamera:Boolean get() = cameraPosition == Position.REAR

    inner class ExposureIndex {
        val isSupported:Boolean get() = cameraInfo.exposureState.isExposureCompensationSupported
        val min:Int
        val max:Int
        val value:Int get() = cameraInfo.exposureState.exposureCompensationIndex
        var pendingValue:Int? = null
        var busy = false
        init {
            if(isSupported) {
                val range = cameraInfo.exposureState.exposureCompensationRange
//                val step = cameraInfo.exposureState.exposureCompensationStep
//                min = (range.lower * step.denominator) / step.numerator
//                max = (range.upper * step.denominator) / step.numerator
                min = range.lower
                max = range.upper
            } else {
                min = 0
                max = 0
            }
        }
        suspend fun setIndex(value:Int) {
            if(!isSupported) return
            synchronized(this) {
                if(busy) {
                    pendingValue = value
                    return
                }
                busy = true
            }
            try {
                var v:Int? = value
                while(v!=null) {
                    camera.cameraControl.setExposureCompensationIndex(v.coerceIn(min, max)).await()
                    TcLib.logger.debug("set exposure index: $v")
                    synchronized(this) {
                        v = pendingValue
                        pendingValue = null
                    }
                }
            } catch(e:Exception) {
                TcLib.logger.error(e)
            } finally {
                synchronized(this) {
                    busy = false
                }
            }
        }
    }
    val exposureIndex by lazy { ExposureIndex() }
}

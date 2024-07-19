package io.github.toyota32k.lib.camera

import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.core.Preview
import io.github.toyota32k.lib.camera.usecase.TcImageCapture
import io.github.toyota32k.lib.camera.usecase.TcVideoCapture

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
}

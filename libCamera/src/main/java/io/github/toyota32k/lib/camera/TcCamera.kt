package io.github.toyota32k.lib.camera

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import io.github.toyota32k.lib.camera.usecase.TcImageCapture
import io.github.toyota32k.lib.camera.usecase.TcVideoCapture

/**
 * TcCameraManager が作成するカメラとその情報を保持するクラス
 */
data class TcCamera(
    val camera: Camera,
    val frontCamera:Boolean,
    val cameraSelector: CameraSelector,
    val preview: Preview? = null,
    val imageCapture: TcImageCapture? = null,
    val videoCapture: TcVideoCapture? = null,
    )
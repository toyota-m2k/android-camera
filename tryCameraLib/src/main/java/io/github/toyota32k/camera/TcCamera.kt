package io.github.toyota32k.camera

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import io.github.toyota32k.camera.usecase.TcImageCapture
import io.github.toyota32k.camera.usecase.TcVideoCapture

data class TcCamera(
    val camera: Camera,
    val frontCamera:Boolean,
    val cameraSelector: CameraSelector,
    val preview: Preview? = null,
    val imageCapture: TcImageCapture? = null,
    val videoCapture: TcVideoCapture? = null,
    )
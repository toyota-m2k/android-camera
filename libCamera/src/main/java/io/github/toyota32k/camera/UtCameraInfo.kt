package io.github.toyota32k.camera

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview

class UtCameraInfo(
    val camera: Camera,
    val frontCamera:Boolean,
    val cameraSelector: CameraSelector,
    val preview: Preview,
    ) {
}
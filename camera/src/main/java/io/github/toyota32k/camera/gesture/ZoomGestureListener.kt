package io.github.toyota32k.camera.gesture

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.FocusMeteringAction
import io.github.toyota32k.camera.CameraLib
import io.github.toyota32k.camera.await
import kotlinx.coroutines.launch

class ZoomGestureListener(private val cameraOwner: ICameraGestureOwner) : ScaleGestureDetector.OnScaleGestureListener {
    companion object {
        val logger = CameraLib.logger
    }
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val camera = cameraOwner.camera ?: return false
        val zoomState = camera.cameraInfo.zoomState
        val currentZoomRatio: Float = zoomState.value?.zoomRatio ?: 1f
        camera.cameraControl.setZoomRatio(
            detector.scaleFactor * currentZoomRatio
        )
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        return cameraOwner.camera != null
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
    }
}
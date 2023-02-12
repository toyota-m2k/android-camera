package io.github.toyota32k.camera.gesture

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.camera.core.FocusMeteringAction
import io.github.toyota32k.camera.CameraLib
import io.github.toyota32k.camera.await
import kotlinx.coroutines.launch

class FocusGestureListener(private val cameraOwner: ICameraGestureOwner) : GestureDetector.SimpleOnGestureListener() {
    companion object {
        val logger = CameraLib.logger
    }
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val previewView = cameraOwner.previewView ?: return false
        val camera = cameraOwner.camera ?: return false
        val meteringPointFactory = previewView.meteringPointFactory
        val focusPoint = meteringPointFactory.createPoint(e.x, e.y)
        val meteringAction = FocusMeteringAction
            .Builder(focusPoint).build()
        cameraOwner.lifecycleScope.launch {
            val focusResult = camera.cameraControl
                .startFocusAndMetering(meteringAction)
                .await(cameraOwner.context)
            if (!focusResult.isFocusSuccessful()) {
                logger.error("tap-to-focus failed")
            }
        }
        return true
    }
}
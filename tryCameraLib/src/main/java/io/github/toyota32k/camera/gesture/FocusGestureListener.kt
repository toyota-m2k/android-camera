package io.github.toyota32k.camera.gesture

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.camera.core.FocusMeteringAction
import androidx.core.view.GestureDetectorCompat
import io.github.toyota32k.camera.TcLib
import io.github.toyota32k.camera.await
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class FocusGestureListener(
    cameraOwner: ICameraGestureOwner,
    private val enableFocus:Boolean,
    private val longTapToFocus:Boolean,
    var customAction:(()->Unit)? = null
) : GestureDetector.SimpleOnGestureListener() {
    companion object {
        val logger = TcLib.logger
    }
    private val cameraOwnerRef = WeakReference(cameraOwner)


    private fun focus(e:MotionEvent):Boolean {
        if(!enableFocus) return false
        val cameraOwner = cameraOwnerRef.get() ?: return false
        val previewView = cameraOwner.previewView ?: return false
        val camera = cameraOwner.camera ?: return false
        val meteringPointFactory = previewView.meteringPointFactory
        val focusPoint = meteringPointFactory.createPoint(e.x, e.y)
        val meteringAction = FocusMeteringAction
            .Builder(focusPoint).build()
        cameraOwner.gestureScope.launch {
            try {
                camera.cameraControl
                    .startFocusAndMetering(meteringAction)
                    .await(cameraOwner.context)
            } catch(e:Throwable) {
                logger.error(e)
            }
        }
        return true
    }

    private fun invokeCustomCommand():Boolean {
        customAction?.invoke() ?: return false
        return true
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        logger.debug("single tap")
        return if(!longTapToFocus) {
            focus(e)
        } else {
            invokeCustomCommand()
        }
    }

    override fun onLongPress(e: MotionEvent) {
        logger.debug("long tap")
        if(!longTapToFocus) {
            invokeCustomCommand()
        } else {
            focus(e)
        }
    }

    private val detector = GestureDetectorCompat(cameraOwner.context, this)
    fun onTouchEvent(event: MotionEvent):Boolean {
        return detector.onTouchEvent(event)
    }
}
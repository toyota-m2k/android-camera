package io.github.toyota32k.lib.camera.gesture

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.camera.core.FocusMeteringAction
import androidx.concurrent.futures.await
import io.github.toyota32k.lib.camera.TcLib
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class FocusGestureListener(
    cameraOwner: ICameraGestureOwner,
    private val enableFocus:Boolean,
    private val longTapToFocus:Boolean,
    var singleTapCustomAction:(()->Boolean)? = null,
    var longTapCustomAction:(()->Boolean)? = null
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
                    .await()
            } catch(e:Throwable) {
                logger.error(e)
            }
        }
        return true
    }

    private fun invokeCustomCommand(fn:(()->Boolean)?):Boolean {
        return fn?.invoke() ?: return false
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        logger.debug("single tap")
        if(invokeCustomCommand(singleTapCustomAction)) {
            return true
        }
        return if(!longTapToFocus) {
            focus(e)
        } else false
    }

    override fun onLongPress(e: MotionEvent) {
        logger.debug("long tap")
        if(invokeCustomCommand(longTapCustomAction)) {
            return
        }
        if(longTapToFocus) {
            focus(e)
        }
    }

    private val detector = GestureDetector(cameraOwner.context, this)
    fun onTouchEvent(event: MotionEvent):Boolean {
        return detector.onTouchEvent(event)
    }
}
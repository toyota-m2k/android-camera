package io.github.toyota32k.camera.lib.gesture

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.ZoomState
import androidx.concurrent.futures.await
import io.github.toyota32k.camera.lib.TcLib
import kotlinx.coroutines.launch
import java.lang.Float.max
import java.lang.Float.min
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class ZoomGestureListener(cameraOwner: ICameraGestureOwner) : ScaleGestureDetector.OnScaleGestureListener {
    companion object {
        val logger = TcLib.logger
    }
    private val cameraOwnerRef = WeakReference(cameraOwner)

    private fun clip(value:Float, min:Float, max:Float):Float {
        return max(min, min(max,value))
    }

    private fun quantize(value:Float):Int {
        return (value*10f).roundToInt()
    }

    private fun nextZoom(zoomState: ZoomState?, scaleFactor:Float):Float {
        if(zoomState==null) return 1f
        val newValue = quantize(clip(zoomState.zoomRatio*scaleFactor, zoomState.minZoomRatio, zoomState.maxZoomRatio))
        return if(newValue == quantize(zoomState.zoomRatio)) 0f else (newValue.toFloat())/10f
    }

    private val isBusy = AtomicBoolean(false)

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val cameraOwner = cameraOwnerRef.get() ?: return false
        val camera = cameraOwner.camera ?: return false
        val zoom = nextZoom(camera.cameraInfo.zoomState.value, detector.scaleFactor)
        if(zoom<1.0f) return false
        if(!isBusy.compareAndSet(false, true)) return false

        cameraOwner.gestureScope.launch {
            try {
                camera.cameraControl.setZoomRatio(zoom).await()
            } catch(e:Throwable) {
                logger.error(e)
            } finally {
                isBusy.set(false)
            }
//            logger.debug("zoom = $zoom")
        }
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        return cameraOwnerRef.get()?.camera != null
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
    }

    private val detector = ScaleGestureDetector(cameraOwner.context, this)
    val isInProgress
        get() = detector.isInProgress
    fun onTouchEvent(event: MotionEvent):Boolean {
        return detector.onTouchEvent(event)
    }
}
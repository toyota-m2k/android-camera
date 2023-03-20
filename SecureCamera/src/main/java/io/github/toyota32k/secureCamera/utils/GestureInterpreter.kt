package io.github.toyota32k.secureCamera.utils

import android.content.Context
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.View.OnTouchListener
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.LifecycleOwner
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.utils.*

class GestureInterpreter(
    applicationContext: Context,
    enableScaleEvent:Boolean,
    val rapidTap:Boolean = false        // true にすると、onSingleTapUp で tapEvent を発行。ただし、doubleTapEventは無効になる。
) : OnTouchListener {
    // region Scroll / Swipe Event

    interface IScrollEvent {
        val dx: Float
        val dy: Float
        val end: Boolean
    }

    val scrollListener: Listeners<IScrollEvent>
        get() = scrollListenerRef.value

    private val scrollListenerRef =
        UtLazyResetableValue<Listeners<IScrollEvent>> { Listeners<IScrollEvent>() }

    private data class ScrollEvent(
        override var dx: Float,
        override var dy: Float,
        override var end: Boolean
    ) : IScrollEvent

    private val scrollEvent = ScrollEvent(0f, 0f, false)
    private fun fireScrollEvent(dx: Float, dy: Float, end: Boolean): Boolean {
        return if (scrollListenerRef.hasValue && scrollListener.count > 0) {
            scrollListener.invoke(scrollEvent.apply {
                this.dx = dx
                this.dy = dy
                this.end = end
            })
            true
        } else false
    }

    // endregion

    // region Scale / Zoom Event

    interface IScaleEvent {
        val scale: Float
        val end: Boolean
    }

    val scaleListener: Listeners<IScaleEvent>
        get() = scaleListenerRef.value

    private val scaleListenerRef =
        UtLazyResetableValue<Listeners<IScaleEvent>> { Listeners<IScaleEvent>() }

    private class ScaleEvent(override var scale: Float, override var end: Boolean) : IScaleEvent

    private val scaleEvent = ScaleEvent(1f, false)
    private fun fireScaleEvent(scale: Float, end: Boolean): Boolean {
        return if (scaleListenerRef.hasValue && scaleListener.count > 0) {
            scaleListener.invoke(scaleEvent.apply {
                this.scale = scale
                this.end = end
            })
            true
        } else false
    }

    // endregion

    // region Tap / Click Event
    interface IPositionalEvent {
        val x:Float
        val y:Float
    }
    class PositionalEvent(override var x: Float, override var y: Float):IPositionalEvent
    private val positionalEvent = PositionalEvent(0f,0f)

    val tapListeners: Listeners<IPositionalEvent>
        get() = tapListenersRef.value

    private val tapListenersRef = UtLazyResetableValue { Listeners<IPositionalEvent>() }
    private val hasTapListeners:Boolean get() = tapListenersRef.hasValue && tapListeners.count>0
    private fun fireTapEvent(x:Float, y:Float): Boolean {
        return if (hasTapListeners) {
            tapListeners.invoke(positionalEvent.apply {
                this.x = x
                this.y = y })
            true
        } else false
    }

    // endregion

    // region Long Tap
    val longTapListeners: Listeners<IPositionalEvent>
        get() = longTapListenersRef.value

    private val longTapListenersRef = UtLazyResetableValue { Listeners<IPositionalEvent>() }
    private val hasLongTapListeners:Boolean get() = longTapListenersRef.hasValue && longTapListeners.count>0
    private fun fireLongTapEvent(x:Float, y:Float): Boolean {
        return if (hasLongTapListeners) {
            longTapListeners.invoke(positionalEvent.apply {
                this.x = x
                this.y = y })
            true
        } else false
    }
    // endregion

    // region Double Tap

    val doubleTapListeners: Listeners<IPositionalEvent>
        get() = doubleTapListenersRef.value
    private val doubleTapListenersRef = UtLazyResetableValue { Listeners<IPositionalEvent>() }
    private val hasDoubleTapListeners:Boolean get() = doubleTapListenersRef.hasValue && doubleTapListeners.count>0
    private fun fireDoubleTapEvent(x:Float, y:Float): Boolean {
        return if (hasDoubleTapListeners) {
            doubleTapListeners.invoke(positionalEvent.apply {
                this.x = x
                this.y = y })
            true
        } else false
    }

    // endregion

    // region Setup Helper

    class SetupHelper {
        var onScroll:  ((IScrollEvent)->Unit)? = null
        var onScale:  ((IScaleEvent)->Unit)? = null
        var onTap: ((IPositionalEvent)->Unit)? = null
        var onLongTap: ((IPositionalEvent)->Unit)? = null
        var onDoubleTap: ((IPositionalEvent)->Unit)? = null
    }
    fun setup(owner:LifecycleOwner, view:View, setupMe:SetupHelper.()->Unit) {
        attachView(view)
        SetupHelper().apply {
            setupMe()
            onScroll?.apply {
                scrollListener.add(owner, this)
            }
            onScale?.apply {
                scaleListener.add(owner, this)
            }
            onTap?.apply {
                tapListeners.add(owner, this)
            }
            onLongTap?.apply {
                longTapListeners.add(owner, this)
            }
            onDoubleTap?.apply {
                doubleTapListeners.add(owner, this)
            }
        }
    }


    // endregion

    private val touchGestureDetector: GestureDetectorCompat =
        GestureDetectorCompat(applicationContext, SwipeGestureListener())
    private var scaleGestureDetector: ScaleGestureDetector? = if (enableScaleEvent) {
        ScaleGestureDetector(applicationContext, ScaleListener())
    } else null

    fun attachView(view: View) {
        view.isClickable = true
        view.setOnTouchListener(this)
    }

    fun detachView(view:View) {
        view.setOnTouchListener(null)
    }

    var scrolling: Boolean = false

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
//        logger.debug("$event")
        scaleGestureDetector?.onTouchEvent(event)
        touchGestureDetector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_POINTER_UP) {
            if (scrolling) {
                scrolling = false
                fireScrollEvent(0f, 0f, true)
            }
        }
        return v?.performClick() == true
    }

    private inner class SwipeGestureListener : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            logger.debug(GI_LOG) {"$e"}
            return false
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            logger.debug(GI_LOG) {"$e"}
            return if(rapidTap) {
                fireTapEvent(e.x, e.y)
            } else false
        }

        override fun onShowPress(e: MotionEvent) {
            logger.debug(GI_LOG) {"$e"}
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            logger.debug(GI_LOG) {"$e"}
            return if(!rapidTap) {
                fireTapEvent(e.x, e.y)
            } else false
        }

        override fun onContextClick(e: MotionEvent): Boolean {
            logger.debug(GI_LOG) {"$e"}
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            logger.debug(GI_LOG) {"$e"}
            fireLongTapEvent(e.x, e.y)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            logger.debug(GI_LOG) {"$e"}
            return if(!rapidTap) {
                fireDoubleTapEvent(e.x, e.y)
            } else false
        }

        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            logger.debug(GI_LOG) {"$e"}
            return false
        }

        override fun onScroll(
            e1: MotionEvent,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            scrolling = true
            return fireScrollEvent(distanceX, distanceY, false)
        }

//        override fun onFling(
//            e1: MotionEvent,
//            e2: MotionEvent,
//            velocityX: Float,
//            velocityY: Float
//        ): Boolean {
//            logger.debug("$e2")
//            if(!consumer.flingEnabled) {
//                return false
//            }
//
//            return try {
//                val diffY = e2.y - e1.y
//                val diffX = e2.x - e1.x
//                if (Math.abs(diffX) > Math.abs(diffY)) {
//                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
//                        if (diffX > 0) {
//                            consumer.onGestureFling(FlingDirection.Right)
//                        } else {
//                            consumer.onGestureFling(FlingDirection.Left)
//                        }
//                    } else false
//                } else if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
//                    if (diffY > 0) {
//                        consumer.onGestureFling(FlingDirection.Down)
//                    } else {
//                        consumer.onGestureFling(FlingDirection.Up)
//                    }
//                } else false
//            } catch (e: Throwable) {
//                TpLib.logger.error(e)
//                false
//            }
//        }
//
//        private val SWIPE_THRESHOLD = 100
//        private val SWIPE_VELOCITY_THRESHOLD = 100
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            logger.debug(GI_LOG) {"${detector.scaleFactor}"}
            return fireScaleEvent(detector.scaleFactor, false)
        }

//        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
//            logger.debug("$detector}")
//            return super.onScaleBegin(detector)
//        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            logger.debug(GI_LOG) { "$detector}" }
            fireScaleEvent(detector.scaleFactor, true)
        }
    }
    companion object {
        const val GI_LOG = false
        val logger: UtLog = UtLog("GI", TpLib.logger, "io.github.toyota32k.secureCamera.utils.")
    }

}


package io.github.toyota32k.secureCamera.utils

import android.content.Context
import android.graphics.PointF
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.View.OnTouchListener
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.LifecycleOwner
import io.github.toyota32k.utils.*

enum class Orientation {
    Horizontal,
    Vertical,
}
enum class Direction {
    Start,
    End
}

class UtGestureInterpreter(
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
        val pivot: PointF?
        val end: Boolean
    }

    val scaleListener: Listeners<IScaleEvent>
        get() = scaleListenerRef.value

    private val scaleListenerRef =
        UtLazyResetableValue<Listeners<IScaleEvent>> { Listeners<IScaleEvent>() }

    private class ScaleEvent(override var scale: Float, override var pivot: PointF?, override var end: Boolean) : IScaleEvent

    private val scaleEvent = ScaleEvent(1f, null, false)
    private fun fireScaleEvent(scale: Float, pivot:PointF?, end: Boolean): Boolean {
        return if (scaleListenerRef.hasValue && scaleListener.count > 0) {
            scaleListener.invoke(scaleEvent.apply {
                this.scale = scale
                this.end = end
                this.pivot = pivot
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
    interface IFlickEvent {
        val direction:Direction
    }
    class FlickEvent(override var direction: Direction) : IFlickEvent
    private val flickEvent = FlickEvent(Direction.Start)
    val hasFlickListeners:Boolean
        get() = hasFlickHorizontalListeners || hasFlickVerticalListeners

    val flickHorizontalListeners: Listeners<IFlickEvent>
        get() = flickHorizontalListenersRef.value
    private val flickHorizontalListenersRef = UtLazyResetableValue { Listeners<IFlickEvent>() }
    private val hasFlickHorizontalListeners
        get() = flickHorizontalListenersRef.hasValue && flickHorizontalListeners.count>0

    private fun fireFlickHorizontalEvent(direction: Direction): Boolean {
        return if (hasFlickHorizontalListeners) {
            flickHorizontalListeners.invoke(flickEvent.apply {
                this.direction = direction
            })
            true
        } else false
    }

    val flickVerticalListeners: Listeners<IFlickEvent>
        get() = flickVerticalListenersRef.value
    private val flickVerticalListenersRef = UtLazyResetableValue { Listeners<IFlickEvent>() }
    private val hasFlickVerticalListeners
        get() = flickVerticalListenersRef.hasValue && flickVerticalListeners.count>0

    private fun fireFlickVerticalEvent(direction: Direction): Boolean {
        return if (hasFlickVerticalListeners) {
            flickVerticalListeners.invoke(flickEvent.apply {
                this.direction = direction
            })
            true
        } else false
    }



    // region Setup Helper

    interface IListenerBuilder {
        fun onScroll(fn: (IScrollEvent)->Unit)
        fun onScale(fn:(IScaleEvent)->Unit)
        fun onTap(fn:(IPositionalEvent)->Unit)
        fun onLongTap(fn:(IPositionalEvent)->Unit)
        fun onDoubleTap(fn:(IPositionalEvent)->Unit)
        fun onFlickHorizontal(fn:(IFlickEvent)->Unit)
        fun onFlickVertical(fn:(IFlickEvent)->Unit)
    }
    private inner class ListenerBuilder:IListenerBuilder {
        private var mScroll:  ((IScrollEvent)->Unit)? = null
        private var mScale:  ((IScaleEvent)->Unit)? = null
        private var mTap: ((IPositionalEvent)->Unit)? = null
        private var mLongTap: ((IPositionalEvent)->Unit)? = null
        private var mDoubleTap: ((IPositionalEvent)->Unit)? = null
        private var mFlickHorizontal: ((IFlickEvent)->Unit)? = null
        private var mFlickVertical: ((IFlickEvent)->Unit)? = null
        override fun onScroll(fn: (IScrollEvent) -> Unit) {
            mScroll = fn
        }

        override fun onScale(fn: (IScaleEvent) -> Unit) {
            mScale = fn
        }

        override fun onTap(fn: (IPositionalEvent) -> Unit) {
            mTap = fn
        }

        override fun onLongTap(fn: (IPositionalEvent) -> Unit) {
            mLongTap = fn
        }

        override fun onDoubleTap(fn: (IPositionalEvent) -> Unit) {
            if(rapidTap) {
                throw IllegalStateException("cannot apply double-tap event listener while rapidTap==true")
            }
            mDoubleTap = fn
        }

        override fun onFlickHorizontal(fn: (IFlickEvent) -> Unit) {
            mFlickHorizontal = fn
        }

        override fun onFlickVertical(fn: (IFlickEvent) -> Unit) {
            mFlickVertical = fn
        }

        fun build(owner:LifecycleOwner) {
            mScroll?.apply {
                scrollListener.add(owner, this)
            }
            mScale?.apply {
                scaleListener.add(owner, this)
            }
            mTap?.apply {
                tapListeners.add(owner, this)
            }
            mLongTap?.apply {
                longTapListeners.add(owner, this)
            }
            mDoubleTap?.apply {
                doubleTapListeners.add(owner, this)
            }
            mFlickHorizontal?.apply {
                flickHorizontalListeners.add(owner, this)
            }
            mFlickVertical?.apply {
                flickVerticalListeners.add(owner, this)
            }
        }
    }
    fun setup(owner:LifecycleOwner, view:View, setupMe:IListenerBuilder.()->Unit) {
        attachView(view)
        ListenerBuilder().apply {
            setupMe()
        }.build(owner)
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

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if(!hasFlickListeners) {
                return false
            }
            logger.debug("$e2")

            return try {
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (hasFlickHorizontalListeners && Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            fireFlickHorizontalEvent(Direction.End)
                        } else {
                            fireFlickHorizontalEvent(Direction.Start)
                        }
                    } else false
                } else {
                    if (hasFlickVerticalListeners && Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            fireFlickVerticalEvent(Direction.End)
                        } else {
                            fireFlickVerticalEvent(Direction.Start)
                        }
                    } else false
                }
            } catch (e: Throwable) {
                logger.error(e)
                false
            }
        }

        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private val pivot = PointF()

        private fun getPivot(detector: ScaleGestureDetector):PointF? {
            return if(detector.isInProgress) {
                pivot.apply {
                    x = detector.focusX
                    y = detector.focusY
                }
            } else null
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            logger.debug(GI_LOG) {"${detector.scaleFactor}"}
            return fireScaleEvent(detector.scaleFactor, getPivot(detector), false)
        }

//        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
//            logger.debug("$detector}")
//            return super.onScaleBegin(detector)
//        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            logger.debug(GI_LOG) { "$detector}" }
            fireScaleEvent(detector.scaleFactor, getPivot(detector),true)
        }
    }
    companion object {
        const val GI_LOG = false
        val logger: UtLog = UtLog("GI", null, UtGestureInterpreter::class.java)
    }

}


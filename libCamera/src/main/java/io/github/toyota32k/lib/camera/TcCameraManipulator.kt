package io.github.toyota32k.lib.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.WeakReferenceDelegate
import io.github.toyota32k.utils.gesture.UtGestureInterpreter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * CameraとPreviewViewに対するタッチジェスチャーをハンドリングするためのクラス。
 * ジェスチャーの解釈には、UtGestureInterpreter を利用。
 *
 * val cameraManipulator = TcCameraManipulator(this, TcCameraManipulator.FocusActionBy.LongTap, rapidTap = false)
 * cameraManipulator.attachCamera(this@CameraActivity, camera, previewView) {
 *    onTap {
 *        takeSnapshot()    // タップで写真撮影
 *    }
 * }
 *
 *
 * @param context       ApplicationContext可（Activityなどを渡しても、applicationContext を取得して保持するのでリークの心配は要らない）
 * @param focusAction   フォーカス合わせにマップするタッチアクションを指定。デフォルト：（PreviewView上の）ダブルタップで、その場所にフォーカスを合わせる。
 * @param rapidTap      true にすると、onSingleTapUp で tapEvent を発行（）。ただし、doubleTapEventは無効になる。
 */
class TcCameraManipulator(context:Context, focusAction:FocusActionBy, rapidTap:Boolean=false) {
    enum class FocusActionBy {
        None,
        Tap,
        LongTap,
        DoubleTap,
    }
    private val focusActionBy = if(rapidTap && focusAction==FocusActionBy.DoubleTap) throw java.lang.IllegalStateException("rapidTap disables doubleTap event.") else focusAction
    private var camera:Camera? by WeakReferenceDelegate()
    private var previewView:PreviewView? by WeakReferenceDelegate()
    private lateinit var gestureScope:CoroutineScope

    val gesture = UtGestureInterpreter(context.applicationContext, enableScaleEvent=true, rapidTap)
    var onFocusdAt:((UtGestureInterpreter.IPositionalEvent)->Unit)? = null

    interface IListenerBuilder {
        fun onTap(fn: (UtGestureInterpreter.IPositionalEvent)->Unit)
        fun onLongTap(fn: (UtGestureInterpreter.IPositionalEvent)->Unit)
        fun onDoubleTap(fn: (UtGestureInterpreter.IPositionalEvent)->Unit)
        fun onFlickHorizontal(fn:(UtGestureInterpreter.IFlickEvent)->Unit)
        fun onFlickVertical(fn:(UtGestureInterpreter.IFlickEvent)->Unit)
        fun onFocusedAt(fn:(UtGestureInterpreter.IPositionalEvent)->Unit)
    }
    private inner class ListenerBuilder:IListenerBuilder {
        var mTap: ((UtGestureInterpreter.IPositionalEvent)->Unit)? = null
        var mLongTap: ((UtGestureInterpreter.IPositionalEvent)->Unit)? = null
        var mDoubleTap: ((UtGestureInterpreter.IPositionalEvent)->Unit)? = null
        var mFlickHorizontal: ((UtGestureInterpreter.IFlickEvent)->Unit)? = null
        var mFlickVertical: ((UtGestureInterpreter.IFlickEvent)->Unit)? = null
        var mFocusedAt: ((UtGestureInterpreter.IPositionalEvent)->Unit)? = null

        override fun onTap(fn: (UtGestureInterpreter.IPositionalEvent)->Unit) {
            if(focusActionBy==FocusActionBy.Tap) {
                throw java.lang.IllegalStateException("tap event is reserved for FocusAction")
            }
            mTap = fn
        }
        override fun onLongTap(fn: (UtGestureInterpreter.IPositionalEvent)->Unit) {
            if(focusActionBy==FocusActionBy.LongTap) {
                throw java.lang.IllegalStateException("longTap event is reserved for FocusAction")
            }
            mLongTap = fn
        }
        override fun onDoubleTap(fn: (UtGestureInterpreter.IPositionalEvent)->Unit) {
            if(focusActionBy==FocusActionBy.DoubleTap || gesture.rapidTap) {
                throw java.lang.IllegalStateException("double event is reserved for FocusAction")
            }
            mDoubleTap = fn
        }
        override fun onFlickHorizontal(fn:(UtGestureInterpreter.IFlickEvent)->Unit) {
            mFlickHorizontal = fn
        }
        override fun onFlickVertical(fn:(UtGestureInterpreter.IFlickEvent)->Unit) {
            mFlickVertical = fn
        }
        override fun onFocusedAt(fn:(UtGestureInterpreter.IPositionalEvent)->Unit) {
            mFocusedAt = fn
        }

        fun build(owner: LifecycleOwner,previewView: PreviewView) {
            if(gesture.rapidTap) {
                mDoubleTap = null
                if(focusActionBy == FocusActionBy.DoubleTap) {
                    throw java.lang.IllegalStateException("FocusActionBy.DoubleTap && rapidTap")
                }
            }
            when(focusActionBy) {
                FocusActionBy.Tap-> mTap = this@TcCameraManipulator::focus
                FocusActionBy.LongTap -> mLongTap = this@TcCameraManipulator::focus
                FocusActionBy.DoubleTap -> mDoubleTap = this@TcCameraManipulator::focus
                else -> {}
            }
            onFocusdAt = mFocusedAt
            gesture.setup(owner, previewView) {
                onScale(this@TcCameraManipulator::zoom)
                mTap?.apply {
                    onTap(this)
                }
                mLongTap?.apply {
                    onLongTap(this)
                }
                mDoubleTap?.apply {
                    onDoubleTap(this)
                }
                mFlickHorizontal?.apply {
                    onFlickHorizontal(this)
                }
                mFlickVertical?.apply {
                    onFlickVertical(this)
                }
            }
        }
    }

    /**
     * カメラ、PreviewView を TcCameraManipulatorに接続する
     * @param owner    ライフサイクルオーナー：イベントリスナーの自動登録解除用
     * @param camera   接続するカメラ（タッチ操作によるズームやフォーカス合わせの対象となる）
     * @param previewView  プレビュービュー（タッチ操作の受け取りとフォーカスポイントの取得に使われる）
     * @param gestureScope  カメラ操作(focus/zoom)用の suspend関数を呼び出すためのスコープ。null なら owner.lifecycleScope を利用する。
     * @param setupMe   リスナー(TcCameraManipulator.IListenerBuilder)構築用関数。UtGestureInterpreter#setup()と似ているが、
     *                   - onScaleは、ズーム操作用に予約されているため設定不可。
     *                   - onTap/onDoubleTap/onLongTap は、focusActionBy の指定により設定が無視される場合がある。
     */
    fun attachCamera(owner: LifecycleOwner, camera: Camera, previewView: PreviewView, gestureScope: CoroutineScope?=null, setupMe:IListenerBuilder.()->Unit):TcCameraManipulator {
        this.camera = camera
        this.previewView = previewView
        this.gestureScope = gestureScope ?: owner.lifecycleScope
        zoomReserved = null

        ListenerBuilder().apply {
            setupMe()
        }.build(owner, previewView)
        return this
    }

    // region Utils

    private fun clip(value:Float, min:Float, max:Float):Float {
        return max(min, min(max, value))
    }

//    private fun quantize(value:Float):Int {
//        return (value*10f).roundToInt()
//    }
    // endregion

    // region Zoom

//    private var rawZoom:Float? = null

//    private fun nextZoom(zoomState: ZoomState?, scaleFactor:Float):Float {
//        if(zoomState==null) return 1f
//        val zoom = (rawZoom ?: zoomState.zoomRatio)*scaleFactor
//        rawZoom = zoom
//        val newValue = quantize(clip(zoom, zoomState.minZoomRatio, zoomState.maxZoomRatio))
//        return if(newValue == quantize(zoomState.zoomRatio)) 0f else (newValue.toFloat())/10f
//    }

//    private val isBusy = AtomicBoolean(false)

    var zoomReserved:Float? = null
    private fun zoom(event: UtGestureInterpreter.IScaleEvent) {
        val camera = this.camera ?: return
        val zoomState = camera.cameraInfo.zoomState.value ?: return
//        if(!isBusy.compareAndSet(false, true)) return
        val zoom = clip((zoomReserved ?: zoomState.zoomRatio)*event.scale, zoomState.minZoomRatio, zoomState.maxZoomRatio)
//        logger.debug("current:${zoomState.zoomRatio} x scale:${event.scale} = ${zoomState.zoomRatio*event.scale} / min:${zoomState.minZoomRatio}-max:${zoomState.maxZoomRatio} -> $zoom")
        zoomReserved = zoom
        gestureScope.launch {
            try {
                camera.cameraControl.setZoomRatio(zoom).await()
            } catch(_: CameraControl.OperationCanceledException) {
                logger.debug("zoom operation cancelled.")
            } catch(e:Throwable) {
                logger.error(e)
            }
//            finally {
//                isBusy.set(false)
//            }
        }
    }

    private fun focus(event: UtGestureInterpreter.IPositionalEvent) {
        val previewView = previewView ?: return
        val camera = camera ?: return
        val meteringPointFactory = previewView.meteringPointFactory
        val focusPoint = meteringPointFactory.createPoint(event.x, event.y)
        val meteringAction = FocusMeteringAction.Builder(focusPoint).disableAutoCancel().build()
        gestureScope.launch {
            try {
                camera.cameraControl.startFocusAndMetering(meteringAction).await()
                onFocusdAt?.invoke(event)
            } catch(e:Throwable) {
                logger.error(e)
            }
        }
    }

    companion object {
        val logger:UtLog = UtLog("CameraMani", null, TcCameraManipulator::class.java)
    }
}
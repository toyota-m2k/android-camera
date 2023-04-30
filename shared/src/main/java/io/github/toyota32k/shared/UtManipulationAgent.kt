package io.github.toyota32k.secureCamera.utils

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Matrix
import android.graphics.PointF
import android.view.View
import io.github.toyota32k.lib.player.common.UtFitter
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Float.min
import java.util.EnumSet
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.*

/**
 * スクロール/ズームの対象（View）に関する情報
 */
interface IUtManipulationTarget {
    val parentView:View      // contentViewのコンテナ（通常、このビューがタッチイベントを受け取る-->GestureInterrupter に attachViewする）
    val contentView:View     // 移動/拡大するビュー : containerView 上にセンタリングされて配置されることを想定

    val parentWidth:Int
        get() = parentView.width
    val parentHeight:Int
        get() = parentView.height

    /**
     * コンテントのサイズ
     * contentViewが wrap_content なら、contentWidth/Height は、contentView.width/height に一致するが、
     * scaleType=fitCenter の ImageViewなど、ビューのサイズとスクロール/ズーム対象が異なる場合は、真のコンテントのサイズを返すようオーバーライドする。
     */
    val contentWidth:Int
        get() = contentView.width
    val contentHeight:Int
        get() = contentView.height

    /**
     * びよーんってなる量を親フレームに対する比率で指定
     * 0 なら可動範囲でスクロールをストップする。
     */
    val overScrollX:Float
    val overScrollY:Float


    // region ページめくり

    val pageOrientation: EnumSet<Orientation>

    /**
     * overScrollX/Y != 0 の場合、限界まで達した状態でタッチをリリースしたときに呼び出すので、ページ移動処理を行う。
     * @return true     移動した（続くページ切り替えアニメーションを実行）
     * @return false    移動しなかった（びよーんと戻す）
     */
    fun changePage(orientation: Orientation, dir:Direction):Boolean
    // 指定方向に次のページはあるか？
    fun hasNextPage(orientation: Orientation, dir:Direction):Boolean

    // endregion
}

/**
 * スクロール / ズーム操作をシンプルに実現したい
 */
class UtManipulationAgent   (val targetViewInfo:IUtManipulationTarget) {
    var minScale:Float = 1f
    var maxScale:Float = 10f

    val contentView:View
        get() = targetViewInfo.contentView
    val contentWidth:Int
        get() = targetViewInfo.contentWidth
    val contentHeight:Int
        get() = targetViewInfo.contentHeight

    val parentView:View
        get() = targetViewInfo.parentView
    val parentWidth:Int
        get() = targetViewInfo.parentWidth
    val parentHeight:Int
        get() = targetViewInfo.parentHeight

    var scale:Float
        get() = contentView.scaleX
        set(v) {
            contentView.scaleX = v
            contentView.scaleY = v
        }

//    fun setPivot(pivot: PointF?) {
//        pivot?:return
//        contentView.pivotX = pivot.x  // / contentView.width.toFloat()
//        contentView.pivotY = pivot.y  // contentView.height.toFloat()
//    }

    var translationX:Float
        get() = contentView.translationX
        set(v) { contentView.translationX = v }

    var translationY:Float
        get() = contentView.translationY
        set(v) { contentView.translationY = v }

    val fitter = UtFitter()


    /**
     * 現在、表示しているサイズ
     */
    val actualWidth:Float
        get() = contentWidth*scale
    val actualHeight:Float
        get() = contentHeight*scale


    /**
     * 可動範囲
     * 親ビューからはみ出している部分だけ移動できる。
     * コンテントはセンタリングされている前提だから、絶対値で、はみ出している量/2だけ上下左右に移動可能。
     */
    val movableX:Float
        get() = max(0f,(actualWidth - parentWidth)/2f)
    val movableY:Float
        get() = max(0f,(actualHeight - parentHeight)/2f)

    val overScrollX:Float
        get() = targetViewInfo.overScrollX*parentWidth
    val overScrollY:Float
        get() = targetViewInfo.overScrollY*parentHeight

    /**
     * スクロール時の移動量を計算する。
     */
    private fun calcTranslation(d:Float, movable:Float, overScroll:Float):Float {
        val ad = abs(d)
        val d2 = if(ad>movable) {
            // 可動範囲を超えている
            if (overScroll<=0) {    // オーバースクロール禁止
                movable             // --> 可動範囲でクリップ
            } else if (ad>=movable+overScroll) { // 可動範囲 + オーバースクロール量を超えている
                movable+overScroll              // --> 可動範囲 + オーバースクロール量 でクリップ
            } else {
                // 可動範囲 ～ オーバースクロールの限界までの間
                // 限界に近づくほど、動きを遅くする（-->少し抵抗がある感じにしてみる）
                val over = ad - movable
                logger.assert(0<over && over<overScroll)
                val corr = (over/overScroll).pow(0.1f)
                movable + min(overScroll, over*corr)
            }
        } else {
            // 可動範囲内 --> 自由に移動
            ad
        }
        return d2 * sign(d)
    }

    /**
     * スクロール処理
     */
    fun onScroll(p:UtGestureInterpreter.IScrollEvent) {
//        if(changingPageNow) {
//            // ページ切り替えアニメーション中は次の操作を止める
//            return
//        }
//        translationX = calcTranslation(translationX-p.dx, movableX, overScrollX)
//        translationY = calcTranslation(translationY-p.dy, movableY, overScrollY)
//        if(pageChangeAction()) {
//            return
//        }
//        if(p.end) {
//            logger.debug("$movableX, $overScrollX")
//            onManipulationComplete()
//        }
    }

    /**
     * 移動量を可動範囲（＋オーバースクロール範囲）でクリップする
     */
    private fun clipTranslation(translation:Float, movable:Float, overScroll: Float):Float {
        val ad = abs(translation)
        return min(movable+overScroll, ad) * sign(translation)
    }

//    var prevPivotX:Float = 0f
//    var prevPivotY:Float = 0f
    var prevTx:Float=0f
    var prevTy:Float=0f
    var prevScale = 1f
    var prevPx:Float=0f
    var prevPy:Float=0f

    private fun Matrix.mapPoint(p:PointF) {
        val points = floatArrayOf(p.x, p.y)
        mapPoints(points)
        p.x = points[0]
        p.y = points[1]
    }
    private fun Matrix.mapPoint(x:Float,y:Float):Pair<Float,Float> {
        val points = floatArrayOf(x,y)
        mapPoints(points)
        return Pair(points[0], points[1])
    }
    /**
     * ズーム処理
     */
    fun onScale(p:UtGestureInterpreter.IScaleEvent) {
        if(changingPageNow) {
            // ページ切り替えアニメーション中は次の操作を止める
            return
        }
        val pivot = p.pivot ?: return
        logger.info("before: scale=$scale, tx=$translationX, ty=$translationY px=${contentView.pivotX}, py=${contentView.pivotY} -> px2=${pivot.x}, px2=${pivot.y}")

//        contentView.pivotX = 0f
//        contentView.pivotY = 0f

//        val m = Matrix().apply {
//            postScale(scale, scale, 0f, 0f)
//            postTranslate(translationX, translationY)
//        }
//        val rect = RectF(0f,0f,contentWidth.toFloat(), contentHeight.toFloat())
//        logger.info("before: $pivot  ($rect)")
//        val points = floatArrayOf(pivot.x, pivot.y)
//        m.mapPoints(points)
//        m.mapRect(rect)
//        val px = points[0]
//        val py = points[1]
//        logger.info("after : ${PointF(px, py)}  ($rect)")
        val s0 = scale
        val s1 = max(minScale, min(maxScale, scale * p.scale))
//

//        val px = -translationX + pivot.x
//        val py = -translationY + pivot.y
//
//        val dx = px * (s1-s0)
//        val dy = py * (s1-s0)
////
//        logger.info("s1-s0 = ${s1-s0}, dx=$dx, dy=$dy")
//        translationX -= dx
//        translationY -= dy


    //        setPivot(p.pivot)
//        scale = max(minScale, min(maxScale, scale * p.scale))

        when(p.timing) {
            Timing.Start -> {
                contentView.pivotX = 0f
                contentView.pivotY = 0f
                logger.info("start : scale=$scale, tx=$translationX, ty=$translationY px=${contentView.pivotX}, py=${contentView.pivotY}")
            }

            Timing.Repeat ->{
                val m1 = Matrix().run {
                    postTranslate(translationX, translationY)
                    postScale(scale, scale, 0f, 0f)
                    Matrix().also {
                        invert(this)
                    }
                }
                val px1 = pivot.x - translationX
                val py1 = pivot.y - translationY
                val (rx,ry) = m1.mapPoint(px1,py1)

                val m2 = Matrix().apply {
                    postTranslate(translationX,translationY)
                    postScale(s1, s1, 0f, 0f)
                }
                val (px2,py2) = m2.mapPoint(rx,ry)

                scale = s1
                translationX -= (px2 - px1)
                translationY -= (py2 - py1)

                logger.info("after : scale=$scale, tx=$translationX, ty=$translationY px=${contentView.pivotX}, py=${contentView.pivotY}")

            }
            Timing.End->{

                CoroutineScope(Dispatchers.Main).launch {
                    delay(1000)
                    logger.info("end   : scale=$scale, tx=${translationX}, ty={$translationY} px=${contentView.pivotX}, py=${contentView.pivotY}")

                }
            }
        }


    }

    /**
     * スクロールのアニメーション
     */
    class AnimationHandler : ValueAnimator.AnimatorUpdateListener, Animator.AnimatorListener {
        private var currentUpdater:((Float)->Unit)? = null
        private var animating:Continuation<Boolean>? = null

        // region AnimatorUpdateListener
        override fun onAnimationUpdate(animation: ValueAnimator) {
            currentUpdater?.invoke(animation.animatedValue as Float)
        }

        // endregion

        // region AnimatorListener
        override fun onAnimationStart(animation: Animator) {
        }
        override fun onAnimationEnd(animation: Animator) {
            currentUpdater = null
            animating?.resume(true)
            animating = null
        }
        override fun onAnimationCancel(animation: Animator) {
            animating?.resume(false)
            animating = null
        }
        override fun onAnimationRepeat(animation: Animator) {
        }

        // endregion

        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration=150
            addUpdateListener(this@AnimationHandler)
            addListener(this@AnimationHandler)
        }

        /**
         * アニメーション開始（終了を待つ）
         */
        suspend fun suspendStart(duration:Long, update:(Float)->Unit):Boolean {
            currentUpdater = update
            animator.duration = duration
            return suspendCoroutine<Boolean> {
                animating = it
                animator.start()
            }
        }

        /**
         * アニメーション開始（やりっぱなし）
         */
        fun start(duration:Long, update: (Float) -> Unit) {
            currentUpdater = update
            animator.duration = duration
            animator.start()
        }

    }

    private val animationHandler = AnimationHandler()
    private var changingPageNow:Boolean = false


    private fun pageChangeActionSub(orientation: Orientation):Boolean {
        if(!targetViewInfo.pageOrientation.contains(orientation)) return false

        val c:Float
        val movable:Float
        val overScroll:Float
        val contentSize:Float
        if(orientation==Orientation.Horizontal) {
            c = translationX
            movable = movableX
            overScroll = overScrollX
            contentSize = actualWidth
        } else {
            c = translationY
            movable = movableY
            overScroll = overScrollY
            contentSize = actualHeight
        }
        if(abs(c)==movable+overScroll) {
            val direction = if(c>0) Direction.Start else Direction.End
            if(targetViewInfo.hasNextPage(orientation, direction)) {
                if(orientation==Orientation.Horizontal) translationY = 0f else translationX = 0f
                changingPageNow = true
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val slideOut = (contentSize - abs(c)) * sign(c)
                        val slideOutUpdater:(Float)->Unit = if(orientation==Orientation.Horizontal) {
                            { ratio -> translationX = c + slideOut * ratio }
                        } else {
                            { ratio -> translationY = c + slideOut * ratio }
                        }
                        animationHandler.suspendStart(150, slideOutUpdater)
                        if (targetViewInfo.changePage(orientation, direction)) {
                            scale = 1f
                            val slideIn = -contentSize * sign(c)
                            if(orientation==Orientation.Horizontal) translationX = slideIn else translationY = slideIn
                            val slideInUpdater:(Float)->Unit = if(orientation==Orientation.Horizontal) {
                                {ratio -> translationX = slideIn - slideIn * ratio}
                            } else {
                                {ratio -> translationY = slideIn - slideIn * ratio}
                            }
                            animationHandler.suspendStart(150, slideInUpdater)
                        }
                    } finally {
                        changingPageNow = false
                        translationX = 0f
                        translationY = 0f
                        scale = 1f
                    }
                }
                return true
            }
        }
        return false
    }

    /**
     * アクション付きでページ切り替えを実行
     */
    private fun pageChangeAction():Boolean {
        return pageChangeActionSub(Orientation.Horizontal) || pageChangeActionSub(Orientation.Vertical)
//
//
//        if(targetViewInfo.pageOrientation.contains(Orientation.Horizontal)) {
//            val cx = translationX
//            if(abs(cx)==movableX+overScrollX) {
//                val direction = if(cx>0) Direction.Start else Direction.End
//                if(targetViewInfo.hasNextPage(Orientation.Horizontal, direction)) {
//                    translationY = 0f
//                    changingPageNow = true
//                    CoroutineScope(Dispatchers.Main).launch {
//                        try {
//                            val slideOut = (contentWidth - abs(cx)) * sign(cx)
//                            animationHandler.suspendStart(150) {
//                                translationX = cx + slideOut * it
//                            }
//                            if (targetViewInfo.changePage(Orientation.Horizontal, direction)) {
//                                scale = 1f
//                                val slideIn = -contentWidth * sign(cx)
//                                translationX = slideIn
//                                animationHandler.suspendStart(150) {
//                                    translationX = slideIn - slideIn * it
//                                }
//                            }
//                        } finally {
//                            changingPageNow = false
//                            translationX = 0f
//                            translationY = 0f
//                            scale = 1f
//                        }
//                    }
//                    return true
//                }
//            }
//        }
//        return false
    }

    /**
     * スクロールの終了（<--指を離した）
     * ホーム位置（　 translation == 0 )に戻す
     */
    private fun onManipulationComplete() {
        val cx = translationX
        val cy = translationY

        val tx = clipTranslation(translationX,movableX,0f)
        val ty = clipTranslation(translationY,movableY,0f)
        animationHandler.start(150) {
            translationX = cx + (tx-cx)*it
            translationY = cy + (ty-cy)*it
        }
    }

    fun resetScrollAndScale():Boolean {
        return if(!changingPageNow) {
            translationY = 0f
            translationX = 0f
            scale = 1f
            true
        } else false
    }

    companion object {
        val logger = UtLog("SZC", null, this::class.java)
    }

}
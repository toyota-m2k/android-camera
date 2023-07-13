package io.github.toyota32k.shared.gesture

import android.animation.Animator
import android.animation.ValueAnimator
import android.view.View
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Float.min
import java.util.EnumSet
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.*

/**
 * parentView, contentView が動的に変化しない単純な ManipulationTarget
 */
@Suppress("unused")
open class UtSimpleManipulationTarget(
    override val parentView: View,
    override val contentView: View,
    override val overScrollX: Float = 0f,
    override val overScrollY: Float = 0f,
    override val pageOrientation: EnumSet<Orientation> = EnumSet.noneOf(Orientation::class.java)
) : IUtManipulationTarget {
    override fun changePage(orientation: Orientation, dir: Direction): Boolean {
        return false
    }

    override fun hasNextPage(orientation: Orientation, dir: Direction): Boolean {
        return false
    }
}

/**
 * スクロール / ズーム操作をカプセル化するクラス
 */
class UtManipulationAgent(private val targetViewInfo: IUtManipulationTarget) {
    private var minScale:Float = 1f
    private var maxScale:Float = 10f

    @Suppress("MemberVisibilityCanBePrivate")
    val contentView:View
        get() = targetViewInfo.contentView
    private val contentWidth:Int
        get() = targetViewInfo.contentWidth
    private val contentHeight:Int
        get() = targetViewInfo.contentHeight

    @Suppress("MemberVisibilityCanBePrivate")
    val parentView:View
        get() = targetViewInfo.parentView
    private val parentWidth:Int
        get() = targetViewInfo.parentWidth
    private val parentHeight:Int
        get() = targetViewInfo.parentHeight

    private var scale:Float
        get() = contentView.scaleX
        set(v) {
            contentView.scaleX = v
            contentView.scaleY = v
        }

    private var translationX:Float
        get() = contentView.translationX
        set(v) { contentView.translationX = v }

    private var translationY:Float
        get() = contentView.translationY
        set(v) { contentView.translationY = v }

    /**
     * 現在、表示しているサイズ
     */
    private val scaledWidth:Float
        get() = contentWidth*scale
    private val scaledHeight:Float
        get() = contentHeight*scale

    /**
     * 可動範囲
     * 親ビューからはみ出している部分だけ移動できる。
     * contentViewがparentViewに対してセンタリングされている状態を基準として、絶対値で、はみ出している量/2だけ上下左右に移動可能。
     */
    private val movableX:Float
        get() = max(0f,(scaledWidth - parentWidth)/2f)
    private val movableY:Float
        get() = max(0f,(scaledHeight - parentHeight)/2f)

    private val overScrollX:Float
        get() = targetViewInfo.overScrollX*parentWidth
    private val overScrollY:Float
        get() = targetViewInfo.overScrollY*parentHeight


    private var prevParentWidth:Int  = 0
    private var prevParentHeight:Int  = 0

    init {
//        contentView.pivotX = 0f
//        contentView.pivotY = 0f

        // 親ビューのサイズが変わったら、可動範囲も変わるので、スクロール位置が不正になるので、スクロール位置はリセットする
        parentView.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val w = right - left
            val h = bottom - top
            if(prevParentWidth!=w||prevParentHeight!=h) {
                prevParentWidth = w
                prevParentHeight = h
                resetScroll()
            }
        }
    }

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
     * 移動量を可動範囲（＋オーバースクロール範囲）でクリップする
     */
    private fun clipTranslation(translation:Float, movable:Float, @Suppress("SameParameterValue") overScroll: Float):Float {
        val ad = abs(translation)
        return min(movable+overScroll, ad) * sign(translation)
    }


    private var scaling: Boolean = false

    /**
     * スクロール処理
     */
    fun onScroll(p: UtGestureInterpreter.IScrollEvent) {
        if(changingPageNow) {
            // ページ切り替えアニメーション中は次の操作を止める
            return
        }
        translationX = calcTranslation(translationX-p.dx, movableX, overScrollX)
        translationY = calcTranslation(translationY-p.dy, movableY, overScrollY)
        if(pageChangeAction()) {
            return
        }
        if(p.end) {
            logger.debug("$movableX, $overScrollX")
            onManipulationComplete()
        }
    }

//    private fun Matrix.mapPoint(p:PointF) {
//        val points = floatArrayOf(p.x, p.y)
//        mapPoints(points)
//        p.x = points[0]
//        p.y = points[1]
//    }
//    private fun Matrix.mapPoint(x:Float,y:Float):Pair<Float,Float> {
//        val points = floatArrayOf(x,y)
//        mapPoints(points)
//        return Pair(points[0], points[1])
//    }
    /**
     * ズーム処理
     */
    fun onScale(p: UtGestureInterpreter.IScaleEvent) {
        if(changingPageNow) {
            // ページ切り替えアニメーション中は次の操作を止める
            return
        }
        val pivot = p.pivot ?: return
        val s1 = max(minScale, min(maxScale, scale * p.scale))

        when(p.timing) {
            Timing.Start -> {
                scaling = true
                logger.info("start : scale=$scale, tx=$translationX, ty=$translationY px=${contentView.pivotX}, py=${contentView.pivotY}")
            }

            Timing.Repeat ->{
                val px1 = -translationX + (pivot.x-0.5f*contentWidth)
                val py1 = -translationY + (pivot.y-0.5f*contentHeight)
                val px2 = px1*s1/scale
                val py2 = py1*s1/scale
                val dx = px2 - px1
                val dy = py2 - py1

//                val px1 = -translationX + pivot.x
//                val py1 = -translationY + pivot.y
//                val px2 = px1/scale * s1
//                val py2 = py1/scale * s1
//                val dx = px2 - px1
//                val dy = py2 - py1
                translationX -= dx
                translationY -= dy
                scale = s1
            }
            Timing.End->{
//                onManipulationComplete()
                scaling = false
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
            return suspendCoroutine {
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
        if(orientation== Orientation.Horizontal) {
            c = translationX
            movable = movableX
            overScroll = overScrollX
            contentSize = scaledWidth
        } else {
            c = translationY
            movable = movableY
            overScroll = overScrollY
            contentSize = scaledHeight
        }
        if(abs(c)==movable+overScroll) {
            val direction = if(c>0) Direction.Start else Direction.End
            if(targetViewInfo.hasNextPage(orientation, direction)) {
                if(orientation== Orientation.Horizontal) translationY = 0f else translationX = 0f
                changingPageNow = true
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val slideOut = (contentSize - abs(c)) * sign(c)
                        val slideOutUpdater:(Float)->Unit = if(orientation== Orientation.Horizontal) {
                            { ratio -> translationX = c + slideOut * ratio }
                        } else {
                            { ratio -> translationY = c + slideOut * ratio }
                        }
                        animationHandler.suspendStart(150, slideOutUpdater)
                        if (targetViewInfo.changePage(orientation, direction)) {
                            scale = 1f
                            val slideIn = -contentSize * sign(c)
                            if(orientation== Orientation.Horizontal) translationX = slideIn else translationY = slideIn
                            val slideInUpdater:(Float)->Unit = if(orientation== Orientation.Horizontal) {
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
        if(scaling) return false       // ピンチ操作中はページ変更禁止
        return pageChangeActionSub(Orientation.Horizontal) || pageChangeActionSub(Orientation.Vertical)
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

    fun resetScroll() : Boolean {
        return if(!changingPageNow) {
            translationY = 0f
            translationX = 0f
            true
        } else false
    }

    companion object {
        val logger = UtLog("SZC", null, this::class.java)
    }

}
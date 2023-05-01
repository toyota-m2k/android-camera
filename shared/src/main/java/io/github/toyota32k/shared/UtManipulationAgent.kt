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

abstract class UtSimpleManipulationTarget(
    override val parentView: View,
    override val contentView: View,
    override val overScrollX: Float,
    override val overScrollY: Float,
    override val pageOrientation: EnumSet<Orientation>
) : IUtManipulationTarget {
}

/**
 * スクロール / ズーム操作をカプセル化するクラス
 */
class UtManipulationAgent(val targetViewInfo:IUtManipulationTarget) {
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

    var translationX:Float
        get() = contentView.translationX
        set(v) { contentView.translationX = v }

    var translationY:Float
        get() = contentView.translationY
        set(v) { contentView.translationY = v }

    /**
     * 現在、表示しているサイズ
     */
    val scaledWidth:Float
        get() = contentWidth*scale
    val scaledHeight:Float
        get() = contentHeight*scale

    /**
     * 可動範囲
     * 親ビューからはみ出している部分だけ移動できる。
     * contentViewがparentViewに対してセンタリングされている状態を基準として、絶対値で、はみ出している量/2だけ上下左右に移動可能。
     */
    val movableX:Float
        get() = max(0f,(scaledWidth - parentWidth)/2f)
    val movableY:Float
        get() = max(0f,(scaledHeight - parentHeight)/2f)

    val overScrollX:Float
        get() = targetViewInfo.overScrollX*parentWidth
    val overScrollY:Float
        get() = targetViewInfo.overScrollY*parentHeight


    private var prevParentWidth:Int  = 0
    private var prevParentHeight:Int  = 0

    init {
        contentView.pivotX = 0f
        contentView.pivotY = 0f

        // 親ビューのサイズが変わったら、可動範囲も変わるので、スクロール位置が不正になる。
        // 選択肢としては、１）リセットする、２）クリップする、が考えられるが、２）に意味があるかどうか微妙なので、潔くリセットする。
        parentView.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val w = right - left
            val h = bottom - top
            if(prevParentWidth!=w||prevParentHeight!=h) {
                prevParentWidth = w
                prevParentHeight = h
                resetScrollAndScale()
            }
        }
    }

    /**
     * スクロール時の移動量を計算する。
     */
    private fun calcTranslation(newTranslation:Float, movable:Float, overScroll:Float):Float {
        // pivot=0としているから、translation は View左上の座標と一致し、-movable*2 ～ 0 の値をとる。
        // そこで、+movable して、中央に配置されたときの translationを基準に移動量を計算する。
        val d = newTranslation + movable
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
        return d2 * sign(d) - movable   // 最初にはかせた下駄をぬがせる。
    }

    /**
     * 移動量を可動範囲（＋オーバースクロール範囲）でクリップする
     */
    private fun clipTranslation(translation:Float, movable:Float, overScroll: Float):Float {
        val d = translation+movable
        val ad = abs(d)
        return min(movable+overScroll, ad) * sign(d) - movable
    }


    var scaling: Boolean = false

    /**
     * スクロール処理
     */
    fun onScroll(p:UtGestureInterpreter.IScrollEvent) {
        if(changingPageNow) {
            // ページ切り替えアニメーション中は次の操作を止める
            return
        }
        contentView.pivotX = 0f
        contentView.pivotY = 0f
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
        val s1 = max(minScale, min(maxScale, scale * p.scale))

        when(p.timing) {
            Timing.Start -> {

                contentView.pivotX = 0f
                contentView.pivotY = 0f
                scaling = true

                logger.info("start : scale=$scale, tx=$translationX, ty=$translationY px=${contentView.pivotX}, py=${contentView.pivotY}")
            }

            Timing.Repeat ->{
                val px1 = -translationX + pivot.x
                val py1 = -translationY + pivot.y
                val px2 = px1/scale * s1
                val py2 = py1/scale * s1
                val dx = px2 - px1
                val dy = py2 - py1
                translationX -= dx
                translationY -= dy
                scale = s1
            }
            Timing.End->{
                onManipulationComplete()
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
            c = translationX + movableX
            movable = movableX
            overScroll = overScrollX
            contentSize = scaledWidth
        } else {
            c = translationY + movableY
            movable = movableY
            overScroll = overScrollY
            contentSize = scaledHeight
        }
        if(abs(c)==movable+overScroll) {
            val direction = if(c>0) Direction.Start else Direction.End
            if(targetViewInfo.hasNextPage(orientation, direction)) {
                if(orientation==Orientation.Horizontal) translationY = -movableX else translationX = -movableY
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

    companion object {
        val logger = UtLog("SZC", null, this::class.java)
    }

}
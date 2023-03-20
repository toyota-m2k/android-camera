package io.github.toyota32k.secureCamera.utils

import android.animation.Animator
import android.animation.ValueAnimator
import android.view.View
import io.github.toyota32k.lib.player.common.TpFitterEx
import io.github.toyota32k.utils.UtLog
import java.lang.Float.min
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

enum class Orientation {
    Horizontal,
    Vertical,
    Both,
}
enum class Direction {
    Start,
    End
}

interface ITargetViewInfo {
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

    val pageOrientation:Orientation

    /**
     * overScrollX/Y != 0 の場合、限界まで達した状態でタッチをリリースしたときに呼び出すので、ページ移動処理を行う。
     * @return true     移動した（続くページ切り替えアニメーションを実行）
     * @return false    移動しなかった（びよーんと戻す）
     */
    fun changePage(orientation: Orientation, dir:Direction):Boolean
}

class ScrollZoomAgent(val targetViewInfo:ITargetViewInfo) {
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

    val fitter = TpFitterEx()


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

    private fun calcTranslation(d:Float, movable:Float, overScroll:Float):Float {
        val ad = abs(d)
        val d2 = if(ad>movable) {
            if (overScroll<=0) {
                movable
            } else if (ad>movable+overScroll) {
                movable+overScroll
            } else {
                val over = ad - movable
                logger.assert(0<over && over<overScroll)
                val corr = (1f-over/overScroll) //.pow(2)
                movable + min(overScroll, over*corr)
            }
        } else {
            ad
        }
        return d2 * sign(d)
    }

    fun onScroll(p:GestureInterpreter.IScrollEvent) {
        translationX = calcTranslation(translationX-p.dx, movableX, overScrollX)
        translationY = calcTranslation(translationY-p.dy, movableY, overScrollY)
        if(p.end) {
            logger.debug("$movableX, $overScrollX")
            onManipulationComplete()
        }
    }

    fun adjustTranslation(translation:Float,movable:Float,overScroll: Float):Float {
        val ad = abs(translation)
        return min(movable+overScroll, ad) * sign(translation)
    }

    fun onScale(p:GestureInterpreter.IScaleEvent) {
        scale = max(minScale, min(maxScale, scale * p.scale))
//        translationX = adjustTranslation(translationX,movableX,overScrollX)
//        translationY = adjustTranslation(translationY,movableX,overScrollY)

    }

    class AnimationHandler : ValueAnimator.AnimatorUpdateListener, Animator.AnimatorListener {
        var callback: ((Float)->Unit)? = null
        override fun onAnimationUpdate(animation: ValueAnimator) {
            callback?.invoke(animation.animatedValue as Float)
        }

        override fun onAnimationStart(animation: Animator) {
        }

        override fun onAnimationEnd(animation: Animator) {
            callback = null
        }

        override fun onAnimationCancel(animation: Animator) {
            callback = null
        }

        override fun onAnimationRepeat(animation: Animator) {
        }

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration=300
            addUpdateListener(this@AnimationHandler)
            addListener(this@AnimationHandler)
        }

        fun start(update:(Float)->Unit) {
            callback = update
            animator.start()
        }
    }
    val animationHandler = AnimationHandler()

    fun onManipulationComplete() {
        val cx = translationX
        val cy = translationY
        val tx = adjustTranslation(translationX,movableX,0f)
        val ty = adjustTranslation(translationY,movableY,0f)
        animationHandler.start {
            translationX = cx + (tx-cx)*it
            translationY = cy + (ty-cy)*it
        }
    }

    companion object {
        val logger = UtLog("SZC", null, this::class.java)
    }

}
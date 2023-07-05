package io.github.toyota32k.shared.gesture

import android.view.View
import java.util.EnumSet

/**
 * スクロール/ズームの対象（View）に関する情報
 */
interface IUtManipulationTarget {
    val parentView: View      // contentViewのコンテナ（通常、このビューがタッチイベントを受け取る-->GestureInterrupter に attachViewする）
    val contentView: View     // 移動/拡大するビュー : containerView 上にセンタリングされて配置されることを想定

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
    fun changePage(orientation: Orientation, dir: Direction):Boolean
    // 指定方向に次のページはあるか？
    fun hasNextPage(orientation: Orientation, dir: Direction):Boolean

    // endregion
}

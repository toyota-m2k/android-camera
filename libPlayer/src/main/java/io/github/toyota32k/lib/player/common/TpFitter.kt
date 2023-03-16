/**
 * 矩形サイズをルールに従って配置するためのサイズ決定ロジックの実装
 *
 * @author M.TOYOTA 2018.07.11 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */

package io.github.toyota32k.lib.player.common

import android.util.Size
import android.util.SizeF
import io.github.toyota32k.lib.player.TpLib

/**
 * 矩形領域のリサイズ方法
 */
enum class FitMode {
    Width,       // 指定された幅になるように高さを調整
    Height,      // 指定された高さになるよう幅を調整
    Inside,      // 指定された矩形に収まるよう幅、または、高さを調整
    Fit          // 指定された矩形にリサイズする
}

/**
 * ビデオや画像のサイズ(original)を、指定されたmode:FitModeに従って、配置先のサイズ(layout)に合わせるように変換して resultに返す。
 *
 * @param original  元のサイズ（ビデオ/画像のサイズ）
 * @param layout    レイアウト先の指定サイズ
 * @param result    結果を返すバッファ
 */
fun fitSizeTo(originalWidth:Float, originalHeight:Float, layoutWidth:Float, layoutHeight:Float, mode: FitMode, result: MuSize) {
    try {
        when (mode) {
            FitMode.Fit -> result.set(layoutWidth, layoutHeight)
            FitMode.Width -> result.set(layoutWidth, originalHeight * layoutWidth / originalWidth)
            FitMode.Height -> result.set(originalWidth * layoutHeight / originalHeight, layoutHeight)
            FitMode.Inside -> {
                val rw = layoutWidth / originalWidth
                val rh = layoutHeight / originalHeight
                if (rw < rh) {
                    result.set(layoutWidth, originalHeight * rw)
                } else {
                    result.set(originalWidth * rh, layoutHeight)
                }
            }
        }
    } catch(e:Exception) {
        TpLib.logger.stackTrace(e)
        result.set(0f,0f)
    }
}
fun fitSizeTo(original: MuSize, layout: MuSize, mode: FitMode, result: MuSize) = fitSizeTo(original.width, original.height, layout.width, layout.height, mode, result)

interface IAmvLayoutHint {
    val fitMode: FitMode
    val layoutWidth: Float
    val layoutHeight: Float
}

open class AmvFitter(override var fitMode: FitMode = FitMode.Inside, protected var layoutSize: MuSize = MuSize(1000f, 1000f)) :
    IAmvLayoutHint {
    override val layoutWidth: Float
        get() = layoutSize.width
    override val layoutHeight: Float
        get() = layoutSize.height


    fun setHint(fitMode: FitMode, width:Float, height:Float) {
        this.fitMode = fitMode
        layoutSize.width = width
        layoutSize.height = height
    }

    fun fit(original: MuSize, result: MuSize) {
        fitSizeTo(original, layoutSize, fitMode, result)
    }

    fun fit(w:Float, h:Float): ImSize {
        val result = MuSize()
        fit(MuSize(w,h), result)
        return result
    }
}

class AmvFitterEx(override var fitMode: FitMode, override var layoutWidth:Float, override var layoutHeight:Float) :
    IAmvLayoutHint {
    constructor():this(FitMode.Inside, 1f, 1f)
    constructor(fitMode: FitMode):this(fitMode, 1f,1f)
    constructor(fitMode: FitMode, layoutWidth:Int, layoutHeight:Int):this(fitMode,layoutWidth.toFloat(), layoutHeight.toFloat())

    val result = MuSize()
    val resultSize:Size
        get() = result.asSize
    val resultSizeF:SizeF
        get() = result.asSizeF
    val resultWidth = result.width
    val resultHeight = result.height

    fun setMode(fitMode: FitMode) {
        this.fitMode = fitMode
    }

    fun setLayoutWidth(width:Float): AmvFitterEx {
        this.layoutWidth = width
        return this
    }
    fun setLayoutWidth(width:Int): AmvFitterEx {
        this.layoutWidth = width.toFloat()
        return this
    }

    fun setLayoutHeight(height:Float): AmvFitterEx {
        this.layoutHeight = height
        return this
    }
    fun setLayoutHeight(height:Int): AmvFitterEx {
        this.layoutHeight = height.toFloat()
        return this
    }

    fun fit(src: Size): AmvFitterEx {
        fitSizeTo(src.width.toFloat(), src.height.toFloat(), layoutWidth, layoutHeight, fitMode, result)
        return this
    }
    fun fit(src: SizeF): AmvFitterEx {
        fitSizeTo(src.width, src.height, layoutWidth, layoutHeight, fitMode, result)
        return this
    }
    fun fit(srcWidth:Int, srcHeight:Int): AmvFitterEx {
        fitSizeTo(srcWidth.toFloat(), srcHeight.toFloat(), layoutWidth, layoutHeight, fitMode, result)
        return this
    }
    fun fit(srcWidth:Float, srcHeight:Float): AmvFitterEx {
        fitSizeTo(srcWidth, srcHeight, layoutWidth, layoutHeight, fitMode, result)
        return this
    }

}
package io.github.toyota32k.secureCamera.db

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import io.github.toyota32k.secureCamera.R

enum class Mark(val markValue:Int, @DrawableRes val iconId:Int, @ColorRes val colorId:Int) {
    None(0, R.drawable.ic_video_stop, R.color.color_mark_none),
    Star(1, R.drawable.ic_mark_star, R.color.color_mark_star),
    Flag(2, R.drawable.ic_mark_flag, R.color.color_mark_flag),
    Check(3, R.drawable.ic_mark_check, R.color.color_mark_check),
    ;

    fun colorStateList(context:Context):ColorStateList {
        return context.getColorStateList(colorId)
    }

    @ColorInt
    fun defaultColor(context:Context): Int {
        return colorStateList(context).defaultColor
    }

    @ColorInt
    fun selectedColor(context:Context):Int {
        return colorStateList(context).getColorForState(intArrayOf(android.R.attr.state_selected), 0)
    }

    fun icon(context:Context): Drawable {
        return ContextCompat.getDrawable(context, iconId) ?: throw IllegalStateException("no icon resource.")
    }

    companion object {
        fun fromMarkValue(value:Int):Mark {
            return values().firstOrNull { it.markValue == value } ?: None
        }
    }
}
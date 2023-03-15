package io.github.toyota32k.secureCamera.utils

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

object TintDrawable {
    fun tint(src: Drawable, @ColorInt color:Int, tintMode: PorterDuff.Mode= PorterDuff.Mode.SRC_IN):Drawable {
        return DrawableCompat.wrap(src.mutate()).apply {
            setTint(color)
            setTintMode(tintMode)
        }
    }
    fun tint(context: Context, @DrawableRes dr:Int, @ColorInt color:Int, tintMode: PorterDuff.Mode= PorterDuff.Mode.SRC_IN):Drawable {
        val src = ContextCompat.getDrawable(context, dr) ?: throw java.lang.IllegalStateException("no resource: $dr")
        return tint(src, color, tintMode)
    }
}
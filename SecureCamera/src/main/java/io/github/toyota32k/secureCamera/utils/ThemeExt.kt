package io.github.toyota32k.secureCamera.utils

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.toDrawable

@ColorInt
fun Context.getAttrColor(@AttrRes attrId:Int, @ColorInt def:Int=0):Int {
    val typedValue = TypedValue()
    return if(this.theme.resolveAttribute(attrId, typedValue, true)) {
        return typedValue.data
    } else def
}

fun Context.getAttrColorAsDrawable(@AttrRes attrId:Int, @ColorInt def:Int=0): Drawable {
    return getAttrColor(attrId, def).toDrawable()
}

package io.github.toyota32k.boodroid.common

import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleableRes
import androidx.core.content.res.getColorOrThrow

@ColorInt
fun Resources.Theme.getAttrColor(@AttrRes attrId:Int, @ColorInt def:Int=0): Int {
    val typedValue = TypedValue()
    return if(this.resolveAttribute(attrId, typedValue, true)) {
        return typedValue.data
    } else def
}

fun Resources.Theme.getAttrColorAsDrawableOrNull(@AttrRes attrId:Int): Drawable? {
    val typedValue = TypedValue()
    return if(this.resolveAttribute(attrId, typedValue, true)) {
        return ColorDrawable(typedValue.data)
    } else null
}

fun Resources.Theme.getAttrColorAsDrawable(@AttrRes attrId:Int, @ColorInt def:Int): Drawable {
    return ColorDrawable(getAttrColor(attrId, def))
}

@ColorInt
fun TypedArray.getColorAwareOfTheme(@StyleableRes attrId:Int, theme:Resources.Theme, @AttrRes themedAttrId:Int, @ColorInt defColor:Int): Int {
    return try {
        this.getColorOrThrow(attrId)
    } catch(e:Throwable) {
        theme.getAttrColor(themedAttrId, defColor)
    }
}

fun TypedArray.getColorAsDrawable(@StyleableRes attrId:Int, theme:Resources.Theme, @AttrRes themedAttrId:Int, @ColorInt defColor:Int): Drawable {
    return ColorDrawable(getColorAwareOfTheme(attrId, theme, themedAttrId, defColor))
}

fun TypedArray.getColorAsDrawableOrNull(@StyleableRes attrId:Int, theme:Resources.Theme, @AttrRes themedAttrId:Int): Drawable? {
    return try {
        ColorDrawable(this.getColorOrThrow(attrId))
    } catch(e:Throwable) {
        theme.getAttrColorAsDrawableOrNull(themedAttrId)
    }
}

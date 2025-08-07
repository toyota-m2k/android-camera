package io.github.toyota32k.secureCamera.db

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.secureCamera.R

enum class Mark(val v:Int, @DrawableRes val iconId:Int, @IdRes val id:Int) {
    None(0, R.drawable.ic_none, View.NO_ID),
    Star(1, R.drawable.ic_mark_star, R.id.tg_mark_star),
    Flag(2, R.drawable.ic_mark_flag, R.id.tg_mark_flag),
    Check(3, R.drawable.ic_mark_check, R.id.tg_mark_check),
    ;

//    fun colorStateList(context:Context):ColorStateList {
//        return context.getColorStateList(colorId)
//    }

//    @ColorInt
//    fun defaultColor(context:Context): Int {
//        return colorStateList(context).defaultColor
//    }


//
//    @ColorInt
//    fun selectedColor(context:Context):Int {
//        return colorStateList(context).getColorForState(intArrayOf(android.R.attr.state_selected), 0)
//    }
//
//    fun icon(context:Context): Drawable {
//        return ContextCompat.getDrawable(context, iconId) ?: throw IllegalStateException("no icon resource.")
//    }

    private class IDResolver : IIDValueResolver<Mark> {
        override fun id2value(@IdRes id: Int): Mark = Mark.id2value(id)
        override fun value2id(v: Mark): Int = v.id
    }

    companion object {
        fun fromMarkValue(value:Int):Mark {
            return values().firstOrNull { it.v == value } ?: None
        }
        fun id2value(@IdRes id: Int, def: Mark = None): Mark {
            return values().find { it.id == id } ?: def
        }
        fun valueOf(v: Int, def: Mark = None): Mark {
            return values().find { it.v == v } ?: def
        }
        val idResolver:IIDValueResolver<Mark> by lazy { IDResolver() }
    }
}
package io.github.toyota32k.secureCamera.db

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.secureCamera.R

enum class Mark(val v:Int, @param:DrawableRes val iconId:Int, @param:IdRes val id:Int) {
    None(0, R.drawable.ic_none, R.id.tg_mark_none),
    Star(1, R.drawable.ic_mark_star, R.id.tg_mark_star),
    Flag(2, R.drawable.ic_mark_flag, R.id.tg_mark_flag),
    Check(3, R.drawable.ic_mark_check, R.id.tg_mark_check),
    ;

    private class IDResolver : IIDValueResolver<Mark> {
        override fun id2value(@IdRes id: Int): Mark = Mark.id2value(id)
        override fun value2id(v: Mark): Int = v.id
    }

    companion object {
        fun fromMarkValue(value:Int):Mark {
            return entries.firstOrNull { it.v == value } ?: None
        }
        fun id2value(@IdRes id: Int, def: Mark = None): Mark {
            return entries.find { it.id == id } ?: def
        }
        fun valueOf(v: Int, def: Mark = None): Mark {
            return entries.find { it.v == v } ?: def
        }
        val idResolver:IIDValueResolver<Mark> by lazy { IDResolver() }
    }
}
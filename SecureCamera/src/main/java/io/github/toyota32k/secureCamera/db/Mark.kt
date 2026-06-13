package io.github.toyota32k.secureCamera.db

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.secureCamera.R

enum class Mark(val v:Int, @param:DrawableRes val iconId:Int, @param:IdRes val id:Int, val flag:Int) {
    None(0, R.drawable.ic_none, R.id.tg_mark_none, 1 shl 1),
    Star(1, R.drawable.ic_mark_star, R.id.tg_mark_star, 1 shl 2),
    Flag(2, R.drawable.ic_mark_flag, R.id.tg_mark_flag, 1 shl 3),
    Check(3, R.drawable.ic_mark_check, R.id.tg_mark_check, 1 shl 4),
    ;

    private class IDResolver : IIDValueResolver<Mark> {
        override fun id2value(@IdRes id: Int): Mark = entries.find { it.id == id } ?: None
        override fun value2id(v: Mark): Int = v.id
    }

    companion object {
        fun fromValue(v: Int, def: Mark = None): Mark {
            return entries.find { it.v == v } ?: def
        }
        val idResolver:IIDValueResolver<Mark> by lazy { IDResolver() }

        fun fromBitFlags(flag: Int): List<Mark> {
            return entries.filter { (flag and it.flag) != 0 }
        }
        fun List<Mark>.toBitFlags(): Int {
            return fold(0) { acc, r -> acc or r.flag }
        }
    }
}
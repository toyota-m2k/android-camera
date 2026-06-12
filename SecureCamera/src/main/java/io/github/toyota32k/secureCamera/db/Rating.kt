package io.github.toyota32k.secureCamera.db

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.secureCamera.R

enum class Rating(val ytLabel:String, val v:Int, @param:IdRes val id:Int, @param:DrawableRes val icon:Int, val flag:Int) {
    RatingNone("NORMAL", 3, R.id.tg_rating_none, R.drawable.ic_none, 0x1),
    Rating1("DREADFUL", 1, R.id.tg_rating_1, R.drawable.ic_rating_1, 0x2),
    Rating2("BAD", 2, R.id.tg_rating_2, R.drawable.ic_rating_2, 0x4),
    Rating3("GOOD", 4, R.id.tg_rating_3, R.drawable.ic_rating_3, 0x8),
    Rating4("EXCELLENT", 5, R.id.tg_rating_4, R.drawable.ic_rating_4, 0x10);

    private class IDResolver : IIDValueResolver<Rating> {
        override fun id2value(@IdRes id: Int):Rating  = entries.find { it.id == id } ?: RatingNone
        override fun value2id(v: Rating): Int = v.id
    }

    companion object {
        fun fromValue(v: Int, def: Rating = RatingNone): Rating {
            return entries.find { it.v == v } ?: def
        }
        val idResolver: IIDValueResolver<Rating> by lazy { IDResolver() }

        fun fromBitFlags(flag: Int): List<Rating> {
            return entries.filter { (flag and it.flag) != 0 }
        }
        fun List<Rating>.toBitFlags(): Int {
            return fold(0) { acc, r -> acc or r.flag }
        }
    }
}

package io.github.toyota32k.secureCamera.db

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.secureCamera.R

enum class Rating(val ytLabel:String, val v:Int, @param:IdRes val id:Int, @param:DrawableRes val icon:Int) {
    RatingNone("NORMAL", 3, View.NO_ID, R.drawable.ic_none),
    Rating1("DREADFUL", 1, R.id.tg_rating_1, R.drawable.ic_rating_1),
    Rating2("BAD", 2, R.id.tg_rating_2, R.drawable.ic_rating_2),
    Rating3("GOOD", 4, R.id.tg_rating_3, R.drawable.ic_rating_3),
    Rating4("EXCELLENT", 5, R.id.tg_rating_4, R.drawable.ic_rating_4);

    private class IDResolver : IIDValueResolver<Rating> {
        override fun id2value(@IdRes id: Int):Rating  = Rating.id2value(id)
        override fun value2id(v: Rating): Int = v.id
    }

    companion object {
        fun id2value(@IdRes id: Int, def: Rating = RatingNone): Rating {
            return entries.find { it.id == id } ?: def
        }
        fun valueOf(v: Int, def: Rating = RatingNone): Rating {
            return entries.find { it.v == v } ?: def
        }
        val idResolver: IIDValueResolver<Rating> by lazy { IDResolver() }
    }
}

package io.github.toyota32k.lib.player.model

import android.util.Size
import kotlin.math.abs

enum class Rotation(val degree:Int) {
    NONE(0),        // reset
    LEFT(-90),
    RIGHT(90);

    companion object {
        fun normalize(degree:Int):Int {
            return degree%360
        }
        fun transposeSize(degree:Int, size:Size): Size {
            return if(abs(degree%180) == 90) {
                Size(size.height, size.width)
            } else size
        }
    }
}

package io.github.toyota32k.lib.player.model

import kotlin.math.max
import kotlin.math.min

data class Range (val start:Long, val end:Long=0) {
    /**
     * pos が start-end 内に収まるようクリップする
     */
    fun clip(pos:Long) : Long {
        return if(start<end) {
            min(max(start, pos), end)
        } else {
            max(start, pos)
        }
    }

    fun contains(pos:Long):Boolean {
        return if(start<end) {
            start<=pos && pos<end
        } else {
            start<=pos
        }
    }

    companion object {
        val empty = Range(0,0)
    }
}

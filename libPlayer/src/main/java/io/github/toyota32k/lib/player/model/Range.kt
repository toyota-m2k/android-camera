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
            pos in start until end
        } else {
            start<=pos
        }
    }

    val isEmpty:Boolean
        get() = start == 0L && end==0L
    val span:Long
        get() = end - start

    companion object {
        val empty = Range(0,0)

        /**
         * end = 0 で「最後まで」を表している Rangeに、duration の値をセットする
         */
        fun terminate(range:Range, duration:Long):Range {
            return if(range.end>0) range else Range(range.start, duration)
        }

        fun terminate(ranges:Sequence<Range>, duration:Long) = sequence<Range> {
            for(r in ranges) {
                yield(terminate(r, duration))
            }
        }
    }
}

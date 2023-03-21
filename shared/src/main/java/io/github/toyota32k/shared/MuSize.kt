package io.github.toyota32k.shared

import android.util.Size
import android.util.SizeF

interface ImSize {
    val height:Float
    val width:Float
    val asSizeF:SizeF
    val asSize: Size
    val isEmpty:Boolean
}

/**
 * MutableなSize型
 */
data class MuSize(override var width: Float, override var height: Float) : ImSize {

    constructor() : this(0f,0f)
    constructor(v:Float) : this(v,v)
    constructor(s: SizeF) : this(s.width, s.height)
    constructor(s: Size) : this(s.width.toFloat(), s.height.toFloat())

    fun copyFrom(s: MuSize) {
        width = s.width
        height = s.height
    }
    fun copyFrom(s: SizeF) {
        width = s.width
        height = s.height
    }
    fun set(width:Float, height:Float) {
        this.width = width
        this.height = height
    }

    fun rotate() {
        val w = width
        width = height
        height = w
    }

    override val asSizeF: SizeF
        get() = SizeF(width,height)

    override val asSize: Size
        get() = Size(width.toInt(), height.toInt())

    override val isEmpty:Boolean
        get() = width==0f && height==0f


    fun empty() {
        set(0f,0f)
    }

}

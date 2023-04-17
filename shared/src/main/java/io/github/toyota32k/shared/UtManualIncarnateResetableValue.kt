package io.github.toyota32k.shared

import io.github.toyota32k.utils.IUtResetableValue

class UtManualIncarnateResetableValue<T>(private val onIncarnate:()->T, private val onReset:((T)->Unit)?): IUtResetableValue<T> {
    constructor(onIncarnate: () -> T) : this(onIncarnate, null)
    private var rawValue:T? = onIncarnate()
    override var value:T
        get() = rawValue!!
        set(v) { rawValue = v }
    override val hasValue
        get() = rawValue!=null
    override fun reset(preReset:((T)->Unit)?) {
        val rv = rawValue ?: return
        (preReset?:onReset)?.invoke(rv)
        rawValue = null
    }
    override fun setIfNeed(fn:()->T) {
        if(rawValue == null) {
            value = fn()
        }
    }

    fun reset() {
        reset(null)
    }
    fun incarnate():Boolean {
        return if(rawValue == null) {
            rawValue = onIncarnate()
            return true
        } else false
    }
}
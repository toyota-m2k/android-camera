package io.github.toyota32k.shared

import io.github.toyota32k.utils.IUtResetableValue

class UtManualIncarnateResetableValue<T>(val fn:()->T): IUtResetableValue<T> {
    private var rawValue:T? = fn()
    override var value:T
        get() = rawValue!!
        set(v) { rawValue = v }
    override val hasValue
        get() = rawValue!=null
    override fun reset(preReset:((T)->Unit)?) {
        val rv = rawValue ?: return
        preReset?.invoke(rv)
        rawValue = null
    }
    override fun setIfNeed(fn:()->T) {
        if(rawValue == null) {
            value = fn()
        }
    }
    fun incarnate() {
        rawValue = fn()
    }
}
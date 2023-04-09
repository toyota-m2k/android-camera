package io.github.toyota32k.secureCamera.utils

import androidx.lifecycle.LifecycleOwner
import io.github.toyota32k.utils.IDisposable
import io.github.toyota32k.utils.Listeners

class UnitListeners(private val listeners:Listeners<Unit> = Listeners<Unit>()) {
    val count:Int
        get() = listeners.count
    val isEmpty:Boolean
        get() = count==0
    val isNotEmpty:Boolean
        get() = count>0

    fun add(owner: LifecycleOwner, fn: () -> Unit): IDisposable
            = listeners.add(owner) { fn() }
    fun addForever(fn: () -> Unit): IDisposable
            = listeners.addForever { fn() }

    fun invoke() {
        listeners.invoke(Unit)
    }
    fun clear() {
        listeners.clear()
    }
}
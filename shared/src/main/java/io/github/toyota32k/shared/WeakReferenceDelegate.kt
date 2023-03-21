package io.github.toyota32k.shared

import java.lang.ref.WeakReference
import kotlin.reflect.KProperty

class WeakReferenceDelegate<T>(value:T?=null) {
    private var ref:WeakReference<T>? = if(value!=null) WeakReference(value) else null
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        return ref?.get()
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        if(value!=null) {
            ref = WeakReference(value)
        } else {
            ref = null
        }
    }
}

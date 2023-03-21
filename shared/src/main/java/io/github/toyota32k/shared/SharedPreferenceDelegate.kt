package io.github.toyota32k.shared

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class SharedPreferenceDelegate(application: Context) {
    private val appPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    fun <T:Any> pref(default: T) = object : ReadWriteProperty<Any, T> {
        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: Any, property: KProperty<*>): T {
            val key = property.name
            return (appPref.all[key] as? T) ?: run {
                put(key, default)
                default
            }
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            val key = property.name
            put(key, value)
        }
    }

    fun <T : Any?> prefNullable() = object : ReadWriteProperty<Any, T?> {

        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: Any, property: KProperty<*>): T? {
            val key = property.name
            return appPref.all[key] as? T?
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
            val key = property.name
            put(key, value)
        }
    }

    private fun <T : Any?> put(key: String, value: T?) {
        appPref.edit {
            when (value) {
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Set<*> -> putStringSet(key, value.map { it as String }.toSet())
                null -> remove(key)
                else -> throw IllegalArgumentException("unsupported type ${value!!::class.java}}")
            }
        }
    }
}

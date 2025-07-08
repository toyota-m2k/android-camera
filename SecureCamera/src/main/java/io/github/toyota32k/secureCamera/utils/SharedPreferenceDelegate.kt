package io.github.toyota32k.secureCamera.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class SharedPreferenceDelegate(val appPref: SharedPreferences) {
    constructor(application: Application) : this(PreferenceManager.getDefaultSharedPreferences(application.applicationContext))
    constructor(context: Context) : this(PreferenceManager.getDefaultSharedPreferences(context))
    constructor(context: Context, name: String, mode: Int = Context.MODE_PRIVATE) : this(context.getSharedPreferences(name, mode))

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

    fun <T: Any> typedPref(default: T, type: Class<T>) = object : ReadWriteProperty<Any, T> {
        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: Any, property: KProperty<*>): T {
            val key = property.name
            val gson = Gson()
            val json = appPref.getString(key, null) ?: return default
            return try {
                gson.fromJson(json, type)
            } catch (e: Throwable) {
                default
            }
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            val key = property.name
            val gson = Gson()
            put(key, gson.toJson(value))
        }
    }
    fun <T: Any?> typedPrefNullable(type: Class<T>) = object : ReadWriteProperty<Any, T?> {
        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: Any, property: KProperty<*>): T? {
            val key = property.name
            val gson = Gson()
            val json = appPref.getString(key, null) ?: return null
            return try {
                gson.fromJson(json, type)
            } catch (e: Throwable) {
                null
            }
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
            val key = property.name
            if (value == null) {
                appPref.edit { remove(key) }
            } else {
                val gson = Gson()
                put(key, gson.toJson(value))
            }
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
                else -> throw IllegalArgumentException("unsupported type $value")
            }
        }
    }
}

package io.github.toyota32k.shared.popup

import android.graphics.drawable.Drawable

interface IMenuItem {
    enum class Type(val hasChildren:Boolean=false) {
        NORMAL,
        SEPARATOR,
        SUBMENU(true),
        CHECKBOX,
        RADIO,
        RADIO_GROUP(true),
        CUSTOM
    }
    val type:Type

    fun add(item:IMenuItem)
    fun remove(item:IMenuItem)

    var icon:Drawable?
    var label:String?
    var enabled:Boolean
    var checked:Boolean
}
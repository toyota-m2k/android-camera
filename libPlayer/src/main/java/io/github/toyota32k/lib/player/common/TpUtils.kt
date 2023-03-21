/**
 * ユーティリティ
 *
 * @author M.TOYOTA 2018.07.11 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package io.github.toyota32k.lib.player.common

import android.content.Context
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity
import io.github.toyota32k.lib.player.TpLib
import java.text.SimpleDateFormat
import java.util.*

fun View.setLayoutWidth(width:Int) {
    val params = layoutParams
    if(null!=params) {
        params.width = width
        layoutParams = params
    }
}

fun View.getLayoutWidth() : Int {
    return if(layoutParams?.width ?: -1 >=0) {
        layoutParams.width
    } else {
        width
    }
}

fun View.setLayoutHeight(height:Int) {
    val params = layoutParams
    if(null!=params) {
        params.height = height
        layoutParams = params
    }
}

@Suppress("unused")
fun View.getLayoutHeight() : Int {
    return if(layoutParams?.height ?: -1 >=0) {
        layoutParams.height
    } else {
        height
    }
}

fun View.setLayoutSize(width:Int, height:Int) {
    val params = layoutParams
    if(null!=params) {
        params.width = width
        params.height = height
        layoutParams = params
    }
}

@Suppress("unused")
fun View.measureAndGetSize() :Size {
    this.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    return Size(this.measuredWidth, this.measuredHeight)
}

fun View.setMargin(left:Int, top:Int, right:Int, bottom:Int) {
    val p = layoutParams as? ViewGroup.MarginLayoutParams
    if(null!=p) {
        p.setMargins(left, top, right, bottom)
        layoutParams = p
    }

}

fun View.getActivity(): FragmentActivity? {
    var ctx = this.context
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) {
            return ctx
        }
        ctx = ctx.baseContext
    }
    return null
}

fun Context.getActivity():FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) {
            return ctx
        }
        ctx = ctx.baseContext
    }
    return null
}

//fun Context.dp2px(dp:Float) : Float {
//    return resources.displayMetrics.density * dp
//}
//
//fun Context.dp2px(dp:Int) : Int {
//    return (resources.displayMetrics.density * dp).roundToInt()
//}
//
//fun Context.px2dp(px:Float) : Float {
//    return px / resources.displayMetrics.density
//}
//
//@Suppress("unused")
//fun Context.px2dp(px:Int) : Int {
//    return px2dp(px.toFloat()).toInt()
//}

class TpTimeSpan(private val ms : Long) {
    val milliseconds: Long
        get() = ms % 1000

    val seconds: Long
        get() = (ms / 1000) % 60

    val minutes: Long
        get() = (ms / 1000 / 60) % 60

    val hours: Long
        get() = (ms / 1000 / 60 / 60)

    fun formatH() : String {
        return String.format("%02d:%02d.%02d", hours, minutes, seconds)
    }
    fun formatM() : String {
        return String.format("%02d'%02d\"", minutes, seconds)
    }
    fun formatS() : String {
        return String.format("%02d\".%02d", seconds, milliseconds/10)
    }
}

fun <T> ignoreErrorCall(def:T, f:()->T): T {
    return try {
        f()
    } catch(e:Exception) {
        TpLib.logger.debug("SafeCall: ${e.message}")
        def
    }
}

fun parseDateString(format:String, dateString:String) : Date? {
    return try {
        val dateFormatter = SimpleDateFormat(format, Locale.US)
        dateFormatter.timeZone = TimeZone.getTimeZone("GMT")
        dateFormatter.calendar = GregorianCalendar()
        dateFormatter.parse(dateString)
    } catch(e:Exception) {
        // UtLogger.error("date format error. ${dateString}")
        null
    }
}

fun parseIso8601DateString(dateString:String) : Date? {
    @Suppress("SpellCheckingInspection")
    return parseDateString("yyyyMMdd'T'HHmmssZ", dateString) ?: parseDateString("yyyy-MM-dd'T'HH:mm:ssZ", dateString)
}

fun formatTime(time:Long, duration:Long) : String {
    val v = TpTimeSpan(time)
    val t = TpTimeSpan(duration)
    return when {
        t.hours>0 -> v.formatH()
        t.minutes>0 -> v.formatM()
        else -> v.formatS()
    }
}

fun formatSize(bytes:Long):String {
    if(bytes>1000*1000*1000) {
        val m = bytes / (1000*1000)
        return "${m/1000f} GB"
    } else if(bytes>1000*1000) {
        val k = bytes / 1000
        return "${k/1000f} MB"
    } else if(bytes>1000) {
        return "${bytes/1000f} KB"
    } else {
        return "${bytes} B"
    }
}

package io.github.toyota32k.secureCamera.db

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object FilenameHelper {
    // yyyy.MM.dd-HH:mm:ss[.SSS]
    private val RX = Regex(
        """^(\d{4})\.(\d{2})\.(\d{2})-(\d{2}):(\d{2}):(\d{2})(?:\.(\d{3}))?$"""
    )

    /**
     * 正規表現で厳密に解釈して Date を返す。形式不正なら null。
     * timeZone は保存時と同じものを渡すのが安全。
     */
    fun parse(text: String, timeZone: TimeZone = TimeZone.getDefault()): Date? {
        val m = RX.matchEntire(text) ?: return null

        val year = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt() // 1..12
        val day = m.groupValues[3].toInt()   // 1..31
        val hour = m.groupValues[4].toInt()  // 0..23
        val min = m.groupValues[5].toInt()   // 0..59
        val sec = m.groupValues[6].toInt()   // 0..59
        val ms = m.groupValues[7].ifEmpty { "0" }.toInt() // 0..999

        // Calendar(non-lenient) で値域/実在日付を検証
        val cal = Calendar.getInstance(timeZone, Locale.US).apply {
            isLenient = false
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1) // Calendar は 0-based month
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, sec)
            set(Calendar.MILLISECOND, ms)
        }

        return try {
            cal.time // ここで不正日付なら例外
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Date -> yyyy.MM.dd-HH:mm:ss
     */
    fun formatSeconds(date: Date, timeZone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(timeZone, Locale.US).apply { time = date }
        return buildString(19) {
            append4(cal.get(Calendar.YEAR)); append('.')
            append2(cal.get(Calendar.MONTH) + 1); append('.')
            append2(cal.get(Calendar.DAY_OF_MONTH)); append('-')
            append2(cal.get(Calendar.HOUR_OF_DAY)); append(':')
            append2(cal.get(Calendar.MINUTE)); append(':')
            append2(cal.get(Calendar.SECOND))
        }
    }

    /**
     * Date -> yyyy.MM.dd-HH:mm:ss.SSS
     */
    fun formatMillis(date: Date, timeZone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(timeZone, Locale.US).apply { time = date }
        return buildString(23) {
            append4(cal.get(Calendar.YEAR)); append('.')
            append2(cal.get(Calendar.MONTH) + 1); append('.')
            append2(cal.get(Calendar.DAY_OF_MONTH)); append('-')
            append2(cal.get(Calendar.HOUR_OF_DAY)); append(':')
            append2(cal.get(Calendar.MINUTE)); append(':')
            append2(cal.get(Calendar.SECOND)); append('.')
            append3(cal.get(Calendar.MILLISECOND))
        }
    }

    /**
     * Date -> ミリ秒が 0 のときは .SSS を省略
     */
    fun formatSmart(date: Date, timeZone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(timeZone, Locale.US).apply { time = date }
        return if (cal.get(Calendar.MILLISECOND) == 0) {
            formatSeconds(date, timeZone)
        } else {
            formatMillis(date, timeZone)
        }
    }

    private fun StringBuilder.append2(v: Int) {
        if (v < 10) append('0')
        append(v)
    }

    private fun StringBuilder.append3(v: Int) {
        when {
            v < 10 -> append("00")
            v < 100 -> append('0')
        }
        append(v)
    }

    private fun StringBuilder.append4(v: Int) {
        when {
            v < 10 -> append("000")
            v < 100 -> append("00")
            v < 1000 -> append('0')
        }
        append(v)
    }
}
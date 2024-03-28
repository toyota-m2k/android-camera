package io.github.toyota32k.secureCamera.utils

// io.github.toyota32k.utils に移行

class TimeSpan (private val ms : Long) {
    val milliseconds: Long
        get() = ms % 1000

    val seconds: Long
        get() = (ms / 1000) % 60

    val minutes: Long
        get() = (ms / 1000 / 60) % 60

    val hours: Long
        get() = (ms / 1000 / 60 / 60)

    fun formatH(format:String="%02d:%02d'%02d\"") : String {
        return String.format(format, hours, minutes, seconds)
    }
    fun formatM(format:String="%02d'%02d\"") : String {
        return String.format(format, minutes, seconds)
    }
    fun formatMm(format:String="%02d\"%02d\"%03d") : String {
        return String.format(format, minutes, seconds, milliseconds)
    }
    fun formatS(format:String="%02d\"%02d") : String {
        return String.format(format, seconds, milliseconds/10)
    }
    fun formatSm(format:String="%02d\"%03d") : String {
        return String.format(format, seconds, milliseconds)
    }
    fun formatAuto() : String {
        return when {
            hours>0 -> formatH()
            minutes>0 -> formatM()
            else-> formatS()
        }
    }


    companion object {
        @JvmStatic
        fun formatH(ms:Long) : String {
            return TimeSpan(ms).formatH()
        }
        @JvmStatic
        fun formatM(ms:Long) : String {
            return TimeSpan(ms).formatM()
        }
        @JvmStatic
        fun formatS(ms:Long) : String {
            return TimeSpan(ms).formatS()
        }
        @JvmStatic
        fun formatAuto(ms:Long):String {
            return TimeSpan(ms).formatAuto()
        }
    }
}
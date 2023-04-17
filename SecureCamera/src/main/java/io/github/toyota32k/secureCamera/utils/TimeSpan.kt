package io.github.toyota32k.secureCamera.utils

class TimeSpan (private val ms : Long) {
    val milliseconds: Long
        get() = ms % 1000

    val seconds: Long
        get() = (ms / 1000) % 60

    val minutes: Long
        get() = (ms / 1000 / 60) % 60

    val hours: Long
        get() = (ms / 1000 / 60 / 60)

    fun formatH() : String {
        return String.format("%02d:%02d\'%02d\"", hours, minutes, seconds)
    }
    fun formatM() : String {
        return String.format("%02d\'%02d\"", minutes, seconds)
    }
    fun formatS() : String {
        return String.format("%02d.%02d", seconds, milliseconds/10)
    }
}
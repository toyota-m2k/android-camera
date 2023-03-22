package io.github.toyota32k.secureCamera

import android.content.Context
import io.github.toyota32k.shared.SharedPreferenceDelegate

object Settings {
    private lateinit var spd :SharedPreferenceDelegate

    fun initialize(application: Context) {
        if(this::spd.isInitialized) return
        spd = SharedPreferenceDelegate(application)
    }

    object Camera {
        const val TAP_NONE = 0
        const val TAP_VIDEO = 1
        const val TAP_PHOTO = 2
        val tapAction:Int by spd.pref(TAP_VIDEO)
        val hidePanelOnStart:Boolean by spd.pref(false)
    }

    object Player {
        val spanOfSkipForward:Long by spd.pref(1000)
        val spanOfSkipBackward:Long by spd.pref(300)
    }

    object Security {
        val enablePassword:Boolean by spd.pref(false)
        val password:String by spd.pref("")
        val numberOfIncorrectPassword:Int by spd.pref(0)
    }
}
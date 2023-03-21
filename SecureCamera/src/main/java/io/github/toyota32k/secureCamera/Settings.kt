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
        val tapToTakePhoto:Boolean by spd.pref(false)
        val tapToTakeVideo:Boolean by spd.pref(true)
        val hidePanelOnStart:Boolean by spd.pref(false)
    }

    object Player {

    }

    object Security {
        val enablePassword:Boolean by spd.pref(false)
        val password:String by spd.pref("")
        val numberOfIncorrectPassword:Int by spd.pref(0)
    }
}
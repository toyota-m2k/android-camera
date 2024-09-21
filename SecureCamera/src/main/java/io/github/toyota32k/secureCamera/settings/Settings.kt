package io.github.toyota32k.secureCamera.settings

import android.app.Application
import android.os.Build
import io.github.toyota32k.binder.DPDate
import io.github.toyota32k.shared.SharedPreferenceDelegate
import java.util.UUID

object Settings {
    private lateinit var spd :SharedPreferenceDelegate

    fun initialize(application: Application) {
        if(this::spd.isInitialized) return
        spd = SharedPreferenceDelegate(application)
        if(SecureArchive.clientId.isEmpty()) {
            SecureArchive.clientId = UUID.randomUUID().toString()
        }
    }


    object Camera {
        const val TAP_NONE = 0
        const val TAP_VIDEO = 1
        const val TAP_PHOTO = 2

        const val DEF_TAP_ACTION = TAP_VIDEO
        const val DEF_SELFIE_ACTION = TAP_VIDEO
        const val DEF_HIDE_PANEL_ON_START = false

        var tapAction:Int by spd.pref(DEF_TAP_ACTION)
        var selfieAction:Int by spd.pref(DEF_SELFIE_ACTION)
        var hidePanelOnStart:Boolean by spd.pref(DEF_HIDE_PANEL_ON_START)

    }

    object Player {
        const val DEF_SPAN_OF_SKIP_FORWARD = 15000L
        const val DEF_SPAN_OF_SKIP_BACKWARD = 5000L
        var spanOfSkipForward:Long by spd.pref(DEF_SPAN_OF_SKIP_FORWARD)
        var spanOfSkipBackward:Long by spd.pref(DEF_SPAN_OF_SKIP_BACKWARD)
    }

    object Security {
        const val DEF_ENABLE_PASSWORD = false
        const val DEF_PASSWORD = ""
        const val DEF_CLEAR_ALL_ON_PASSWORD_ERROR = false
        const val DEF_NUMBER_OF_INCORRECT_PASSWORD = 3

        var enablePassword:Boolean by spd.pref(DEF_ENABLE_PASSWORD)
        var password:String by spd.pref(DEF_PASSWORD)
        var clearAllOnPasswordError by spd.pref(DEF_CLEAR_ALL_ON_PASSWORD_ERROR)
        var numberOfIncorrectPassword:Int by spd.pref(DEF_NUMBER_OF_INCORRECT_PASSWORD)
        var incorrectCount:Int by spd.pref(0)
    }

    object SecureArchive {
        var clientId:String by spd.pref("")
        var primaryAddress:String by spd.pref("")
        var secondaryAddress:String by spd.pref("")
        var myPort by spd.pref(5001)
        var deviceName by spd.pref(Build.MODEL)
        val isConfigured:Boolean get() = primaryAddress.isNotEmpty()

        val hosts:Iterator<String> get() = iterator<String> {
            if(primaryAddress.isNotEmpty()) {
                yield(primaryAddress)
            }
            if(secondaryAddress.isNotEmpty()) {
                yield(secondaryAddress)
            }
        }
    }

    fun reset() {
        Camera.tapAction = Camera.DEF_TAP_ACTION
        Camera.selfieAction = Camera.DEF_SELFIE_ACTION
        Camera.hidePanelOnStart = Camera.DEF_HIDE_PANEL_ON_START
        Player.spanOfSkipForward = Player.DEF_SPAN_OF_SKIP_FORWARD
        Player.spanOfSkipBackward = Player.DEF_SPAN_OF_SKIP_BACKWARD
        Security.enablePassword = Security.DEF_ENABLE_PASSWORD
        Security.password = Security.DEF_PASSWORD
        Security.clearAllOnPasswordError = Security.DEF_CLEAR_ALL_ON_PASSWORD_ERROR
        Security.numberOfIncorrectPassword = Security.DEF_NUMBER_OF_INCORRECT_PASSWORD
        Security.incorrectCount = 0
        SecureArchive.primaryAddress = ""
    }

    object PlayListSetting {
        var sortOrder by spd.pref(false)    // 日付昇順がデフォルト
        var enableStartDate by spd.pref(false)
        var enableEndDate by spd.pref(false)
        var startDateInt by spd.pref(0)
        var endDateInt by spd.pref(0)
        var cloudTestMode by spd.pref(false)
        var onlyUnBackedUpItems by spd.pref(false)

        var startDate: DPDate
            get() = DPDate.fromInt(startDateInt)
            set(v) { startDateInt = v.intValue }
        var endDate:DPDate
            get() = DPDate.fromInt(endDateInt)
            set(v) { endDateInt = v.intValue }

        fun reset() {
            sortOrder = false
            enableStartDate = false
            enableEndDate = false
            cloudTestMode = false
            onlyUnBackedUpItems = false
        }
    }
}
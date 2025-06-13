package io.github.toyota32k.secureCamera.settings

import android.app.Application
import android.os.Build
import androidx.fragment.app.FragmentActivity
import io.github.toyota32k.binder.DPDate
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.utils.IThemeList
import io.github.toyota32k.secureCamera.utils.ThemeInfo
import io.github.toyota32k.secureCamera.utils.ThemeSelector
import io.github.toyota32k.secureCamera.utils.ThemeSelector.ContrastLevel
import io.github.toyota32k.secureCamera.utils.ThemeSelector.NightMode
import io.github.toyota32k.utils.android.SharedPreferenceDelegate
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

    object ThemeList: IThemeList {
        override val themes: List<ThemeInfo> = listOf(
            ThemeInfo("Default", R.style.DefaultTheme, null, null),
            ThemeInfo("Cherry", R.style.CherryTheme, R.style.CherryTheme_MediumContrast, R.style.CherryTheme_HighContrast),
            ThemeInfo("Grape", R.style.GrapeTheme, R.style.GrapeTheme_MediumContrast, R.style.GrapeTheme_HighContrast),
            ThemeInfo("Blueberry", R.style.BlueberryTheme, R.style.BlueberryTheme_MediumContrast, R.style.BlueberryTheme_HighContrast),
            ThemeInfo("Melon", R.style.MelonTheme, R.style.MelonTheme_MediumContrast, R.style.MelonTheme_HighContrast),
            ThemeInfo("Orange", R.style.OrangeTheme, R.style.OrangeTheme_MediumContrast, R.style.OrangeTheme_HighContrast),
            ThemeInfo("Soda", R.style.SodaTheme, R.style.SodaTheme_MediumContrast, R.style.SodaTheme_HighContrast),
        )
    }


    object Camera {
        const val TAP_NONE = 0
        const val TAP_VIDEO = 1
        const val TAP_PHOTO = 2

        const val DEF_TAP_ACTION = TAP_VIDEO
        const val DEF_SELFIE_ACTION = TAP_VIDEO
        const val DEF_HIDE_PANEL_ON_START = false

//        var tapAction:Int by spd.pref(DEF_TAP_ACTION)
        var selfieAction:Int by spd.pref(DEF_SELFIE_ACTION)
        var hidePanelOnStart:Boolean by spd.pref(DEF_HIDE_PANEL_ON_START)

        fun reset() {
            selfieAction = Camera.DEF_SELFIE_ACTION
            hidePanelOnStart = Camera.DEF_HIDE_PANEL_ON_START
        }
    }

    object Player {
        const val DEF_SPAN_OF_SKIP_FORWARD = 15000L
        const val DEF_SPAN_OF_SKIP_BACKWARD = 5000L
        var spanOfSkipForward:Long by spd.pref(DEF_SPAN_OF_SKIP_FORWARD)
        var spanOfSkipBackward:Long by spd.pref(DEF_SPAN_OF_SKIP_BACKWARD)

        fun reset() {
            spanOfSkipForward = Player.DEF_SPAN_OF_SKIP_FORWARD
            spanOfSkipBackward = Player.DEF_SPAN_OF_SKIP_BACKWARD
        }
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

        fun reset() {
            enablePassword = Security.DEF_ENABLE_PASSWORD
            password = Security.DEF_PASSWORD
            clearAllOnPasswordError = Security.DEF_CLEAR_ALL_ON_PASSWORD_ERROR
            numberOfIncorrectPassword = Security.DEF_NUMBER_OF_INCORRECT_PASSWORD
            incorrectCount = 0
        }
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

        fun reset() {
            primaryAddress = ""
            secondaryAddress = ""
        }
    }

    object Design {
        var themeName:String by spd.pref("Default")
        var contrastLevelName by spd.pref("System")
        var nightModeInt by spd.pref(-1)

        fun reset() {
            themeName = "Default"
            contrastLevelName = "System"
            nightModeInt = -1
        }

        var themeInfo: ThemeInfo
            get() = ThemeList.themeOf(themeName)
            set(v) { themeName = v.label }
        var contrastLevel: ContrastLevel
            get() = ContrastLevel.parse(contrastLevelName) ?: ContrastLevel.System
            set(v) { contrastLevelName = v.name }
        var nightMode: NightMode
            get() = NightMode.ofMode(nightModeInt) ?: NightMode.System
            set(v) { nightModeInt = v.mode }

        fun applyToActivity(activity: FragmentActivity) {
            ThemeSelector.defaultInstance.applyNightMode(nightMode)
            ThemeSelector.defaultInstance.applyTheme(themeInfo, contrastLevel, activity)
        }
    }

    object PlayListSetting {
        var sortOrder by spd.pref(false)    // 日付昇順がデフォルト
        var enableStartDate by spd.pref(false)
        var enableEndDate by spd.pref(false)
        var startDateInt by spd.pref(0)
        var endDateInt by spd.pref(0)
        var cloudTestMode by spd.pref(false)
        var onlyUnBackedUpItems by spd.pref(false)
        var allowDelete by spd.pref(false)

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

    fun reset() {
        Camera.reset()
        Player.reset()
        Security.reset()
        SecureArchive.reset()
        Design.reset()
        PlayListSetting.reset()
    }
}
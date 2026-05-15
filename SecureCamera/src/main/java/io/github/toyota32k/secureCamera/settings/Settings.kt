package io.github.toyota32k.secureCamera.settings

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.fragment.app.FragmentActivity
import io.github.toyota32k.binder.DPDate
import io.github.toyota32k.lib.camera.TcAspect
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.dialog.SettingDialog
import io.github.toyota32k.secureCamera.utils.IThemeList
import io.github.toyota32k.secureCamera.utils.ThemeInfo
import io.github.toyota32k.secureCamera.utils.ThemeSelector
import io.github.toyota32k.secureCamera.utils.ThemeSelector.ContrastLevel
import io.github.toyota32k.secureCamera.utils.ThemeSelector.NightMode
import io.github.toyota32k.utils.android.SharedPreferenceDelegate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
        const val DEF_PREFER_HDR = false
        const val DEF_PREFER_QUALITY = false
        const val DEF_RESOLUTION = 0

//        var tapAction:Int by spd.pref(DEF_TAP_ACTION)
        var selfieAction:Int by spd.pref(DEF_SELFIE_ACTION)
        var hidePanelOnStart:Boolean by spd.pref(DEF_HIDE_PANEL_ON_START)
        var preferHDR:Boolean by spd.pref(DEF_PREFER_HDR)
        var preferQuality:Boolean by spd.pref(DEF_PREFER_QUALITY)
        private var rawAspect:Int by spd.pref(TcAspect.Default.ratio)
        private var rawResolution:Int by spd.pref(DEF_RESOLUTION)

        var aspect:TcAspect
            get() = TcAspect.fromRatio(rawAspect)
            set(v) { rawAspect = v.ratio }
        var resolution: SettingDialog.SettingViewModel.Resolution
            get() = SettingDialog.SettingViewModel.Resolution.fromValue(rawResolution)
            set(v) { rawResolution = v.value }

        fun reset() {
            selfieAction = Camera.DEF_SELFIE_ACTION
            hidePanelOnStart = Camera.DEF_HIDE_PANEL_ON_START
            preferHDR = false
            rawAspect = TcAspect.Default.ratio
            rawResolution = 0
        }
    }

    object Player {
        const val DEF_SPAN_OF_SKIP_FORWARD = 15000L
        const val DEF_SPAN_OF_SKIP_BACKWARD = 5000L
        const val DEF_RESOLUTION = 0

        var spanOfSkipForward:Long by spd.pref(DEF_SPAN_OF_SKIP_FORWARD)
        var spanOfSkipBackward:Long by spd.pref(DEF_SPAN_OF_SKIP_BACKWARD)
        private var rawSnapshotResolution:Int by spd.pref(DEF_RESOLUTION)
        var snapshotResolution: SettingDialog.SettingViewModel.Resolution
            get() = SettingDialog.SettingViewModel.Resolution.fromValue(rawSnapshotResolution)
            set(v) { rawSnapshotResolution = v.value }

        fun reset() {
            spanOfSkipForward = Player.DEF_SPAN_OF_SKIP_FORWARD
            spanOfSkipBackward = Player.DEF_SPAN_OF_SKIP_BACKWARD
            rawSnapshotResolution = 0
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

    object Server {
        const val DEF_SSL = true
        const val DEF_PORT = 5001
        var myPort by spd.pref(DEF_PORT)
        var ssl by spd.pref(DEF_SSL)
    }

    object SecureArchive {
        var clientId:String by spd.pref("")
        // var primaryAddress:String by spd.pref("")
        // private var secondaryAddress:String by spd.pref("")
        private var _primaryHost:String? by spd.prefNullable()
        private var _secondaryHost:String? by spd.prefNullable()
        private var _primaryHostCache: SecureArchiveHost? = null
        private var _secondaryHostCache: SecureArchiveHost? = null
        var primaryHost: SecureArchiveHost?
            get() = _primaryHostCache ?: SecureArchiveHost.fromJson(_primaryHost).apply { _primaryHostCache = this }
            set(v) {
                _primaryHostCache = v
                _primaryHost = v?.toJson()
            }
        var secondaryHost: SecureArchiveHost?
            get() = _secondaryHostCache ?: SecureArchiveHost.fromJson(_secondaryHost).apply { _secondaryHostCache = this }
            set(v) {
                _secondaryHostCache = v
                _secondaryHost = v?.toJson()
            }
        var deviceName by spd.pref(Build.MODEL?:"Unknown")
        val isConfigured:Boolean get() = primaryHost != null || secondaryHost != null

        fun updateHost(oldHost: SecureArchiveHost, newHost: SecureArchiveHost) {
            if (primaryHost?.isSameHost(oldHost)==true) {
                primaryHost = newHost
            } else if (secondaryHost?.isSameHost(oldHost)==true) {
                secondaryHost = newHost
            }
        }
        fun getPairedHost(serviceName:String): SecureArchiveHost? {
            return hosts.firstOrNull { it.serviceName == serviceName }
        }

        val hasPairedHost:Boolean get() = hosts.any { it.serviceName!=null }

//        fun tryUpdatePairedHost(newHost: SecureArchiveHost) {
//            if (SecureArchiveHost.isSamePairedHost(primaryHost, newHost)) {
//                primaryHost = newHost
//            }
//            if (SecureArchiveHost.isSamePairedHost(secondaryHost, newHost)) {
//                secondaryHost = newHost
//            }
//        }

        val hosts:Sequence<SecureArchiveHost> get() = sequence<SecureArchiveHost> {
            val primary = primaryHost
            if (primary!=null) {
                yield(primary)
            }
            val secondary = secondaryHost
            if(secondary!=null) {
                yield(secondary)
            }
        }

        fun reset() {
            primaryHost = null
            secondaryHost = null
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
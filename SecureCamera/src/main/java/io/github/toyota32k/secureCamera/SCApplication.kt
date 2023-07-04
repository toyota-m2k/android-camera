package io.github.toyota32k.secureCamera

import android.app.Application
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.UtDialogConfig
import io.github.toyota32k.dialog.UtStandardString
import io.github.toyota32k.secureCamera.db.MetaDB
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.UtLog

class SCApplication : Application() {
    companion object {
        lateinit var instance:SCApplication
            private set
        val logger = UtLog("SC", null, SCApplication::class.java)
    }

    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        Settings.initialize(this)
        MetaDB.initialize(this)
        UtDialogConfig.solidBackgroundOnPhone = false   // phone の場合も、ダイアログの背景を灰色にしない
        UtDialogConfig.defaultGuardColorOfCancellableDialog = UtDialog.GuardColor.SEE_THROUGH.color
        UtStandardString.setContext(this)

    }

//    class FatalErrorHandler : Thread.UncaughtExceptionHandler {
//        private val orgHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
//        init {
//            Thread.setDefaultUncaughtExceptionHandler(this)
//        }
//        override fun uncaughtException(t: Thread, e: Throwable) {
//            logger.error(e, "Uncaught Exception")
//            orgHandler?.uncaughtException(t,e)
//        }
//    }
}
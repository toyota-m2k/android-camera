package io.github.toyota32k.secureCamera

import android.app.Application
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
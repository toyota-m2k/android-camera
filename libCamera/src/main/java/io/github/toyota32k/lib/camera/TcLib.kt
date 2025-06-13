package io.github.toyota32k.lib.camera

import android.app.Application
import android.content.Context
import io.github.toyota32k.logger.UtLog

object TcLib {
    val logger = UtLog("CameraLib", null, "io.github.toyota32k.")
    lateinit var applicationContext: Application

    fun initialize(context: Context) {
        applicationContext = context.applicationContext as Application
    }
}
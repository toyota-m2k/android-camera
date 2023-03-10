package io.github.toyota32k.camera.lib

import android.app.Application
import android.content.Context
import io.github.toyota32k.utils.UtLog

object TcLib {
    val logger = UtLog("CameraLib", null, "io.github.toyota32k.")
    lateinit var applicationContext: Application

    fun initialize(context: Context) {
        applicationContext = context.applicationContext as Application
    }
}
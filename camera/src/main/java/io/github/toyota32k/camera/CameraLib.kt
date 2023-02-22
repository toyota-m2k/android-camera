package io.github.toyota32k.camera

import android.content.Context
import io.github.toyota32k.utils.UtLog

object CameraLib {
    val logger = UtLog("CameraLib", null, "io.github.toyota32k.")
    lateinit var applicationContext: Context
}
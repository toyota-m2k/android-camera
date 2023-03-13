package io.github.toyota32k.lib.camera

import android.content.Context
import androidx.camera.core.CameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.concurrent.futures.await
import io.github.toyota32k.utils.UtLog

class TcCameraExtensions(val applicationContext: Context, val cameraProvider:CameraProvider) {
    enum class Mode(val mode:Int) {
        NONE(ExtensionMode.NONE),
        BOKEH(ExtensionMode.BOKEH),
        HDR(ExtensionMode.HDR),
        NIGHT(ExtensionMode.NIGHT),
        FACE_RETOUCH(ExtensionMode.FACE_RETOUCH),
        AUTO(ExtensionMode.AUTO);
    }

    lateinit var extensionsManager:ExtensionsManager

    suspend fun prepare(): TcCameraExtensions {
        extensionsManager = ExtensionsManager.getInstanceAsync(applicationContext, cameraProvider)
            .await()
        return this
    }

    fun capabilitiesOf(cameraSelector:CameraSelector):List<Mode> {
        return Mode.values().filter {
            extensionsManager.isExtensionAvailable(cameraSelector, it.mode)
        }
    }

    fun capabilitiesOf(front:Boolean):List<Mode> {
        return capabilitiesOf(if(front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA)
    }

    fun applyExtensionTo(mode: Mode, cameraSelector:CameraSelector) : CameraSelector {
        return if(extensionsManager.isExtensionAvailable(cameraSelector, mode.mode)) {
            extensionsManager.getExtensionEnabledCameraSelector(cameraSelector, mode.mode)
        } else {
            cameraSelector
        }
    }

    companion object {
        val logger: UtLog = TcLib.logger
    }

//    companion object {
//        suspend fun getCameraSelector(applicationContext: Context, cameraProvider:CameraProvider, cameraSelector:CameraSelector, mode: Mode):CameraSelector {
//            return if(mode== Mode.NONE) {
//                cameraSelector
//            } else {
//                CameraExtensions(applicationContext,cameraProvider,cameraSelector).prepare().applyExtension(mode)
//            }
//        }
//    }


}
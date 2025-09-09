package io.github.toyota32k.lib.camera

import android.content.Context
import androidx.camera.core.CameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.concurrent.futures.await
import io.github.toyota32k.lib.camera.TcFacing.Companion.cameraSelector
import io.github.toyota32k.logger.UtLog

/**
 * TcCameraExtensions
 * androidx.camera.extensions.ExtensionsManager, ExtensionMode のカプセル化
 * applyExtensionTo()メソッドを使って、選択された拡張モードをサポートするCameraSelectorを作成できる。
 *
 * 初期化方法
 * val cameraProvider = ProcessCameraProvider.getInstance(application).await()
 * val extensions = TcCameraExtensions(applicationContext, cameraProvider).prepare()
 *
 * 尚、TcCameraManager#cameraExtensions として常設されているので、通常はこれを使うので、個別に初期化する必要はない。
 *
 */
class TcCameraExtensions(val applicationContext: Context, private val cameraProvider:CameraProvider) {
    enum class Mode(val mode:Int) {
        NONE(ExtensionMode.NONE),       // エフェクトなし
        BOKEH(ExtensionMode.BOKEH),     // 背景をぼかす
        HDR(ExtensionMode.HDR),         // HDRモード
        NIGHT(ExtensionMode.NIGHT),     // 入光の少ない条件用
        FACE_RETOUCH(ExtensionMode.FACE_RETOUCH),   // 顔の色味を自動調整
        AUTO(ExtensionMode.AUTO);       // 自動（vendor依存）
    }

    private lateinit var extensionsManager:ExtensionsManager

    /**
     * 初期化（suspendなので、２段階初期化）
     */
    suspend fun prepare(): TcCameraExtensions {
        extensionsManager = ExtensionsManager.getInstanceAsync(applicationContext, cameraProvider)
            .await()
        return this
    }

    /**
     * CameraSelectorで指定されたカメラで利用可能な拡張モードのリストを取得
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun capabilitiesOf(cameraSelector:CameraSelector):List<Mode> {
        return Mode.entries.filter {
            extensionsManager.isExtensionAvailable(cameraSelector, it.mode)
        }
    }

    /**
     * フロントカメラ、または、リアカメラで利用可能な拡張モードのリストを取得
     */
    fun capabilitiesOf(facing:TcFacing):List<Mode> {
        return capabilitiesOf(facing.cameraSelector)
    }

    /**
     * 拡張モード（mode)は、カメラ（cameraSelector)に適用可能か？
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun canApplyExtensionTo(mode: Mode, cameraSelector:CameraSelector) : Boolean {
        return extensionsManager.isExtensionAvailable(cameraSelector, mode.mode)
    }

    /**
     * カメラ（cameraSelectorで指定）に、拡張モードを指定する。
     * 与えられた cameraSelectorをもとにして、拡張モードを設定した新しいCameraSelectorインスタンスを返す。
     * 与えられたモードが利用できない場合は何もしない。
     * 必要なら、事前にcanApplyExtensionTo()や、capabilitiesOf() でチェックする。
     */
    fun applyExtensionTo(mode: Mode, cameraSelector:CameraSelector) : CameraSelector {
        return if(canApplyExtensionTo(mode, cameraSelector)) {
            extensionsManager.getExtensionEnabledCameraSelector(cameraSelector, mode.mode)
        } else {
            cameraSelector
        }
    }

    companion object {
        val logger: UtLog = TcLib.logger
    }
}
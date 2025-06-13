package io.github.toyota32k.lib.camera

import androidx.camera.core.TorchState
import androidx.lifecycle.asFlow
import io.github.toyota32k.utils.IUtPropOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * フラッシュライトの制御
 *
 * TcCameraManager#createCamera() でカメラを作成して利用する場合は、
 * TcCameraManager#torch フィールドが利用できる。この場合は、attach/detach が createCamera/unbind で処理される。
 * }
 */
class TcTorch(camera: TcCamera? = null) : IUtPropOwner {
    data class TorchInfo(
        val isAvailable:Boolean,
        val isOn:Boolean
    )
    private val mCamera = MutableStateFlow<TcCamera?>(null)
    private val cameraInfo get() = mCamera.value?.cameraInfo
    private val isTorchOnNow:Boolean get() = cameraInfo?.torchState?.value == TorchState.ON

    /**
     * 選択されたカメラでフラッシュライトが利用可能かどうかをリアルタイムに流すflow
     */
    val isTorchAvailable:Flow<Boolean> = mCamera.map { it?.camera?.cameraInfo?.hasFlashUnit() == true }

    /**
     * フラッシュライト on/off 状態をリアルタイムに流すflow
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isTouchOn: Flow<Boolean> = mCamera.map { it?.cameraInfo?.torchState?.asFlow() }
        .flatMapLatest { it ?: emptyFlow() }
        .map { it == TorchState.ON }

    /**
     * 現在のフラッシュライトの情報を返す
     */
    val currentTorchInfo:TorchInfo
        get() = cameraInfo?.run {
            TorchInfo(hasFlashUnit(), torchState.value==TorchState.ON) } ?: TorchInfo(false,false)

    init {
        if(camera!=null) {
            mCamera.value = camera
        }
    }

    /**
     * すでにアタッチされているカメラがあればデタッチしてから、
     * カメラをTcTorchにアタッチする
     */
    fun attach(camera:TcCamera) {
        detach()
        mCamera.value = camera
    }

    /**
     * カメラをTcTorchから切り離す。
     * フラッシュライト点灯中なら消灯する。
      */
    fun detach() {
        mCamera.value?.let { c->
            if(c.camera.cameraInfo.torchState.value == TorchState.ON) {
                c.camera.cameraControl.enableTorch(false)
            }
            mCamera.value = null
        }
    }

    /**
     * フラッシュライトを on/offする
     */
    fun put(on:Boolean) {
        mCamera.value?.camera?.cameraControl?.enableTorch(on)
    }

    /**
     * フラッシュライトを点灯する
     */
    fun putOn() = put(true)

    /**
     * フラッシュライトを消灯する
     */
    fun putOff() {
        put(false)
    }

    /**
     * フラッシュライトの on/off をトグルする。
     */
    fun toggle() {
        put(!isTorchOnNow)
    }
}
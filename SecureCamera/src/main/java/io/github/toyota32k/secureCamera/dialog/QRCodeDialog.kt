package io.github.toyota32k.secureCamera.dialog

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.View
import androidx.lifecycle.application
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtAndroidViewModel
import io.github.toyota32k.dialog.task.UtAndroidViewModel.Companion.createAndroidViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.databinding.DialogQrCodeBinding
import io.github.toyota32k.utils.lifecycle.ConstantLiveData
import io.github.toyota32k.utils.utAssert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class QRCodeDialog : UtDialogEx() {
    companion object {
        fun hasCamera(context:Context):Boolean {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            return cm.cameraIdList.size > 0
        }

        suspend fun show(title:String?, prompt:String?, preCheck:(String)->Boolean):String? {
            return UtImmortalTask.awaitTaskResult("PairingTask") {
                val vm = createAndroidViewModel<QRCodeViewModel> {
                    if(title!=null) it.title.value = title
                    if(prompt!=null) it.promptMessage = prompt
                    it.preCheck = preCheck
                }
                val dlg = showDialog(taskName) { QRCodeDialog() }
                if(dlg.status.positive) vm.resultText else null
            }
        }
    }

    class QRCodeViewModel(application: Application) : UtAndroidViewModel(application), BarcodeCallback {
        var promptMessage:String = "Hold your camera on QR code."
        val title = MutableStateFlow<String>("QR Code")
        val decodedText = MutableStateFlow<String>("")
        var preCheck: ((String)->Boolean)? = null

        val cameraManager get() = application.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val numberOfCameras: Int get() = cameraManager.cameraIdList.size

        val initialCameraIndex: Int
            get() {
                val count = numberOfCameras
                if (count == 0) return -1
                if (count == 1) return 0
                val cameraIdList = cameraManager.cameraIdList
                for ((index, cameraId) in cameraIdList.withIndex()) {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        return index
                    }
                }
                return 0 // 見つからなければ0番目を返す
            }

        /**
         * 初期状態で選択するカメラIDを取得
         */
        var currentCameraIndex:Int = -1
            get() {
                if(field !in 0..<numberOfCameras) {
                    field = initialCameraIndex
                }
                return field
            }
            private set

        fun nextCameraIndex():Int {
            currentCameraIndex++
            if (currentCameraIndex>=numberOfCameras) {
                currentCameraIndex = 0
            }
            return currentCameraIndex
        }

        var resultText:String? = null
            private set
        val commandComplete = LiteUnitCommand()

        override fun barcodeResult(result: BarcodeResult?) {
            if (resultText!=null) return
            val tx = result?.text
            if(tx.isNullOrBlank()) return
            decodedText.value = tx
            if (preCheck?.invoke(tx) ?: true) {
                resultText = tx
                immortalTaskContext.coroutineScope.launch {
                    commandComplete.invoke()
                }
            }
        }
    }

    private lateinit var controls: DialogQrCodeBinding
    private val viewModel: QRCodeViewModel by lazy { getViewModel() }

    override fun preCreateBodyView() {
        rightButtonType = ButtonType.CANCEL
        leftButtonType = ButtonType.NONE
        widthOption = WidthOption.FULL
        heightOption = HeightOption.FULL
        cancellable = true
    }

    override fun createBodyView(
        savedInstanceState: Bundle?,
        inflater: IViewInflater
    ): View {
        utAssert(hasCamera(context)) { "check before show this dialog." }
        controls = DialogQrCodeBinding.inflate(inflater.layoutInflater)
        binder
            .owner(this)
            .dialogTitle(viewModel.title)
            .textBinding(controls.qrCodeResult, viewModel.decodedText)
            .visibilityBinding(controls.qrCodeResult, viewModel.decodedText.map { it.isNotBlank()})
            .visibilityBinding(controls.cameraSwitchingBtn, ConstantLiveData(viewModel.numberOfCameras > 1))
            .bindCommand(viewModel.commandComplete) {
                onPositive()
            }
            .bindCommand(LiteUnitCommand(::onSwitchCamera), controls.cameraSwitchingBtn)
        initializeQRCodeView()
        return controls.root
    }

    enum class ScanType(val code: Int) {
        Straight(0),
        Inverted(1),
        Mixed(2)
    }

    private fun initializeQRCodeView() {
        logger.debug()
        val formats = listOf(BarcodeFormat.QR_CODE)
        val intent = Intent().apply {
            putExtra(Intents.Scan.PROMPT_MESSAGE, viewModel.promptMessage)
            putExtra(Intents.Scan.CHARACTER_SET, "UTF-8")
            putExtra(Intents.Scan.SCAN_TYPE, ScanType.Mixed.code)
            putExtra(Intents.Scan.CAMERA_ID, viewModel.currentCameraIndex)
        }
        controls.barcodeScanner.barcodeView.decoderFactory = DefaultDecoderFactory(formats)
        controls.barcodeScanner.initializeFromIntent(intent)
        controls.barcodeScanner.decodeContinuous(viewModel)
        controls.barcodeScanner.resume()
    }

    private fun onSwitchCamera() {
        controls.barcodeScanner.pauseAndWait()
        controls.barcodeScanner.barcodeView.cameraSettings.requestedCameraId = viewModel.nextCameraIndex()
        controls.barcodeScanner.decodeContinuous(viewModel)
        controls.barcodeScanner.resume()
    }

    override fun onPause() {
        controls.barcodeScanner.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        controls.barcodeScanner.resume()
    }

    override fun onDestroyView() {
        controls.barcodeScanner.pauseAndWait()
        super.onDestroyView()
    }
}



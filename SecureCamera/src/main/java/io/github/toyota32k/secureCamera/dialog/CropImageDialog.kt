package io.github.toyota32k.secureCamera.dialog

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.observe
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogConfig
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.databinding.DialogSnapshotBinding
import io.github.toyota32k.secureCamera.utils.BitmapStore
import io.github.toyota32k.utils.Disposer
import io.github.toyota32k.utils.android.FitMode
import io.github.toyota32k.utils.android.UtFitter
import io.github.toyota32k.utils.android.px2dp
import io.github.toyota32k.utils.android.setLayoutSize
import io.github.toyota32k.utils.lifecycle.disposableObserve
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class CropImageDialog : UtDialogEx() {


    class CropImageViewModel : UtDialogViewModel() {
        val bitmapStore = BitmapStore()
        lateinit var bitmapScaler: RealTimeBitmapScaler
        val deflating = MutableStateFlow(false)

        val targetBitmap get() = bitmapScaler.bitmap.value
        var maskViewModel = CropMaskViewModel()
        private val cropFlows = maskViewModel.enableCropFlow(100, 100)

        val sizeText by lazy {
            combine(cropFlows.cropWidth, cropFlows.cropHeight, bitmapScaler.bitmap) { cw, ch, bmp ->
                "$cw x $ch (${bmp.width} x ${bmp.height})"
            }
        }

        fun crop(): Bitmap {
            return maskViewModel.cropBitmap(bitmapScaler.bitmap.value)
        }

        private val disposer = Disposer()

        fun setup(bitmap: Bitmap, maskParams: MaskCoreParams?): CropImageViewModel {
            bitmapScaler = RealTimeBitmapScaler(bitmap, bitmapStore)
            if (maskParams!=null) {
                maskViewModel.setParams(maskParams)
            }
            disposer.register(
                bitmapStore,
                bitmapScaler.apply { start(viewModelScope) },
                bitmapScaler.bitmap.disposableObserve {
                    maskViewModel.enableCropFlow(it.width, it.height)
                }
            )
            return this
        }

        override fun onCleared() {
            super.onCleared()
            disposer.dispose()
        }
    }

    override fun preCreateBodyView() {
        noHeader = true
        heightOption = HeightOption.FULL
        widthOption = WidthOption.FULL
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.OK
        systemZoneOption = UtDialogConfig.SystemZoneOption.CUSTOM_INSETS
    }

    lateinit var controls: DialogSnapshotBinding
    private val viewModel: CropImageViewModel by lazy { getViewModel() }

    private fun Int.dp():Int {
        return context.px2dp(this)
    }

    private fun fitBitmap(bitmap:Bitmap, containerWidth:Int, containerHeight:Int) {
        val w = containerWidth - (controls.root.paddingLeft + controls.root.paddingRight)
        val h = containerHeight - (controls.root.paddingTop + controls.root.paddingBottom)
        val fitter = UtFitter(FitMode.Inside, w, h)
        val size = fitter.fit(bitmap.width, bitmap.height).result.asSize
        logger.debug("root size = ${w.dp()} x ${h.dp()} (${controls.root.width.dp()}x${controls.root.height.dp()}) --> container size = ${size.width.dp()} x ${size.height.dp()} (image size = ${viewModel.targetBitmap.width} x ${viewModel.targetBitmap.height})")
        controls.imageContainer.setLayoutSize(size.width, size.height)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogSnapshotBinding.inflate(inflater.layoutInflater)
        controls.cropOverlay.bindViewModel(viewModel.maskViewModel)
        binder.owner(this)
            .textBinding(controls.sizeText, viewModel.sizeText)
            .visibilityBinding(controls.resolutionPanel, viewModel.deflating)
            .clickBinding(controls.resolutionButton) {
                viewModel.deflating.value = !viewModel.deflating.value
            }
            .apply {
                viewModel.bitmapScaler.bindToSlider(this, controls.resolutionSlider, controls.buttonMinus, controls.buttonPlus,
                    mapOf(480 to controls.button480, 720 to controls.button720, 1280 to controls.button1280, 1920 to controls.button1920))
            }
            .observe(viewModel.bitmapScaler.bitmap) {
                controls.image.setImageBitmap(it)
                fitBitmap(it, controls.root.width, controls.root.height)
            }

        var pw = 0
        var ph = 0
        controls.root.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0 && (pw!=w || ph!=h)) {
                pw = w
                ph = h
                fitBitmap(viewModel.targetBitmap, w, h)
            }
        }
        return controls.root
    }

    override fun onDialogClosing() {
        controls.image.setImageBitmap(null)
        controls.imagePreview.setImageBitmap(null)
        super.onDialogClosing()
    }

    companion object {
        data class CropResult(
            val bitmap: Bitmap,
            val maskParams: MaskCoreParams
        )
        suspend fun cropBitmap(bitmap: Bitmap, maskParams: MaskCoreParams?): CropResult? {
            return UtImmortalTask.awaitTaskResult(this::class.java.name) {
                val vm = createViewModel<CropImageViewModel> { setup(bitmap, maskParams) }
                val dlg = showDialog(taskName) { CropImageDialog() }
                if(dlg.status.ok) {
                    CropResult(vm.crop(), vm.maskViewModel.getParams())
                } else {
                    null
                }
            }
        }
    }
}
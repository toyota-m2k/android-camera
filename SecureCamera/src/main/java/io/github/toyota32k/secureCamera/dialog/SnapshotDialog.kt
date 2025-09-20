package io.github.toyota32k.secureCamera.dialog

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.observe
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogSnapshotBinding
import io.github.toyota32k.utils.android.FitMode
import io.github.toyota32k.utils.android.UtFitter
import io.github.toyota32k.utils.android.setLayoutSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class SnapshotDialog : UtDialogEx() {
    class SnapshotViewModel : UtDialogViewModel() {
        var createdBitmap: Bitmap? = null
        val targetBitmap = MutableStateFlow<Bitmap?>(null)
        val enableTrimming = MutableStateFlow(false)
        val maskViewModel = CropMaskViewModel()
        private val cropFlows = maskViewModel.enableCropFlow(100, 100)

        val sizeText = combine(enableTrimming, targetBitmap, cropFlows.cropWidth, cropFlows.cropHeight) { trimming, bmp, cw, ch->
            if(bmp==null) {
                ""
            } else if(trimming) {
                "$cw x $ch"
            } else {
                "${bmp.width} x ${bmp.height}"
            }
        }

        fun setup(bitmap: Bitmap, maskParams: MaskCoreParams?): SnapshotViewModel {
            targetBitmap.value = bitmap
            if (maskParams!=null) {
                maskViewModel.setParams(maskParams)
            }
            maskViewModel.enableCropFlow(bitmap.width, bitmap.height)
            return this
        }
        fun crop(): Bitmap {
            val bmp = targetBitmap.value ?: throw IllegalStateException("no bitmap")
            return maskViewModel.cropBitmap(bmp).also {
                if (createdBitmap!=null && createdBitmap!=it) {
                    createdBitmap?.recycle()
                }
                createdBitmap = it
            }
        }

        override fun onCleared() {
            super.onCleared()
            if (createdBitmap!=null) {
                createdBitmap?.recycle()
                createdBitmap = null
            }
        }
    }
    override fun preCreateBodyView() {
        heightOption = HeightOption.FULL
        widthOption = WidthOption.FULL
        noHeader = true
        optionButtonType = ButtonType(getString(R.string.crop), positive=false)
        leftButtonType = ButtonType(getString(R.string.reject), positive=false)
        rightButtonType = ButtonType(getString(R.string.accept), positive=true)
    }

    private val viewModel: SnapshotViewModel by lazy { getViewModel() }
    private lateinit var controls: DialogSnapshotBinding

    override fun createBodyView(
        savedInstanceState: Bundle?,
        inflater: IViewInflater
    ): View {
        controls = DialogSnapshotBinding.inflate(inflater.layoutInflater, null, false)
//        controls.image.setImageBitmap(viewModel.targetBitmap.value)
        controls.cropOverlay.bindViewModel(viewModel.maskViewModel)

        binder
            .owner(this)
            .textBinding(controls.sizeText, viewModel.sizeText)
            .visibilityBinding(controls.cropOverlay, viewModel.enableTrimming)
            .dialogOptionButtonVisibility(viewModel.enableTrimming, BoolConvert.Inverse)
            .dialogLeftButtonString(viewModel.enableTrimming.map { if(it) getString(R.string.cancel) else getString(R.string.reject) })
            .dialogRightButtonString(viewModel.enableTrimming.map { if(it) getString(R.string.crop) else getString(R.string.accept) })
            .clickBinding(optionButton!!) { viewModel.enableTrimming.value = true }
            .clickBinding(leftButton) {
                if (viewModel.enableTrimming.value) {
                    viewModel.enableTrimming.value = false
                } else {
                    onNegative()
                }
            }
            .clickBinding(rightButton) {
                if (viewModel.enableTrimming.value) {
                    // crop
                    val cropped = viewModel.crop()
                    viewModel.targetBitmap.value = cropped
                    viewModel.enableTrimming.value = false
                } else {
                    viewModel.createdBitmap = null  // recycle しない
                    onPositive()
                }
            }
            .observe(viewModel.targetBitmap) {
                controls.image.setImageBitmap(it)
                if (it!=null) {
                    viewModel.maskViewModel.enableCropFlow(it.width, it.height)
                }
            }

        var pw:Int = 0
        var ph:Int = 0
        controls.root.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            var w = right - left
            var h = bottom - top
            val bitmap = viewModel.targetBitmap.value ?: return@addOnLayoutChangeListener
            if (w > 0 && h > 0 && (pw!=w || ph!=h)) {
                pw = w
                ph = h
                w -= (controls.root.paddingLeft + controls.root.paddingRight)
                h -= (controls.root.paddingTop + controls.root.paddingBottom)
                val fitter = UtFitter(FitMode.Inside, w, h)
                val size = fitter.fit(bitmap.width, bitmap.height).result.asSize
//                logger.debug("root size = ${w.dp()} x ${h.dp()} (${controls.root.width.dp()}x${controls.root.height.dp()}) --> container size = ${size.width.dp()} x ${size.height.dp()} (image size = ${viewModel.targetBitmap.width} x ${viewModel.targetBitmap.height})")
                controls.imageContainer.setLayoutSize(size.width, size.height)
            }
        }

        return controls.root
    }

    companion object {
        data class CropResult(
            val sourceBitmap: Bitmap,
            val bitmap: Bitmap,
            val maskParams: MaskCoreParams?
        ) {
            fun consume():Bitmap {
                if (sourceBitmap!=bitmap) {
                    sourceBitmap.recycle()
                }
                return bitmap
            }
        }
        suspend fun showBitmap(source: Bitmap, maskParams: MaskCoreParams?):CropResult? {
            return UtImmortalTask.awaitTaskResult(this::class.java.name) {
                val vm = createViewModel<SnapshotViewModel> { setup(source, maskParams) }
                if (showDialog(taskName) { SnapshotDialog() }.status.ok) {
                    CropResult(source, vm.targetBitmap.value!!, vm.maskViewModel.getParams())
                } else {
                    null
                }
            }
        }
    }
}
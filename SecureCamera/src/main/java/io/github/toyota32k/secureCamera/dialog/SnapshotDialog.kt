package io.github.toyota32k.secureCamera.dialog

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.combinatorialVisibilityBinding
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
        var croppedBitmap = MutableStateFlow<Bitmap?>(null)
        val isCropped = croppedBitmap.map { it!=null }
        lateinit var targetBitmap:Bitmap
        var result:CropResult? = null
        val trimmingNow = MutableStateFlow(false)
        val maskViewModel = CropMaskViewModel()
        private val cropFlows = maskViewModel.enableCropFlow(100, 100)

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

        val sizeText = combine(trimmingNow, cropFlows.cropWidth, cropFlows.cropHeight) { trimming, cw, ch->
            if(trimming) {
                "$cw x $ch"
            } else {
                "${targetBitmap.width} x ${targetBitmap.height}"
            }
        }

        fun resetCropped() {
            if (croppedBitmap.value!=null && croppedBitmap.value!=targetBitmap) {
                croppedBitmap.value?.recycle()
            }
            croppedBitmap.value = null
        }

        fun setup(bitmap: Bitmap, maskParams: MaskCoreParams?): SnapshotViewModel {
            targetBitmap = bitmap
            if (maskParams!=null) {
                maskViewModel.setParams(maskParams)
            }
            maskViewModel.enableCropFlow(bitmap.width, bitmap.height)
            return this
        }
        fun crop(): Bitmap {
            return maskViewModel.cropBitmap(targetBitmap).also {
                resetCropped()
                croppedBitmap.value = it
            }
        }

        fun fix() {
            result = CropResult(
                sourceBitmap = targetBitmap,
                bitmap = croppedBitmap.value ?: targetBitmap,
                maskParams = maskViewModel.getParams()
            ).also {
                croppedBitmap.value = null
            }
        }

        override fun onCleared() {
            super.onCleared()
            resetCropped()
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
        controls.cropOverlay.bindViewModel(viewModel.maskViewModel)
        controls.image.setImageBitmap(viewModel.targetBitmap)
        binder
            .owner(this)
            .textBinding(controls.sizeText, viewModel.sizeText)
            .visibilityBinding(controls.cropOverlay, viewModel.trimmingNow)
            .dialogOptionButtonVisibility(viewModel.trimmingNow, BoolConvert.Inverse)
            .dialogLeftButtonString(viewModel.trimmingNow.map { if(it) getString(R.string.cancel) else getString(R.string.reject) })
            .dialogRightButtonString(viewModel.trimmingNow.map { if(it) getString(R.string.crop) else getString(R.string.accept) })
            .combinatorialVisibilityBinding(viewModel.isCropped) {
                inverseGone(controls.image)
                straightGone(controls.imagePreview)
            }
            .clickBinding(optionButton!!) {
                viewModel.resetCropped()
                viewModel.trimmingNow.value = true
            }
            .clickBinding(leftButton) {
                if (viewModel.trimmingNow.value) {
                    viewModel.trimmingNow.value = false
                } else {
                    onNegative()
                }
            }
            .clickBinding(rightButton) {
                if (viewModel.trimmingNow.value) {
                    // crop
                    viewModel.crop()
                    viewModel.trimmingNow.value = false
                } else {
                    // accept --> detatch cropped bitmap to result
                    viewModel.fix()
                    onPositive()
                }
            }
            .observe(viewModel.croppedBitmap)  { bmp->
                controls.imagePreview.setImageBitmap(bmp)
            }

        var pw:Int = 0
        var ph:Int = 0
        controls.root.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            var w = right - left
            var h = bottom - top
            val bitmap = viewModel.targetBitmap
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
       suspend fun showBitmap(source: Bitmap, maskParams: MaskCoreParams?): SnapshotViewModel.CropResult? {
            return UtImmortalTask.awaitTaskResult(this::class.java.name) {
                val vm = createViewModel<SnapshotViewModel> { setup(source, maskParams) }
                if (showDialog(taskName) { SnapshotDialog() }.status.ok) {
                    vm.result
                } else {
                    null
                }
            }
        }
    }
}
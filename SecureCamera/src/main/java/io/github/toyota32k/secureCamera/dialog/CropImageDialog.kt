package io.github.toyota32k.secureCamera.dialog

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.databinding.DialogCropImageBinding
import io.github.toyota32k.secureCamera.databinding.DialogSnapshotBinding
import io.github.toyota32k.utils.android.FitMode
import io.github.toyota32k.utils.android.UtFitter
import io.github.toyota32k.utils.android.px2dp
import io.github.toyota32k.utils.android.setLayoutSize
import kotlinx.coroutines.flow.combine

class CropImageDialog : UtDialogEx() {
    class CropImageViewModel : UtDialogViewModel() {
        lateinit var targetBitmap: Bitmap
        var maskViewModel = CropMaskViewModel()
        private val cropFlows = maskViewModel.enableCropFlow(100, 100)

        val sizeText = combine(cropFlows.cropWidth, cropFlows.cropHeight) { cw, ch->
            "$cw x $ch (${targetBitmap.width} x ${targetBitmap.height})"
        }

        fun crop(): Bitmap {
            return maskViewModel.cropBitmap(targetBitmap)
        }

        fun setup(bitmap: Bitmap, maskParams: MaskCoreParams?): CropImageViewModel {
            targetBitmap = bitmap
            if (maskParams!=null) {
                maskViewModel.setParams(maskParams)
            }
            maskViewModel.enableCropFlow(bitmap.width, bitmap.height)
            return this
        }
    }

    override fun preCreateBodyView() {
        noHeader = true
        heightOption = HeightOption.FULL
        widthOption = WidthOption.FULL
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.OK
    }

    lateinit var controls: DialogSnapshotBinding
    private val viewModel: CropImageViewModel by lazy { getViewModel() }

    private fun Int.dp():Int {
        return context.px2dp(this)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogSnapshotBinding.inflate(inflater.layoutInflater)
        controls.image.setImageBitmap(viewModel.targetBitmap)
        controls.cropOverlay.bindViewModel(viewModel.maskViewModel)
        binder.owner(this).textBinding(controls.sizeText, viewModel.sizeText)

        var pw:Int = 0
        var ph:Int = 0
        controls.root.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            var w = right - left
            var h = bottom - top
            if (w > 0 && h > 0 && (pw!=w || ph!=h)) {
                pw = w
                ph = h
                w -= (controls.root.paddingLeft + controls.root.paddingRight)
                h -= (controls.root.paddingTop + controls.root.paddingBottom)
                val fitter = UtFitter(FitMode.Inside, w, h)
                val size = fitter.fit(viewModel.targetBitmap.width, viewModel.targetBitmap.height).result.asSize
                logger.debug("root size = ${w.dp()} x ${h.dp()} (${controls.root.width.dp()}x${controls.root.height.dp()}) --> container size = ${size.width.dp()} x ${size.height.dp()} (image size = ${viewModel.targetBitmap.width} x ${viewModel.targetBitmap.height})")
                controls.imageContainer.setLayoutSize(size.width, size.height)
            }
        }
        return controls.root
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
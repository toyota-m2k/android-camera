package io.github.toyota32k.secureCamera.dialog

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.databinding.DialogCropImageBinding
import io.github.toyota32k.utils.android.FitMode
import io.github.toyota32k.utils.android.UtFitter
import io.github.toyota32k.utils.android.px2dp
import io.github.toyota32k.utils.android.setLayoutSize

class CropImageDialog : UtDialogEx() {
    class CropImageViewModel : UtDialogViewModel() {
        lateinit var targetBitmap: Bitmap
        lateinit var maskViewModel: CropMaskViewModel

        fun crop(): Bitmap {
            return maskViewModel.cropBitmap(targetBitmap)
        }

        fun setup(bitmap: Bitmap): CropImageViewModel {
            targetBitmap = bitmap
            maskViewModel = CropMaskViewModel(bitmap.width, bitmap.height)
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

    lateinit var controls: DialogCropImageBinding
    private val viewModel: CropImageViewModel by lazy { getViewModel() }

    private fun Int.dp():Int {
        return context.px2dp(this)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogCropImageBinding.inflate(inflater.layoutInflater)
        controls.image.setImageBitmap(viewModel.targetBitmap)
        controls.cropOverlay.bindViewModel(viewModel.maskViewModel)
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
        suspend fun cropBitmap(bitmap: Bitmap): Bitmap? {
            return UtImmortalTask.awaitTaskResult(this::class.java.name) {
                val vm = createViewModel<CropImageViewModel> { setup(bitmap) }
                val dlg = showDialog(taskName) { CropImageDialog() }
                if(dlg.status.ok) {
                    vm.crop()
                } else {
                    null
                }
            }
        }
    }
}
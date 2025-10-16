package io.github.toyota32k.secureCamera.dialog

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.combinatorialVisibilityBinding
import io.github.toyota32k.binder.enableBinding
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
import io.github.toyota32k.secureCamera.dialog.CropImageDialog.Companion.popupAspectMenu
import io.github.toyota32k.secureCamera.utils.BitmapStore
import io.github.toyota32k.secureCamera.utils.onViewSizeChanged
import io.github.toyota32k.utils.Disposer
import io.github.toyota32k.utils.android.FitMode
import io.github.toyota32k.utils.android.UtFitter
import io.github.toyota32k.utils.android.setLayoutSize
import io.github.toyota32k.utils.lifecycle.disposableObserve
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SnapshotDialog : UtDialogEx() {
    class SnapshotViewModel : UtDialogViewModel() {
        val bitmapStore = BitmapStore()
        lateinit var bitmapScaler: RealTimeBitmapScaler
        val deflating = MutableStateFlow(false)

        val croppedBitmapFlow = MutableStateFlow<Bitmap?>(null)
        var cropBitmap: Bitmap?
            get() = croppedBitmapFlow.value
            set(v) {
                croppedBitmapFlow.value = bitmapStore.replaceNullable(croppedBitmapFlow.value, v)
            }


        val isCropped = croppedBitmapFlow.map { it != null }
//        lateinit var targetBitmap:Bitmap
//        var recycleTargetBitmap:Boolean = false

        val trimmingNow = MutableStateFlow(false)
        val maskViewModel = CropMaskViewModel()
        private val cropFlows = maskViewModel.enableCropFlow(100, 100)

        var result: CropResult? = null

        data class CropResult(
            val bitmap: Bitmap,
            val maskParams: MaskCoreParams?
        )

        private val croppedSize =
            combine(trimmingNow, croppedBitmapFlow, cropFlows.cropWidth, cropFlows.cropHeight) { trimmingNow, cropped, w, h ->
                if (trimmingNow) {
                    "$w x $h"
                } else if (cropped!=null) {
                    "${cropped.width} x ${cropped.height}"
                } else {
                    null
                }
            }
        private val baseSize by lazy {
            bitmapScaler.bitmap.map {
                "${it.width} x ${it.height}"
            }
        }

        val sizeText by lazy {
            combine(croppedSize, baseSize) { cropped, base ->
                if (cropped!=null) {
                    "$cropped  ($base)"
                } else {
                    base
                }
            }
        }

        private val disposer = Disposer()
        fun setup(bitmap: Bitmap, autoRecycle:Boolean, maskParams: MaskCoreParams?): SnapshotViewModel {
            bitmapScaler = RealTimeBitmapScaler(bitmap, bitmapStore)
            if (autoRecycle) {
                bitmapStore.attach(bitmap)
            }
            if (maskParams!=null) {
                maskViewModel.setParams(maskParams)
            }
            disposer.register(
                bitmapStore,
                bitmapScaler.apply {start(viewModelScope)},
                bitmapScaler.bitmap.disposableObserve {
                    maskViewModel.enableCropFlow(it.width, it.height)
                }
            )
            return this
        }

        fun crop(): Bitmap {
            return maskViewModel.cropBitmap(bitmapScaler.bitmap.value).also {
                cropBitmap = it
            }
        }

        fun fix() {
            val bitmap = cropBitmap ?: bitmapScaler.bitmap.value
            bitmapStore.detach(bitmap)
            result = CropResult(
                bitmap = bitmap,
                maskParams = maskViewModel.getParams()
            )
        }

        override fun onCleared() {
            super.onCleared()
            disposer.dispose()
        }

    }

    override fun preCreateBodyView() {
        cancellable = false
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
        controls.cropOverlay.bindViewModel(viewModel.maskViewModel, viewModel.viewModelScope)
//        controls.image.setImageBitmap(viewModel.targetBitmap)
        binder
            .owner(this)
            .textBinding(controls.sizeText, viewModel.sizeText)
            .textBinding(controls.aspectButton, viewModel.maskViewModel.aspectMode.map { it.label })
            .visibilityBinding(controls.resolutionPanel, viewModel.deflating)
            .enableBinding(controls.memoryRead, viewModel.maskViewModel.memory.map { it!=null }, BoolConvert.Straight, alphaOnDisabled=0.4f)
            .dialogLeftButtonString(viewModel.trimmingNow.map { if(it) getString(R.string.cancel) else getString(R.string.reject) })
            .dialogRightButtonString(viewModel.trimmingNow.map { if(it) getString(R.string.crop) else getString(R.string.accept) })
            .combinatorialVisibilityBinding(viewModel.isCropped) {
                inverseGone(controls.image)
                straightGone(controls.imagePreview)
            }
            .combinatorialVisibilityBinding(viewModel.trimmingNow) {
                straightGone(controls.cropOverlay,controls.aspectButton, controls.maxButton, controls.memoryPlus, controls.memoryRead)
                inverseGone(optionButton!!)
            }
            .apply {
                viewModel.bitmapScaler.bindToSlider(this, controls.resolutionSlider, controls.buttonMinus, controls.buttonPlus,
                    mapOf(480 to controls.button480, 720 to controls.button720, 1280 to controls.button1280, 1920 to controls.button1920))
            }
            .clickBinding(controls.maxButton) {
                controls.cropOverlay.resetCrop()
            }
            .clickBinding(controls.aspectButton) {
                lifecycleScope.launch {
                    val aspect = CropImageDialog.popupAspectMenu(context, it)
                    if(aspect!=null) {
                        viewModel.maskViewModel.aspectMode.value = aspect
                    }
                }
            }
            .clickBinding(controls.memoryPlus) {
                viewModel.maskViewModel.pushMemory()
            }
            .clickBinding(controls.memoryRead) {
                controls.cropOverlay.applyCropFromMemory()
            }
            .clickBinding(controls.resolutionButton) {
                viewModel.deflating.value = !viewModel.deflating.value
            }
            .clickBinding(optionButton!!) {
                viewModel.deflating.value = false
                viewModel.cropBitmap = null
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
            .observe(viewModel.croppedBitmapFlow)  { bmp->
                controls.imagePreview.setImageBitmap(bmp)
            }
            .observe(viewModel.bitmapScaler.bitmap) {
                controls.image.setImageBitmap(it)
                fitBitmap(it, controls.root.width, controls.root.height)
            }
            .onViewSizeChanged(controls.root) { w, h ->
                val bitmap = viewModel.bitmapScaler.bitmap.value
                fitBitmap(bitmap, w, h)
            }
        return controls.root
    }

    private fun fitBitmap(bitmap:Bitmap, containerWidth:Int, containerHeight:Int) {
        val paddingHorizontal = controls.image.paddingLeft + controls.image.paddingRight
        val paddingVertical = controls.image.paddingTop + controls.image.paddingBottom
        // image/image_preview/cropOverlay には同じ padding が設定されている
        // コンテナー領域から、そのpaddingを差し引いた領域内に、bitmapを最大表示したときのサイズを計算
        val w = containerWidth - paddingHorizontal
        val h = containerHeight - paddingVertical
        val fitter = UtFitter(FitMode.Inside, w, h)
        val size = fitter.fit(bitmap.width, bitmap.height).result.asSize
        // bitmapのサイズに padding を加えたサイズを imageContainerにセットする。
        controls.imageContainer.setLayoutSize(size.width+paddingHorizontal, size.height+paddingHorizontal)
    }

    override fun onDialogClosing() {
        controls.image.setImageBitmap(null)
        controls.imagePreview.setImageBitmap(null)
        super.onDialogClosing()
    }

    companion object {
       suspend fun showBitmap(source: Bitmap, autoRecycle:Boolean = true, maskParams: MaskCoreParams?=null): SnapshotViewModel.CropResult? {
            return UtImmortalTask.awaitTaskResult(this::class.java.name) {
                val vm = createViewModel<SnapshotViewModel> { setup(source, autoRecycle, maskParams) }
                if (showDialog(taskName) { SnapshotDialog() }.status.ok) {
                    vm.result
                } else {
                    null
                }
            }
        }
    }
}
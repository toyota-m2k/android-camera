package io.github.toyota32k.secureCamera.dialog

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.core.graphics.scale
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.observe
import io.github.toyota32k.binder.sliderBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.secureCamera.databinding.DialogSnapshotBinding
import io.github.toyota32k.utils.Disposer
import io.github.toyota32k.utils.IDisposable
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.android.FitMode
import io.github.toyota32k.utils.android.UtFitter
import io.github.toyota32k.utils.android.px2dp
import io.github.toyota32k.utils.android.setLayoutSize
import io.github.toyota32k.utils.lifecycle.disposableObserve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class CropImageDialog : UtDialogEx() {
    class RealTimeBitmapScaler(val sourceBitmap:Bitmap): IUtPropOwner, IDisposable {
        private val busy = AtomicBoolean(false)
        private var scaledBitmap:Bitmap? = null
        private var scaledWidth:Int = sourceBitmap.width
        private var scaledHeight:Int = sourceBitmap.height
        val bitmap: StateFlow<Bitmap> = MutableStateFlow<Bitmap>(sourceBitmap)
        val scale = MutableStateFlow<Float>(100f)
        var tryAgain = false
        var job: Job? = null



        fun start(coroutineScope: CoroutineScope) {
            job = coroutineScope.launch {
                scale.collect {
                    deflateBitmap(it/100f)
                }
            }
        }

        override fun dispose() {
            job?.cancel()
            job = null
            scaledBitmap?.recycle()
            scaledBitmap = null
        }

        private suspend fun deflateBitmap(newScale:Float) {
            if (busy.getAndSet(true)) {
                var s = newScale
                try {
                    while (true) {
                        tryAgain = false
                        val w = (sourceBitmap.width * s).toInt()
                        val h = (sourceBitmap.height * s).toInt()
                        if (w != scaledWidth || h != scaledHeight) {
                            if (w == sourceBitmap.width && h == sourceBitmap.height) {
                                scaledBitmap?.recycle()
                                scaledBitmap = null
                                bitmap.mutable.value = sourceBitmap
                            } else {
                                val bmp = withContext(Dispatchers.IO) { sourceBitmap.scale(w, h) }
                                bitmap.mutable.value = bmp
                                scaledBitmap?.recycle()
                                scaledBitmap = bmp
                                scaledWidth = w
                                scaledHeight = h
                            }
                        }
                        if (!tryAgain) break
                        s = scale.value /100f
                    }
                } finally {
                    busy.set(false)
                }
            } else {
                tryAgain = true
            }
        }
    }


    class CropImageViewModel : UtDialogViewModel() {
        //lateinit var targetBitmap: Bitmap
        lateinit var bitmapScaler: RealTimeBitmapScaler
        val targetBitmap get() = bitmapScaler.bitmap.value
        var maskViewModel = CropMaskViewModel()
        private val cropFlows = maskViewModel.enableCropFlow(100, 100)
        val deflating = MutableStateFlow(false)

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
            bitmapScaler = RealTimeBitmapScaler(bitmap)
            if (maskParams!=null) {
                maskViewModel.setParams(maskParams)
            }
            disposer.register(
                bitmapScaler.apply {start(viewModelScope)},
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
            .clickBinding(controls.buttonPlus) {
                viewModel.bitmapScaler.scale.value = (viewModel.bitmapScaler.scale.value + 1f).coerceAtMost(100f)
            }
            .clickBinding(controls.buttonMinus) {
                viewModel.bitmapScaler.scale.value = (viewModel.bitmapScaler.scale.value - 1f).coerceAtLeast(10f)
            }
            .sliderBinding(controls.resolutionSlider, viewModel.bitmapScaler.scale)
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
package io.github.toyota32k.secureCamera.dialog

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.bitmapBinding
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
import io.github.toyota32k.lib.media.editor.model.MaskCoreParams
import io.github.toyota32k.lib.media.editor.model.RealTimeBitmapScaler
import io.github.toyota32k.secureCamera.R
import io.github.toyota32k.secureCamera.databinding.DialogSnapshotBinding
import io.github.toyota32k.secureCamera.utils.onViewSizeChanged
import io.github.toyota32k.utils.Disposer
import io.github.toyota32k.utils.IDisposable
import io.github.toyota32k.utils.android.FitMode
import io.github.toyota32k.utils.android.RefBitmap
import io.github.toyota32k.utils.android.RefBitmap.Companion.toRef
import io.github.toyota32k.utils.android.RefBitmapFlow
import io.github.toyota32k.utils.android.RefBitmapHolder
import io.github.toyota32k.utils.android.UtFitter
import io.github.toyota32k.utils.android.setLayoutSize
import io.github.toyota32k.utils.lifecycle.disposableObserve
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.Closeable

class SnapshotDialog : UtDialogEx() {
    class SnapshotViewModel : UtDialogViewModel() {
//        val bitmapStore = BitmapStore()
        lateinit var bitmapScaler: RealTimeBitmapScaler
        val deflating = MutableStateFlow(false)

        val croppedBitmapFlow = RefBitmapFlow(null)
        var cropBitmap: RefBitmap?
            get() = croppedBitmapFlow.value
            set(v) { croppedBitmapFlow.value = v }


        val isCropped = croppedBitmapFlow.map { it != null }
//        val targetBitmap: Flow<RefBitmap?> = combine(croppedBitmapFlow, bitmapScaler.bitmap) { c,s->
//            if (c?.hasBitmap==true) c else s
//        }
//        lateinit var targetBitmap:Bitmap
//        var recycleTargetBitmap:Boolean = false

        val trimmingNow = MutableStateFlow(false)
        val maskViewModel = CropMaskViewModel()
        private val cropFlows = maskViewModel.enableCropFlow(100, 100)

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
                if (it!=null) "${it.width} x ${it.height}" else ""
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
        fun setup(bitmap: RefBitmap, maskParams: MaskCoreParams?): SnapshotViewModel {
            bitmapScaler = RealTimeBitmapScaler().apply { setSource(bitmap)}
            if (maskParams!=null) {
                maskViewModel.setParams(maskParams)
            }
            disposer.register(
                bitmapScaler,
                bitmapScaler.bitmap.disposableObserve {
                    if (it!=null) {
                        maskViewModel.enableCropFlow(it.width, it.height)
                    }
                }
            )
            return this
        }

        fun crop(): Bitmap? {
            return bitmapScaler.bitmap.value?.use { bmp ->
                maskViewModel.cropBitmap(bmp).also {
                    cropBitmap = it.toRef()
                }
            }
        }

        var result: CropResult? = null

        class CropResult(
            bitmap: RefBitmap?,
            var maskParams: MaskCoreParams?
        ): Closeable {
            var bitmap: RefBitmap? by RefBitmapHolder(bitmap)
            override fun close() {
                bitmap = null
            }

        }


        fun fix() {
            val bitmap = cropBitmap ?: bitmapScaler.bitmap.value
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
        controls.image.setImageBitmap(viewModel.bitmapScaler.sourceBitmap?.bitmap)
        viewModel.bitmapScaler.enable(lifecycleScope)
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
                viewModel.bitmapScaler.bindView(this, controls.resolutionSlider, controls.buttonMinus, controls.buttonPlus,
                    mapOf(480 to controls.button480, 720 to controls.button720, 1280 to controls.button1280, 1920 to controls.button1920))
            }
            .clickBinding(controls.maxButton) {
                controls.cropOverlay.resetCrop()
            }
            .clickBinding(controls.aspectButton) {
                lifecycleScope.launch {
                    val aspect = popupAspectMenu(context, it)
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
            .bitmapBinding(controls.imagePreview, viewModel.croppedBitmapFlow)

            .observe(viewModel.bitmapScaler.bitmap) {
                it?.use { bitmap ->
                    controls.image.setImageBitmap(bitmap)
                    fitBitmap(bitmap, controls.root.width, controls.root.height)
                }
            }
            .onViewSizeChanged(controls.root) { w, h ->
                viewModel.bitmapScaler.bitmap.value?.use { bitmap->
                    fitBitmap(bitmap, w, h)
                }
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
        private suspend fun popupAspectMenu(context: Context, anchor: View):CropMaskViewModel.AspectMode? {
            val selection = MutableStateFlow<Int?>(null)
            PopupMenu(context, anchor).apply {
                setOnMenuItemClickListener {
                    selection.value = it.itemId
                    true
                }
                setOnDismissListener {
                    selection.value = -1
                }
                inflate(R.menu.menu_aspect)
            }.show()
            val sel = selection.first { it != null }
            return when(sel) {
                R.id.aspect_free -> CropMaskViewModel.AspectMode.FREE
                R.id.aspect_4_3 -> CropMaskViewModel.AspectMode.ASPECT_4_3
                R.id.aspect_16_9 -> CropMaskViewModel.AspectMode.ASPECT_16_9
                else -> null
            }
        }

       suspend fun showBitmap(source: RefBitmap, maskParams: MaskCoreParams?=null): SnapshotViewModel.CropResult? {
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
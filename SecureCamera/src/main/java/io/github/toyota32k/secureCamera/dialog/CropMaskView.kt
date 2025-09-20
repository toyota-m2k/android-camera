package io.github.toyota32k.secureCamera.dialog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.drawable.toDrawable
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.utils.android.dp2px
import io.github.toyota32k.utils.android.getLayoutHeight
import io.github.toyota32k.utils.android.getLayoutWidth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * CropMaskView の位置情報を表すデータクラス
 *
 * rsx, rsy: mask の左上の位置 (0.0～1.0)
 * rex, rey: mask の右下の位置 (0.0～1.0)
 */
data class MaskCoreParams(
    val rsx:Float,
    val rsy:Float,
    val rex:Float,
    val rey:Float,
) {
    companion object {
        fun fromSize(sourceWidth:Int, sourceHeight:Int, sx:Float, sy:Float, w:Float, h:Float):MaskCoreParams {
            return MaskCoreParams(
                (sx / sourceWidth.toFloat()).coerceIn(0f, 1f),
                (sy / sourceHeight.toFloat()).coerceIn(0f, 1f),
                ((sx+w) / sourceWidth.toFloat()).coerceIn(0f, 1f),
                ((sy+h) / sourceHeight.toFloat()).coerceIn(0f, 1f),
            )
        }
    }
}

/**
 * CropMask 用の ViewModel
 * @param sourceWidth ソース画像の幅 (px)
 * @param sourceHeight ソース画像の高さ (px)
 */
class CropMaskViewModel {
    companion object {
//        const val MIN = 32f
    }
    // invalidateが必要かどうか
    var isDirty: Boolean = false

    // isDirty が true の場合に fn() を実行し、isDirty を false にする
    // usage: viewModel.clearDirty { invalidate() }
    fun clearDirty(fn:()->Unit) {
        if(isDirty) {
            fn()
            isDirty = false
            cropFlow?.update()
        }
    }
    // Mask用位置データ
    // 0.0～1.0 の範囲で、ソース画像に対する相対位置を表す
    // これがコアデータで、そのほかのパラメータはこの値から導出する。
    private var rsx:Float = 0f
        set(v) {
            if(v!=field) {
                field = v
                isDirty = true
            }
        }
    private var rsy:Float = 0f
        set(v) {
            if(v!=field) {
                field = v
                isDirty = true
            }
        }

    private var rex:Float = 1f
        set(v) {
            if(v!=field) {
                field = v
                isDirty = true
            }
        }
    private var rey:Float = 1f
        set(v) {
            if(v!=field) {
                field = v
                isDirty = true
            }
        }

    // view の padding (mask のハンドルの半径に相当)
    var padding:Int = 10
        private set(v) {
            val nv = v.coerceAtLeast(10)
            if(nv!=field) {
                field = nv
                isDirty = true
            }
        }

    // padding 込みの view サイズ
    var rawViewWidth:Int = 100
        private set(v) {
            if(v!=field) {
                field = v
                isDirty = true
            }
        }
    var rawViewHeight:Int = 100
        private set(v) {
            if(v!=field) {
                field = v
                isDirty = true
            }
        }

    // view のサイズと padding を設定する
    fun setViewDimmension(width:Int, height:Int, padding:Int) {
        this.rawViewWidth = width
        this.rawViewHeight = height
        this.padding = padding
    }

    // padding を除いた view サイズ
    val viewWidth:Int
        get() = (rawViewWidth - padding*2).coerceAtLeast(1)
    val viewHeight:Int
        get() = (rawViewHeight - padding*2).coerceAtLeast(1)
    val minMaskWidth:Float
        get() = padding * 2f

    // View上での mask の位置 (px 単位、padding 込み)
    var maskSx:Float
        get() = rsx * viewWidth + padding
        private set(v) { rsx = (v-padding) / viewWidth }
    fun setMaskSx(v:Float):Float {
        return v.coerceIn(padding.toFloat(), maskEx-minMaskWidth+padding).also { maskSx = it }
    }
    var maskSy:Float
        get() = rsy * viewHeight + padding
        private set(v) { rsy = (v-padding) / viewHeight }
    fun setMaskSy(v:Float):Float {
        return  v.coerceIn(padding.toFloat(), maskEy-minMaskWidth+padding).also { maskSy = it }
    }
    var maskEx:Float
        get() = rex * viewWidth + padding
        private set(v) { rex = (v-padding) / viewWidth }
    fun setMaskEx(v:Float):Float {
        return v.coerceIn(maskSx+minMaskWidth+padding, viewWidth.toFloat()+padding).also { maskEx = it }
    }
    var maskEy:Float
        get() = rey * viewHeight + padding
        private set(v) { rey = (v-padding) / viewHeight }
    fun setMaskEy(v:Float):Float {
        return v.coerceIn(maskSy+minMaskWidth+padding, viewHeight.toFloat()+padding).also { maskEy = it }
    }

    // mask の幅と高さ
    val maskWidth:Float
        get() = maskEx - maskSx
    val maskHeight:Float
        get() = maskEy - maskSy

    //    fun moveMask(dx:Float, dy:Float) {
//        val w = maskWidth
//        val h = maskHeight
//        val newSx = (maskSx + dx).coerceIn(0f, viewWidth - w)
//        val newSy = (maskSy + dy).coerceIn(0f, viewHeight - h)
//        maskSx = newSx
//        maskSy = newSy
//        maskEx = newSx + w
//        maskEy = newSy + h
//    }
    /**
     * maskのサイズを維持したまま、左上の座標を (x,y) に移動する。
     *
     * mask が view の範囲を超える場合は、view 内に収まるように調整する。
     * @param x mask の左上の X座標 (padding 込み)
     * @param y mask の左上の Y座標 (padding 込み)
     */
    fun moveTo(x:Float, y:Float) {
        val w = maskWidth
        val h = maskHeight
        val newSx = x.coerceIn(padding.toFloat(), viewWidth - w + padding)
        val newSy = y.coerceIn(padding.toFloat(), viewHeight - h + padding)
        maskSx = newSx
        maskSy = newSy
        maskEx = newSx + w
        maskEy = newSy + h
    }

    interface ICropFlows {
        val cropSx: StateFlow<Int>
        val cropSy: StateFlow<Int>
        val cropWidth: StateFlow<Int>
        val cropHeight: StateFlow<Int>
    }
    inner class CropFlows : ICropFlows {
        override val cropSx = MutableStateFlow(0)
        override val cropSy = MutableStateFlow(0)
        override val cropWidth = MutableStateFlow(0)
        override val cropHeight = MutableStateFlow(0)

        var width:Float = 0f
        var height:Float = 0f

        fun setSize(width:Float, height:Float):CropFlows {
            this.width = width
            this.height = height
            update()
            return this
        }

        fun update() {
            val sx = (rsx * width.toFloat()).roundToInt()
            val sy = (rsy * height.toFloat()).roundToInt()
            val w = ((rex-rsx) * width.toFloat()).roundToInt()
            val h = ((rey-rsy) * height.toFloat()).roundToInt()
            if(cropSx.value != sx) cropSx.value = sx
            if(cropSy.value != sy) cropSy.value = sy
            if(cropWidth.value != w) cropWidth.value = w
            if(cropHeight.value != h) cropHeight.value = h
        }
        init {
            update()
        }
    }
    private var cropFlow :CropFlows? = null
    fun enableCropFlow(width:Int, height:Int):ICropFlows {
        return (cropFlow ?: CropFlows().apply {cropFlow=this}).setSize(width.toFloat(), height.toFloat())
    }

    data class CropRect(val sx:Int, val sy:Int, val width:Int, val height:Int)
    fun cropRect(width:Int, height:Int):CropRect {
        return CropRect(
            (rsx * width.toFloat()).roundToInt(),
            (rsy * height.toFloat()).roundToInt(),
            ((rex-rsx) * width.toFloat()).roundToInt(),
            ((rey-rsy) * height.toFloat()).roundToInt(),
        )
    }
    fun cropRect(bitmap:Bitmap):CropRect {
        return cropRect(bitmap.width, bitmap.height)
    }

    /**
     * ソース画像から mask情報に従って crop 領域を切り出す
     *
     * @param bitmap ソース画像
     * @return 切り出した画像 (cropWidth x cropHeight)
     */
    fun cropBitmap(bitmap:Bitmap):Bitmap {
        val crop = cropRect(bitmap)
        return Bitmap.createBitmap(bitmap, crop.sx, crop.sy, crop.width, crop.height)
    }

    fun getParams():MaskCoreParams {
        return MaskCoreParams(rsx, rsy, rex, rey)
    }

    fun setParams(p:MaskCoreParams) {
        rsx = p.rsx
        rsy = p.rsy
        rex = p.rex
        rey = p.rey
        isDirty = true
    }
}

class CropMaskView@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : View(context, attrs, defStyleAttr) {
    val maskDrawable = 0x50FFFFFF.toDrawable()
    val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
        strokeWidth = context.dp2px(1f)
    }
    val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    val handleFillPaint: Paint
    val handleStrokePaint:Paint

    val logger = UtLog("CropMaskView", null, CropMaskView::class.java)
    init {
        this.background = 0x00000000.toInt().toDrawable()
        this.isClickable = true

        val typedValue = TypedValue()
        val primaryColor = if (context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)) {
            typedValue.data
        } else {
            0xFFFFFFFF.toInt()
        }
        val onPrimaryColor = if (context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer    , typedValue, true)) {
            typedValue.data
        } else {
            0xFF000000.toInt()
        }
        logger.debug(String.format("primaryColor = %08X, onPrimaryColor = %08X", primaryColor, onPrimaryColor))
        handleFillPaint = Paint().apply {
            style = Paint.Style.FILL
            color = primaryColor
        }
        handleStrokePaint = Paint().apply {
            style = Paint.Style.STROKE
            color = onPrimaryColor
            strokeWidth = context.dp2px(4f)
            isAntiAlias = true
        }
    }

    var viewModel: CropMaskViewModel? = null

    fun bindViewModel(vm: CropMaskViewModel) {
        viewModel = vm
        vm.setViewDimmension(getLayoutWidth(), getLayoutHeight(), paddingStart)
        vm.clearDirty { invalidate() }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = right - left
        val h = bottom - top
        viewModel?.let { vm->
            if (vm.rawViewWidth!=w || vm.rawViewHeight!=h) {
                vm.setViewDimmension(w, h, paddingStart)
                vm.clearDirty { invalidate() }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val vm = viewModel ?: return
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        maskDrawable.setBounds(vm.padding, vm.padding, width-vm.padding, height-vm.padding)
        maskDrawable.draw(canvas)

        canvas.drawRect(vm.maskSx, vm.maskSy, vm.maskEx, vm.maskEy, clearPaint)
        canvas.restoreToCount(saveCount)

        // mask の枠を描く
        canvas.drawRect(vm.maskSx, vm.maskSy, vm.maskEx, vm.maskEy, linePaint)

        // mask の四隅にハンドルを描く
        val handleSize = vm.padding*2f
        // 左上
        canvas.drawCircle(vm.maskSx, vm.maskSy, handleSize/2, handleFillPaint)
        canvas.drawCircle(vm.maskSx, vm.maskSy, (handleSize-handleStrokePaint.strokeWidth)/2, handleStrokePaint)
        // 右上
        canvas.drawCircle(vm.maskEx, vm.maskSy, handleSize/2, handleFillPaint)
        canvas.drawCircle(vm.maskEx, vm.maskSy, (handleSize-handleStrokePaint.strokeWidth)/2, handleStrokePaint)
        // 左下
        canvas.drawCircle(vm.maskSx, vm.maskEy, handleSize/2, handleFillPaint)
        canvas.drawCircle(vm.maskSx, vm.maskEy, (handleSize-handleStrokePaint.strokeWidth)/2, handleStrokePaint)
        // 右下
        canvas.drawCircle(vm.maskEx, vm.maskEy, handleSize/2, handleFillPaint)
        canvas.drawCircle(vm.maskEx, vm.maskEy, (handleSize-handleStrokePaint.strokeWidth)/2, handleStrokePaint)
    }

    enum class HitResult {
        None,
        LeftTop,
        RightTop,
        LeftBottom,
        RightBottom,
        Move
    }

    inner class DragState {
        var hit: HitResult = HitResult.None
        private fun hitTest(x:Float, y:Float): HitResult {
            val vm = viewModel ?: return HitResult.None
            val handleSize = (vm.padding*2f).coerceAtLeast(context.dp2px(36f))
            return when {
                // 左上
                (x in (vm.maskSx-handleSize)..(vm.maskSx+handleSize) && y in (vm.maskSy-handleSize)..(vm.maskSy+handleSize)) -> HitResult.LeftTop
                // 右上
                (x in (vm.maskEx-handleSize)..(vm.maskEx+handleSize) && y in (vm.maskSy-handleSize)..(vm.maskSy+handleSize)) -> HitResult.RightTop
                // 左下
                (x in (vm.maskSx-handleSize)..(vm.maskSx+handleSize) && y in (vm.maskEy-handleSize)..(vm.maskEy+handleSize)) -> HitResult.LeftBottom
                // 右下
                (x in (vm.maskEx-handleSize)..(vm.maskEx+handleSize) && y in (vm.maskEy-handleSize)..(vm.maskEy+handleSize)) -> HitResult.RightBottom
                // mask 内部
                (x in vm.maskSx..vm.maskEx && y in vm.maskSy..vm.maskEy) -> HitResult.Move
                else -> HitResult.None
            }
        }

        fun reset() {
            hit = HitResult.None
        }
        var x:Float = 0f
        var y:Float = 0f
        var sx:Float = 0f
        var sy:Float = 0f
        val isDragging get() = hit != HitResult.None
        fun start(x:Float, y:Float): Boolean {
            val vm = viewModel ?: return false
            val hit = hitTest(x, y)
            if (hit == HitResult.None) return false
            this.hit = hit
            this.x = x
            this.y = y
            this.sx = vm.maskSx
            this.sy = vm.maskSy
            return true
        }
        fun move(x:Float, y:Float) {
            val vm = viewModel ?: return
            val dx = x - this.x
            val dy = y - this.y
            vm.moveTo(sx + dx, sy + dy)
        }
    }
    val dragState = DragState()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val vm = viewModel ?: return super.onTouchEvent(event)
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                // このビューがタッチイベントを受け取ることを宣言する
                return dragState.start(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                when(dragState.hit) {
                    HitResult.LeftTop -> {
                        vm.setMaskSx(event.x)
                        vm.setMaskSy(event.y)
                        vm.clearDirty { invalidate() }
                    }
                    HitResult.RightTop -> {
                        vm.setMaskEx(event.x)
                        vm.setMaskSy(event.y)
                        vm.clearDirty { invalidate() }
                    }
                    HitResult.LeftBottom -> {
                        vm.setMaskSx(event.x)
                        vm.setMaskEy(event.y)
                        vm.clearDirty { invalidate() }
                    }
                    HitResult.RightBottom -> {
                        vm.setMaskEx(event.x)
                        vm.setMaskEy(event.y)
                        vm.clearDirty { invalidate() }
                    }
                    HitResult.Move -> {
                        // マスクの中心をタッチ位置に移動する
                        dragState.move(event.x, event.y)
                        vm.clearDirty { invalidate() }
                    }
                    else -> {}
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // タッチ終了
                dragState.reset()
                return true
            }
            else -> {}
        }

        return super.onTouchEvent(event)
    }
}
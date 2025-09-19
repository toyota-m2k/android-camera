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
import androidx.core.view.marginTop
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.dialog.CropMaskViewModel.Companion.MIN
import io.github.toyota32k.utils.android.dp2px
import io.github.toyota32k.utils.android.getLayoutHeight
import io.github.toyota32k.utils.android.getLayoutWidth
import io.github.toyota32k.utils.android.setMargin
import kotlin.math.roundToInt
import kotlin.text.toFloat

class CropMaskViewModel(val sourceWidth:Int,val sourceHeight:Int,) {
    companion object {
        const val MIN = 32f
    }
    var isDirty: Boolean = false
    fun clearDirty(fn:()->Unit) {
        if(isDirty) {
            fn()
            isDirty = false
        }
    }
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

    var viewWidth:Int = 100
        set(v) {
            val nv = v.coerceAtLeast(MIN.roundToInt())
            if(nv!=field) {
                field = nv
                isDirty = true
            }
        }
    var viewHeight:Int = 100
        set(v) {
            val nv = v.coerceAtLeast(MIN.roundToInt())
            if(nv!=field) {
                field = nv
                isDirty = true
            }
        }

    var maskSx:Float
        get() = rsx * viewWidth
        private set(v) { rsx = v / viewWidth }
    fun setMaskSx(v:Float):Float {
        return v.coerceIn(0f, maskEx-MIN).also { maskSx = it }
    }
    var maskSy:Float
        get() = rsy * viewHeight
        private set(v) { rsy = v / viewHeight }
    fun setMaskSy(v:Float):Float {
        return  v.coerceIn(0f, maskEy-MIN).also { maskSy = it }
    }
    var maskEx:Float
        get() = rex * viewWidth
        private set(v) { rex = v / viewWidth }
    fun setMaskEx(v:Float):Float {
        return v.coerceIn(maskSx+MIN, viewWidth.toFloat()).also { maskEx = it }
    }
    var maskEy:Float
        get() = rey * viewHeight
        private set(v) { rey = v / viewHeight }
    fun setMaskEy(v:Float):Float {
        return v.coerceIn(maskSy+MIN, viewHeight.toFloat()).also { maskEy = it }
    }

    fun moveMask(dx:Float, dy:Float) {
        val w = maskWidth
        val h = maskHeight
        val newSx = (maskSx + dx).coerceIn(0f, viewWidth - w)
        val newSy = (maskSy + dy).coerceIn(0f, viewHeight - h)
        maskSx = newSx
        maskSy = newSy
        maskEx = newSx + w
        maskEy = newSy + h
    }
    fun moveTo(x:Float, y:Float) {
        val w = maskWidth
        val h = maskHeight
        val newSx = x.coerceIn(0f, viewWidth - w)
        val newSy = y.coerceIn(0f, viewHeight - h)
        maskSx = newSx
        maskSy = newSy
        maskEx = newSx + w
        maskEy = newSy + h
    }

    val maskWidth:Float
        get() = maskEx - maskSx
    val maskHeight:Float
        get() = maskEy - maskSy

    val cropSx: Int
        get() = (rsx * sourceWidth.toFloat()).roundToInt()
    val cropSy: Int
        get() = (rsy * sourceHeight.toFloat()).roundToInt()
    val cropWidth: Int
        get() = ((rex-rsx) * sourceWidth.toFloat()).roundToInt()
    val cropHeight: Int
        get() = ((rey-rsy) * sourceHeight.toFloat()).roundToInt()

    fun cropBitmap(bitmap:Bitmap):Bitmap {
        assert(bitmap.width == sourceWidth && bitmap.height == sourceHeight)
        return Bitmap.createBitmap(bitmap, cropSx, cropSy, cropWidth, cropHeight)
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
        val primaryColor = if (context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimarySurface, typedValue, true)) {
            typedValue.data
        } else {
            0xFFFFFFFF.toInt()
        }
        val onPrimaryColor = if (context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimarySurface, typedValue, true)) {
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
        vm.viewWidth = getLayoutWidth()
        vm.viewHeight = getLayoutHeight()
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = right - left
        val h = bottom - top
        viewModel?.let { vm->
            if (vm.viewWidth!=w || vm.viewHeight!=h) {
                vm.viewWidth = w
                vm.viewHeight = h
                invalidate()
            }
        }
    }


    override fun onDraw(canvas: Canvas) {
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        maskDrawable.setBounds(0, 0, width, height)
        maskDrawable.draw(canvas)

        val vm = viewModel ?: return
        canvas.drawRect(vm.maskSx, vm.maskSy, vm.maskEx, vm.maskEy, clearPaint)
        canvas.restoreToCount(saveCount)

        // mask の枠を描く
        canvas.drawRect(vm.maskSx, vm.maskSy, vm.maskEx, vm.maskEy, linePaint)

        // mask の四隅にハンドルを描く
        val handleSize = context.dp2px(MIN)
        // 左上
        canvas.drawCircle(vm.maskSx, vm.maskSy, handleSize/2, handleFillPaint)
        canvas.drawCircle(vm.maskSx, vm.maskSy, handleSize/2, handleStrokePaint)
        // 右上
        canvas.drawCircle(vm.maskEx, vm.maskSy, handleSize/2, handleFillPaint)
        canvas.drawCircle(vm.maskEx, vm.maskSy, handleSize/2, handleStrokePaint)
        // 左下
        canvas.drawCircle(vm.maskSx, vm.maskEy, handleSize/2, handleFillPaint)
        canvas.drawCircle(vm.maskSx, vm.maskEy, handleSize/2, handleStrokePaint)
        // 右下
        canvas.drawCircle(vm.maskEx, vm.maskEy, handleSize/2, handleFillPaint)
        canvas.drawCircle(vm.maskEx, vm.maskEy, handleSize/2, handleStrokePaint)
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
            val handleSize = context.dp2px(MIN)
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
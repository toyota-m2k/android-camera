package io.github.toyota32k.video.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.boodroid.common.getAttrColor
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.model.*
import io.github.toyota32k.lib.player.model.option.ChapterHandlerImpl
import io.github.toyota32k.player.lib.R
import io.github.toyota32k.utils.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ChapterView @JvmOverloads constructor(context: Context, attrs: AttributeSet?=null, defStyleAttr:Int=0) : View(context, attrs, defStyleAttr) {
    companion object {
        val logger by lazy { UtLog("Chapter", TpLib.logger) }
    }
    private var mWidth:Int = 0
    private var mHeight:Int = 0
    private val mTickWidth = 1f

    private lateinit var model: IPlayerModel
    private val duration:Long get() = model.naturalDuration.value
    private val chapterList: IChapterList? get() = (model.currentSource.value as? IMediaSourceWithChapter)?.chapterList
    private val disabledRanges:List<Range>? get() = chapterList?.disabledRanges(model.currentSource.value?.trimming?:Range.empty)

    @ColorInt private val defaultColor:Int
    @ColorInt private val tickColor:Int
    @ColorInt private val enabledColor:Int
    @ColorInt private val disabledColor:Int

    init {
        val sa = context.theme.obtainStyledAttributes(attrs, R.styleable.ChapterView, defStyleAttr,0)
        defaultColor = sa.getColor(R.styleable.ChapterView_defaultColor, Color.TRANSPARENT)
        tickColor = sa.getColor(R.styleable.ChapterView_tickColor, context.theme.getAttrColor(com.google.android.material.R.attr.colorOnPrimary, Color.WHITE))
        enabledColor = sa.getColor(R.styleable.ChapterView_enabledColor,context.theme.getAttrColor(com.google.android.material.R.attr.colorSecondary, Color.GREEN))
        disabledColor = sa.getColor(R.styleable.ChapterView_disabledColor, Color.argb(0xa0,0,0,0))
        sa.recycle()
    }

//    private lateinit var binder:Binder
    fun bindViewModel(model: IPlayerModel, @Suppress("UNUSED_PARAMETER") binder: Binder) {
        this.model = model
//        this.binder = binder
        val owner = lifecycleOwner()!!
        val scope = owner.lifecycleScope

//        val mutableChapterList = chapterList as? IMutableChapterList
//        if(mutableChapterList!=null) {
//            binder.add(mutableChapterList.modifiedListener.add(owner) { invalidate() })
//        }

        model.currentSource.onEach(::bindChapterList).launchIn(scope)

        val flow = if(model is IChapterHandler) {
            combine(model.currentSource, model.naturalDuration) { src, dur->
                src !=null && dur>0
            }
        } else model.naturalDuration.filter { it>0 }

        flow.onEach {
            invalidate()
        }.launchIn(scope)
    }

    var chapterListened:IDisposable? = null
    private fun bindChapterList(src:IMediaSource?) {
        chapterListened?.dispose()
        chapterListened = null
        src ?: return
        val sourceWithChapter = src as? IMediaSourceWithChapter ?: return
        val mutableChapterList = sourceWithChapter.chapterList as? IMutableChapterList ?: return
        chapterListened = mutableChapterList.modifiedListener.add(lifecycleOwner()!!) { invalidate() }
    }

    private fun time2x(time: Long): Float {
        return if (duration == 0L) 0f else mWidth.toFloat() * time.toFloat() / duration.toFloat()
    }

    val rect = RectF()
    val paint = Paint()

    private val hideChapterViewIfEmpty by lazy { (model as? IChapterHandler)?.hideChapterViewIfEmpty == true }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if(canvas==null) return
        if(mWidth==0||mHeight==0) return
        if(!this::model.isInitialized) return
        if(duration<=0L) return
        if(hideChapterViewIfEmpty && chapterList?.isNotEmpty!=true) return
        val list = chapterList?.chapters ?: return

        val width = mWidth.toFloat()
        val height = mHeight.toFloat()

        // background
        rect.set(0f,0f, width, height)
        paint.color = defaultColor
        canvas.drawRect(rect,paint)

        // chapters
        val dr = disabledRanges
        if(list.isEmpty() && dr.isNullOrEmpty()) return

        // enabled range
        rect.set(0f,0f, width, height)
        paint.color = enabledColor
        canvas.drawRect(rect,paint)

        // disabled range
        if(!dr.isNullOrEmpty()) {
            paint.setColor(disabledColor)
            for (r in dr) {
                val end = if (r.end == 0L) duration else r.end
                val x1 = time2x(r.start)
                val x2 = time2x(end)
                rect.set(x1, 0f, x2, height)
                canvas.drawRect(rect, paint)
            }
        }

        // chapter tick
        paint.color = tickColor
        for(c in list) {
            val x = time2x(c.position)
            rect.set(x-mTickWidth/2,0f,x+mTickWidth/2, height)
            canvas.drawRect(rect,paint)
        }

    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if(w>0 && h>=0 && (mWidth!=w || mHeight!=h)) {
            mWidth = w
            mHeight = h
            invalidate()
        }
    }
}
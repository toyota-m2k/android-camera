package io.github.toyota32k.secureCamera.utils

import android.view.View
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.utils.IDisposable
import java.lang.ref.WeakReference

class ViewSizeChangeListener(view: View, private val onGoodSize:Boolean, private val onSizeChanged:(width:Int,height:Int)->Unit): View.OnLayoutChangeListener, IDisposable {
    constructor(view: View, onSizeChanged:(width:Int,height:Int)->Unit):this(view, true, onSizeChanged)

    private val viewRef = WeakReference(view)
    init {
        view.addOnLayoutChangeListener(this)
    }

    override fun dispose() {
        viewRef.get()?.removeOnLayoutChangeListener(this)
    }

    private var prevWidth:Int = 0
    private var prevHeight:Int = 0
    override fun onLayoutChange(
        v: View?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int
    ) {
        val newWidth = right - left
        val newHeight = bottom - top
        if (prevWidth != newWidth || prevHeight != newHeight) {
            if (!onGoodSize|| (newWidth > 0 && newHeight > 0)) {
                prevWidth = newWidth
                prevHeight = newHeight
                onSizeChanged(newWidth, newHeight)
            }
        }
    }

    fun forceFire() {
        val view = viewRef.get()?: return
        val width = view.width
        val height = view.height
        if (!onGoodSize || (width > 0 && height > 0)) {
            onSizeChanged(width, height)
        }
    }
}

fun Binder.onViewSizeChanged(view: View, onSizeChanged:(width:Int,height:Int)->Unit):Binder
    = add(ViewSizeChangeListener(view, onSizeChanged))
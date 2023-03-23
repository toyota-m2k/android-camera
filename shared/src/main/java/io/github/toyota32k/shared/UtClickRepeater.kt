package io.github.toyota32k.shared

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import io.github.toyota32k.utils.Chronos
import io.github.toyota32k.utils.IDisposable
import io.github.toyota32k.utils.UtLog

/**
 * ボタンを押し続けたときに、Clickイベントを連続して発行できるようにするクラス
 * view.setOnTouchListener を使うので、これを使う仕掛け（UtGestureInterpreterなど）とは共存できない。
 *
 * @param   view    Clickするview
 * @param   repeatInterval  Clickイベントの発行インターバル
 * @param   activationTime  クリックしてから、連続イベントを発行し始めるまでのインターバル
 */
class UtClickRepeater(
    view:View?=null,
    val repeatInterval:Long = 200,
    val activationTime:Long = 500
): IDisposable {
    var view: View? by WeakReferenceDelegate()

    val logger = UtLog("ClickRepeater", null, UtClickRepeater::class.java)
    var chronos = Chronos(logger)

    enum class RepeatStatus {
        NONE,
        STANDBY,
        REPEATING,
    }
    var status = RepeatStatus.NONE
    init {
        if(view!=null) {
            attachView(view)
        }
    }

    val standby = object: Runnable {
        override fun run() {
            if(status == RepeatStatus.STANDBY) {
                chronos.lap("Touch - Repeat Started")
                status = RepeatStatus.REPEATING
                repeat.run()
            }
        }
    }

    val repeat = object: Runnable {
        override fun run() {
            if(status!=RepeatStatus.REPEATING) return
            chronos.lap("Touch - Perform Repeat")
            view?.performClick()
            view?.postDelayed(this,repeatInterval)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attachView(view:View) {
        status = RepeatStatus.NONE
        this.view = view
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    chronos.lap("Touch - UP")
                    status = RepeatStatus.NONE
                }
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    chronos.lap("Touch - DOWN")
                    status = RepeatStatus.STANDBY
                    view.postDelayed(standby, activationTime)
                }
                else -> {}
            }
//            view.performClick()
            false
        }
    }

    override fun dispose() {
        this.view?.setOnTouchListener(null)
        this.view = null
    }
}
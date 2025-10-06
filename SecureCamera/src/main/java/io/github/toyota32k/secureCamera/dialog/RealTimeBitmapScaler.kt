package io.github.toyota32k.secureCamera.dialog

import android.graphics.Bitmap
import android.widget.Button
import androidx.core.graphics.scale
import com.google.android.material.slider.Slider
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.sliderBinding
import io.github.toyota32k.secureCamera.utils.BitmapStore
import io.github.toyota32k.utils.IDisposable
import io.github.toyota32k.utils.IUtPropOwner
import io.github.toyota32k.utils.lifecycle.ConstantLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

class RealTimeBitmapScaler(val sourceBitmap: Bitmap, val bitmapStore: BitmapStore): IUtPropOwner, IDisposable {
    companion object {
        const val MIN_LENGTH = 100f // px
    }
    private val busy = AtomicBoolean(false)
    private var scaledBitmap: Bitmap? = null
        get() = field
        set(v) { field = bitmapStore.replaceNullable(field, v) }

    private var scaledWidth:Int = sourceBitmap.width
    private var scaledHeight:Int = sourceBitmap.height
    val bitmap: StateFlow<Bitmap> = MutableStateFlow<Bitmap>(sourceBitmap)
    val orgLongSideLength = max(sourceBitmap.width, sourceBitmap.height).toFloat()
    val longSideLength = MutableStateFlow(orgLongSideLength)
    var tryAgain = false
    var job: Job? = null



    fun start(coroutineScope: CoroutineScope) {
        job = coroutineScope.launch {
            longSideLength.collect {
                deflateBitmap(it / orgLongSideLength)
            }
        }
    }

    override fun dispose() {
        job?.cancel()
        job = null
    }

    fun bindToSlider(binder: Binder, slider: Slider, minus: Button, plus: Button, presetButtons:Map<Int, Button>) {
        slider.stepSize = 1f
        binder
            .sliderBinding(view=slider, data=longSideLength, mode= BindingMode.TwoWay, min= MutableStateFlow<Float>(MIN_LENGTH), max= MutableStateFlow(orgLongSideLength))
            .clickBinding(minus) {
                val len = ((longSideLength.value.roundToInt()+7)/8)*8 - 8
                longSideLength.value = len.toFloat().coerceAtLeast(MIN_LENGTH)
            }
            .clickBinding(plus) {
                val len = (longSideLength.value.roundToInt()/8)*8 + 8
                longSideLength.value = len.toFloat().coerceAtMost(orgLongSideLength)
            }
            .apply {
                for ((k, v) in presetButtons) {
                    clickBinding(v) {
                        longSideLength.value = k.toFloat()
                    }
                    enableBinding(v, ConstantLiveData( MIN_LENGTH*2<orgLongSideLength && orgLongSideLength.roundToInt() >= k))
                }
            }
    }

    private suspend fun deflateBitmap(newScale:Float) {
        if (!busy.getAndSet(true)) {
            var s = newScale
            try {
                while (true) {
                    tryAgain = false
                    val w = (sourceBitmap.width * s).toInt()
                    val h = (sourceBitmap.height * s).toInt()
                    if (w != scaledWidth || h != scaledHeight) {
                        if (w == sourceBitmap.width && h == sourceBitmap.height) {
                            scaledBitmap = null
                            bitmap.mutable.value = sourceBitmap
                        } else {
                            val bmp = withContext(Dispatchers.IO) { sourceBitmap.scale(w, h) }
                            bitmap.mutable.value = bmp
                            scaledBitmap = bmp
                        }
                        scaledWidth = w
                        scaledHeight = h
                    }
                    if (!tryAgain) break
                    s = longSideLength.value / orgLongSideLength
                }
            } finally {
                busy.set(false)
            }
        } else {
            tryAgain = true
        }
    }
}

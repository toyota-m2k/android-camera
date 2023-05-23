package io.github.toyota32k.lib.player.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.google.android.material.slider.Slider
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.sliderBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.boodroid.common.getColorAsDrawable
import io.github.toyota32k.boodroid.common.getColorAwareOfTheme
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.player.lib.R
import io.github.toyota32k.player.lib.databinding.V2SliderPanelBinding
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.roundToLong

class SliderPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr), Slider.OnChangeListener  {
    companion object {
        val logger by lazy { UtLog("Slider", ControlPanel.logger) }
    }

    val controls:V2SliderPanelBinding
    private lateinit var model: PlayerControllerModel

    init {
        val sa = context.theme.obtainStyledAttributes(attrs,
            R.styleable.ControlPanel, defStyleAttr,0)
        val panelBackground = sa.getColorAsDrawable(R.styleable.ControlPanel_panelBackgroundColor, context.theme, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val panelText = sa.getColorAwareOfTheme(R.styleable.ControlPanel_panelTextColor, context.theme, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        sa.recycle()

        controls = V2SliderPanelBinding.inflate(LayoutInflater.from(context), this, true).apply {
            sliderPanelRoot.background = panelBackground
            counterLabel.setTextColor(panelText)
            durationLabel.setTextColor(panelText)
        }
    }

    fun bindViewModel(model: PlayerControllerModel, binder: Binder) {
        this.model = model
        controls.slider.addOnChangeListener(this)
        controls.chapterView.bindViewModel(model.playerModel, binder)

        binder
            .textBinding(controls.counterLabel, combine(model.playerModel.playerSeekPosition, model.playerModel.naturalDuration) { pos,dur-> formatTime(pos, dur) })
            .textBinding(controls.durationLabel, model.playerModel.naturalDuration.map { formatTime(it,it) } )
            .sliderBinding(controls.slider, model.playerModel.playerSeekPosition.map { it.toFloat() }, min=null, max= model.playerModel.naturalDuration.map { max(100f, it.toFloat()) })
            .enableBinding(controls.slider, model.playerModel.isReady)
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if(fromUser) {
            model.playerModel.seekManager.requestedPositionFromSlider.value = value.roundToLong()
        }
    }
}
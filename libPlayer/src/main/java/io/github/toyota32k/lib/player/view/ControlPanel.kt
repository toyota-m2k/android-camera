package io.github.toyota32k.lib.player.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.children
import com.google.android.material.slider.Slider
import io.github.toyota32k.bindit.*
import io.github.toyota32k.player.lib.R
import io.github.toyota32k.boodroid.common.getColorAsDrawable
import io.github.toyota32k.boodroid.common.getColorAwareOfTheme
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.lib.player.model.*
import io.github.toyota32k.player.lib.databinding.V2ControlPanelBinding
import io.github.toyota32k.utils.ConstantLiveData
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.bindCommand
import io.github.toyota32k.utils.lifecycleOwner
import io.github.toyota32k.video.view.ChapterView
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.roundToLong

class ControlPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr), Slider.OnChangeListener {
    companion object {
        val logger by lazy { UtLog("ControlPanel", TpLib.logger) }
    }

    val controls:V2ControlPanelBinding

    init {
        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.ControlPanel, defStyleAttr,0)
        val panelBackground = sa.getColorAsDrawable(R.styleable.ControlPanel_panelBackgroundColor, context.theme, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val panelText = sa.getColorAwareOfTheme(R.styleable.ControlPanel_panelTextColor, context.theme, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)

        val buttonEnabled = sa.getColorAwareOfTheme(R.styleable.ControlPanel_buttonTintColor, context.theme, com.google.android.material.R.attr.colorPrimaryVariant, Color.WHITE)
        val disabledDefault = Color.argb(0x80, Color.red(panelText), Color.green(panelText), Color.blue(panelText))
        val buttonDisabled = sa.getColor(R.styleable.ControlPanel_buttonDisabledTintColor, disabledDefault)
        val buttonTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_enabled),
                intArrayOf(),
            ),
            intArrayOf(buttonEnabled, buttonDisabled)
        )

        sa.recycle()

        controls = V2ControlPanelBinding.inflate(LayoutInflater.from(context), this, true).apply {
            controlPanelRoot.background = panelBackground
            counterLabel.setTextColor(panelText)
            durationLabel.setTextColor(panelText)
            controlButtons.children.forEach { (it as? ImageButton)?.imageTintList = buttonTint }
        }
    }

    private lateinit var model: PlayerControllerModel

    fun bindViewModel(model: PlayerControllerModel, binder: Binder) {
        this.model = model
//        val owner = lifecycleOwner()!!
//        val scope = owner.lifecycleScope

//        val playButton = findViewById<ImageButton>(R.id.play_button)
//        val pauseButton = findViewById<ImageButton>(R.id.pause_button)
//        val prevVideoButton = findViewById<ImageButton>(R.id.prev_video_button)
//        val nextVideoButton = findViewById<ImageButton>(R.id.next_video_button)
//        val prevChapterButton = findViewById<ImageButton>(R.id.prev_chapter_button)
//        val nextChapterButton = findViewById<ImageButton>(R.id.next_chapter_button)
//        val seekBackButton = findViewById<ImageButton>(R.id.seek_back_button)
//        val seekForwardButton = findViewById<ImageButton>(R.id.seek_forward_button)
//        val pinpButton = findViewById<ImageButton>(R.id.pinp_button)
//        val fullscreenButton = findViewById<ImageButton>(R.id.fullscreen_button)
//        val collapseButton = findViewById<ImageButton>(R.id.collapse_button)
////        val closeButton = findViewById<ImageButton>(R.id.close_button)
//        val snapshotButton = findViewById<ImageButton>(R.id.snapshot_button)
//        val slider = findViewById<Slider>(R.id.slider)

        controls.slider.addOnChangeListener(this)
//        slider.addOnSliderTouchListener(this)

        findViewById<ChapterView>(R.id.chapter_view).bindViewModel(model.playerModel, binder)

        val chapterHandler = model.playerModel as? IChapterHandler
        val playlistHandler = model.playerModel as? IPlaylistHandler

        binder
            .visibilityBinding(controls.playButton, model.playerModel.isPlaying, BoolConvert.Inverse, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.pauseButton, model.playerModel.isPlaying, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.fullscreenButton, model.windowMode.map { it!=PlayerControllerModel.WindowMode.FULLSCREEN }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.collapseButton, model.windowMode.map { it!=PlayerControllerModel.WindowMode.NORMAL }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.pinpButton, ConstantLiveData(model.supportPinP), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.fullscreenButton, ConstantLiveData(model.supportFullscreen), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.snapshotButton, ConstantLiveData(model.snapshotHandler!=null), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.rotateLeft, ConstantLiveData(model.enableRotateLeft), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.rotateRight, ConstantLiveData(model.enableRotateRight), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .multiVisibilityBinding(arrayOf(controls.prevChapterButton, controls.nextChapterButton), ConstantLiveData(chapterHandler!=null), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .multiVisibilityBinding(arrayOf(controls.prevVideoButton, controls.nextVideoButton), ConstantLiveData(playlistHandler!=null), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .multiEnableBinding(arrayOf(controls.playButton, controls.pauseButton, controls.seekBackButton, controls.seekForwardButton, controls.fullscreenButton, controls.pinpButton, controls.slider), model.playerModel.isReady)
            .textBinding(findViewById(R.id.counter_label), combine(model.playerModel.playerSeekPosition, model.playerModel.naturalDuration) { pos,dur->formatTime(pos, dur) })
            .textBinding(findViewById(R.id.duration_label), model.playerModel.naturalDuration.map { formatTime(it,it) } )
            .sliderBinding(controls.slider, model.playerModel.playerSeekPosition.map { it.toFloat() }, min=null, max= model.playerModel.naturalDuration.map { max(100f, it.toFloat())})

            .bindCommand(model.commandPlay, controls.playButton)
            .bindCommand(model.commandPlay, controls.playButton)
            .bindCommand(model.commandPause, controls.pauseButton)
            .bindCommand(model.commandSeekBackward, controls.seekBackButton)
            .bindCommand(model.commandSeekForward, controls.seekForwardButton)
            .bindCommand(model.commandFullscreen, controls.fullscreenButton)
            .bindCommand(model.commandSnapshot, controls.snapshotButton)
            .bindCommand(model.commandPinP, controls.pinpButton)
            .bindCommand(model.commandCollapse, controls.collapseButton)
            .bindCommand(model.commandRotate, controls.rotateLeft, Rotation.LEFT)
            .bindCommand(model.commandRotate, controls.rotateRight, Rotation.RIGHT)
            .apply {
                if(playlistHandler!=null ) {
                    enableBinding(controls.prevVideoButton, playlistHandler.hasPrevious)
                    enableBinding(controls.nextVideoButton, playlistHandler.hasNext)
                    bindCommand(playlistHandler.commandNext, controls.nextVideoButton)
                    bindCommand(playlistHandler.commandPrev, controls.prevVideoButton)
                }
                if(chapterHandler!=null) {
                    bindCommand(chapterHandler.commandNextChapter, controls.nextChapterButton)
                    bindCommand(chapterHandler.commandPrevChapter, controls.prevChapterButton)
                }
            }
    }

    @SuppressLint("RestrictedApi")
    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if(fromUser) {
//            logger.debug("XX2: ${value.roundToLong()} / ${slider.valueTo.roundToLong()}")
            model.playerModel.seekManager.requestedPositionFromSlider.value = value.roundToLong()
        }
    }

}
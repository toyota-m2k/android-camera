package io.github.toyota32k.lib.player.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.view.children
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.boodroid.common.getColorAsDrawable
import io.github.toyota32k.boodroid.common.getColorAwareOfTheme
import io.github.toyota32k.lib.player.model.IChapterHandler
import io.github.toyota32k.lib.player.model.IPlaylistHandler
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.model.Rotation
import io.github.toyota32k.player.lib.R
import io.github.toyota32k.player.lib.databinding.V2ButtonPanelBinding
import io.github.toyota32k.shared.gesture.UtClickRepeater
import io.github.toyota32k.utils.ConstantLiveData
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.flow.map

//class ButtonPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
//    : FrameLayout(context, attrs, defStyleAttr) {
//    companion object {
//        val logger by lazy { UtLog("Buttons", ControlPanel.logger) }
//    }
//    val controls: V2ButtonPanelBinding
//
//    init {
//        val sa = context.theme.obtainStyledAttributes(attrs,
//            R.styleable.ControlPanel, defStyleAttr,0)
//        val panelBackground = sa.getColorAsDrawable(R.styleable.ControlPanel_panelBackgroundColor, context.theme, com.google.android.material.R.attr.colorSurface, Color.WHITE)
//        val panelText = sa.getColorAwareOfTheme(R.styleable.ControlPanel_panelTextColor, context.theme, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
//
//        val buttonEnabled = sa.getColorAwareOfTheme(R.styleable.ControlPanel_buttonTintColor, context.theme, com.google.android.material.R.attr.colorPrimaryVariant, Color.WHITE)
//        val disabledDefault = Color.argb(0x80, Color.red(panelText), Color.green(panelText), Color.blue(panelText))
//        val buttonDisabled = sa.getColor(R.styleable.ControlPanel_buttonDisabledTintColor, disabledDefault)
//        val buttonTint = ColorStateList(
//            arrayOf(
//                intArrayOf(android.R.attr.state_enabled),
//                intArrayOf(),
//            ),
//            intArrayOf(buttonEnabled, buttonDisabled)
//        )
//
//        sa.recycle()
//
//        controls = V2ButtonPanelBinding.inflate(LayoutInflater.from(context), this, true).apply {
//            buttonPanelRoot.background = panelBackground
//            controlButtons.children.forEach { (it as? ImageButton)?.imageTintList = buttonTint }
//        }
//
//    }
//
//    private lateinit var model: PlayerControllerModel
//
//    fun bindViewModel(model: PlayerControllerModel, binder: Binder) {
//        this.model = model
//
//        val chapterHandler = model.playerModel as? IChapterHandler
//        val playlistHandler = model.playerModel as? IPlaylistHandler
//
//        binder
//            .visibilityBinding(controls.playButton, model.playerModel.isPlaying, BoolConvert.Inverse, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.pauseButton, model.playerModel.isPlaying, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.fullscreenButton, model.windowMode.map { it!=PlayerControllerModel.WindowMode.FULLSCREEN }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.collapseButton, model.windowMode.map { it!=PlayerControllerModel.WindowMode.NORMAL }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.pinpButton, ConstantLiveData(model.supportPinP), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.fullscreenButton, ConstantLiveData(model.supportFullscreen), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.snapshotButton, ConstantLiveData(model.snapshotHandler!=null), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.rotateLeft, ConstantLiveData(model.enableRotateLeft), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.rotateRight, ConstantLiveData(model.enableRotateRight), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .multiVisibilityBinding(arrayOf(controls.prevChapterButton, controls.nextChapterButton), ConstantLiveData(chapterHandler!=null), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .multiVisibilityBinding(arrayOf(controls.prevVideoButton, controls.nextVideoButton), ConstantLiveData(playlistHandler!=null && model.showNextPreviousButton), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .multiEnableBinding(arrayOf(controls.playButton, controls.pauseButton, controls.seekBackButton, controls.seekForwardButton, controls.fullscreenButton, controls.pinpButton), model.playerModel.isReady)
//
//            .bindCommand(model.commandPlay, controls.playButton)
//            .bindCommand(model.commandPlay, controls.playButton)
//            .bindCommand(model.commandPause, controls.pauseButton)
//            .bindCommand(model.commandSeekBackward, controls.seekBackButton)
//            .bindCommand(model.commandSeekForward, controls.seekForwardButton)
//            .bindCommand(model.commandFullscreen, controls.fullscreenButton)
//            .bindCommand(model.commandSnapshot, controls.snapshotButton)
//            .bindCommand(model.commandPinP, controls.pinpButton)
//            .bindCommand(model.commandCollapse, controls.collapseButton)
//            .bindCommand(model.commandRotate, controls.rotateLeft, Rotation.LEFT)
//            .bindCommand(model.commandRotate, controls.rotateRight, Rotation.RIGHT)
//            .apply {
//                if(playlistHandler!=null ) {
//                    enableBinding(controls.prevVideoButton, playlistHandler.hasPrevious)
//                    enableBinding(controls.nextVideoButton, playlistHandler.hasNext)
//                    bindCommand(playlistHandler.commandNext, controls.nextVideoButton)
//                    bindCommand(playlistHandler.commandPrev, controls.prevVideoButton)
//                }
//                if(chapterHandler!=null) {
//                    bindCommand(chapterHandler.commandNextChapter, controls.nextChapterButton)
//                    bindCommand(chapterHandler.commandPrevChapter, controls.prevChapterButton)
//                }
//            }
//            .add(UtClickRepeater(controls.seekBackButton))
//            .add(UtClickRepeater(controls.seekForwardButton))
//    }
//
//
//}
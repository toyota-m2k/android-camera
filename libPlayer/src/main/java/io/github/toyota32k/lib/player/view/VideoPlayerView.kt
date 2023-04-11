package io.github.toyota32k.video.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.VisibilityBinding
import io.github.toyota32k.bindit.visibilityBinding
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.player.lib.databinding.V2PlayerViewBinding
import io.github.toyota32k.utils.UtLog

class VideoPlayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger by lazy { UtLog("PlayerView", TpLib.logger) }
    }

    val controls:V2PlayerViewBinding

    init {
        controls = V2PlayerViewBinding.inflate(LayoutInflater.from(context), this, true)
    }

    private lateinit var model: PlayerControllerModel

    fun bindViewModel(model: PlayerControllerModel, binder: Binder) {
        this.model = model
        controls.player.bindViewModel(model, binder)
        controls.controller.bindViewModel(model, binder)
        binder.visibilityBinding(controls.controller, model.showControlPanel, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
    }

    fun associatePlayer(flag:Boolean) {
        controls.player.associatePlayer(flag)
    }
}
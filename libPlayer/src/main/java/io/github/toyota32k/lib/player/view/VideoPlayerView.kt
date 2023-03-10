package io.github.toyota32k.video.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.model.ControlPanelModel
import io.github.toyota32k.lib.player.view.AmvExoVideoPlayer
import io.github.toyota32k.lib.player.view.ControlPanel
import io.github.toyota32k.player.lib.R
import io.github.toyota32k.utils.UtLog

class VideoPlayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger by lazy { UtLog("PlayerView", TpLib.logger) }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.v2_player_view, this)
    }

    private lateinit var model: ControlPanelModel

    fun bindViewModel(model: ControlPanelModel, binder: Binder) {
        this.model = model
//        val owner = lifecycleOwner()!!
//        val scope = owner.lifecycleScope

        val player = findViewById<AmvExoVideoPlayer>(R.id.player)
        player.bindViewModel(model, binder)

        val controller = findViewById<ControlPanel>(R.id.controller)
        controller.bindViewModel(model, binder)
    }
}
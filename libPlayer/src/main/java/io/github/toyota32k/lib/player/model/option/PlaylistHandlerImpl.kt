package io.github.toyota32k.lib.player.model.option

import io.github.toyota32k.bindit.LiteUnitCommand
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.lib.player.model.IMediaFeed
import io.github.toyota32k.lib.player.model.IPlayerModel
import io.github.toyota32k.lib.player.model.IPlaylistHandler
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PlaylistHandlerImpl(
    val playerModel: IPlayerModel,
    val playlist: IMediaFeed,
    override val autoPlayOnSetSource:Boolean,
    override val continuousPlay: Boolean) : IPlaylistHandler {
    private val currentSource: StateFlow<IMediaSource?>
        get() = playlist.currentSource
    init {
        currentSource.onEach(::onCurrentSourceChanged).launchIn(playerModel.scope)
    }

    private fun onCurrentSourceChanged(src: IMediaSource?) {
        playerModel.setSource(src, autoPlayOnSetSource)
    }

//    val currentVideo: IAmvSource?
//        get() = currentSource.value

//    override fun onEnd() {
//        playlist.onEnd()
//    }

    override val commandNext = LiteUnitCommand(this::next)
    override val commandPrev = LiteUnitCommand(this::previous)

    private fun next() {
        playlist.next()
    }

    private fun previous() {
        playlist.previous()
    }

    override val hasPrevious: StateFlow<Boolean>
        get() = playlist.hasPrevious

    override val hasNext: StateFlow<Boolean>
        get() = playlist.hasNext
}
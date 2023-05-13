package io.github.toyota32k.lib.player.model

import android.app.Application
import android.util.Size
import com.google.android.exoplayer2.ui.StyledPlayerView
import io.github.toyota32k.utils.IUnitCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface IPlayerModel : AutoCloseable {
    fun setSource(src:IMediaSource?, autoPlay:Boolean)
    fun play()
    fun pause()
    fun togglePlay()

    fun reset()
    fun seekRelative(seek: Long)
    fun seekTo(seek:Long)

    fun associatePlayerView(playerView: StyledPlayerView)
    fun dissociatePlayerView(playerView: StyledPlayerView)
    fun onRootViewSizeChanged(size: Size)
    fun onPlaybackCompleted()

    val currentSource: StateFlow<IMediaSource?>
    val playerSize: StateFlow<Size>
    val stretchVideoToView: StateFlow<Boolean>

    val rotation: StateFlow<Int>
    fun rotate(value:Rotation)


    val playerSeekPosition: StateFlow<Long>
    val naturalDuration: StateFlow<Long>
    val isReady: StateFlow<Boolean>
    val isLoading: StateFlow<Boolean>
    val isPlaying: StateFlow<Boolean>
    val isError: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>

    val seekManager:ISeekManager
    val currentPosition:Long

    val scope: CoroutineScope
    val context: Application

    fun killPlayer()
    fun revivePlayer():Boolean

}

interface IPlaylistHandler {
    val autoPlayOnSetSource:Boolean
    val continuousPlay:Boolean
    val commandNext: IUnitCommand
    val commandPrev: IUnitCommand
//    fun next()
//    fun previous()

    val hasPrevious:StateFlow<Boolean>
    val hasNext:StateFlow<Boolean>
}

interface  IChapterHandler {
    val hideChapterViewIfEmpty:Boolean
    val commandNextChapter: IUnitCommand
    val commandPrevChapter: IUnitCommand

//    val chapterList:StateFlow<IChapterList?>
//    val hasChapters:StateFlow<Boolean>
}
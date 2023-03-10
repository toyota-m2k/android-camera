package io.github.toyota32k.lib.player.model

import android.app.Application
import android.util.Size
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface IPlayerModel : AutoCloseable {
    fun play()
    fun pause()

    fun next()
    fun previous()

    fun nextChapter()
    fun prevChapter()
    fun seekRelative(i: Long)

    fun associatePlayerView(playerView: StyledPlayerView)
    fun onRootViewSizeChanged(size: Size)

    val playerSize: StateFlow<Size>
    val stretchVideoToView: StateFlow<Boolean>

    val playerSeekPosition: StateFlow<Long>
    val naturalDuration: StateFlow<Long>
    val isReady: StateFlow<Boolean>
    val isLoading: StateFlow<Boolean>
    val isPlaying: StateFlow<Boolean>
    val isError: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>

    val chapterList:StateFlow<IChapterList?>
    val hasChapters:StateFlow<Boolean>
    val disabledRanges:List<Range>?

    val hasPrevious:StateFlow<Boolean>
    val hasNext:StateFlow<Boolean>
    val seekManager:ISeekManager

    val scope: CoroutineScope
    val context: Application
}
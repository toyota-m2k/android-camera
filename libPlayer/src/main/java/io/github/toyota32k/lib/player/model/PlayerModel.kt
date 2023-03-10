package io.github.toyota32k.lib.player.model

import android.app.Application
import android.content.Context
import android.util.Size
import android.view.ViewGroup
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.video.VideoSize
import io.github.toyota32k.bindit.list.ObservableList
import io.github.toyota32k.lib.player.TpLib
import io.github.toyota32k.lib.player.common.AmvFitterEx
import io.github.toyota32k.lib.player.common.FitMode
import io.github.toyota32k.player.lib.R
import io.github.toyota32k.utils.SuspendableEvent
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Closeable
import kotlin.math.max
import kotlin.math.min

/**
 * 動画プレーヤーと、それに関するプロパティを保持するビューモデル
 * ExoPlayerは（何と！）Viewではなく、ActivityやViewのライフサイクルから独立しているので、ビューモデルに持たせておくのが一番しっくりくるのだ。
 * しかも、ダイアログのような一時的な画面で使うのでなく、PinPや全画面表示などを有効にするなら、このビューモデルはApplicationスコープのようなライフサイクルオブジェクトに持たせるのがよい。
 * @param context   Application Context
 */
class PlayerModel(
    context: Context,                   // application context が必要
) : PlayerModelBase(context) {
    override val hasNext = MutableStateFlow(false)
    override val hasPrevious = MutableStateFlow(false)
    private val videoSources = ObservableList<IAmvSource>()

    override fun next() {
        val index = videoSources.indexOf(currentSource.value)
        if(index<0) return
        playAt(index+1)
    }
    override fun previous() {
        val index = videoSources.indexOf(currentSource.value)
        if(index<0) return
        playAt(index-1)
    }

    fun setSources(sources:List<IAmvSource>, startIndex:Int=0, position:Long=0L) {
        reset()
        videoSources.replace(sources)

        if (sources.isNotEmpty()) {
            val si = max(0, min(startIndex, sources.size))
            val pos = max(sources[si].trimming.start, position)
            if (useExoPlayList) {
                val list = sources.map { src -> makeMediaSource(src) }.toList()
                player.setMediaSources(list, si, pos)
            } else {
                player.setMediaSource(makeMediaSource(sources[si]), pos)
                currentSource.mutable.value = sources[si]
                hasNext.mutable.value = si<sources.size-1
                hasPrevious.mutable.value = 0<si
            }
            player.prepare()
            play()
        }
    }

    fun playAt(index:Int, position:Long=0L) {
        val current = currentSource.value
        if(current!=null && videoSources.indexOf(current)==index) {
            if(!isPlaying.value) {
                play()
            }
        }

        if (0 <= index && index < videoSources.size) {
            player.setMediaSource(makeMediaSource(videoSources[index]), max(videoSources[index].trimming.start, position))
            currentSource.mutable.value = videoSources[index]
            hasNext.mutable.value = index<videoSources.size-1
            hasPrevious.mutable.value = 0<index
            player.prepare()
            play()
        }
    }

    fun playAt(item:IAmvSource, position: Long=0L) {
        playAt(videoSources.indexOf(item), position)
    }

    /**
     * 再初期化
     */
    override fun reset() {
        super.reset()
        hasPrevious.value = false
        hasNext.value = false
        videoSources.clear()
    }

    override fun onEnd() {
        logger.debug()
        if(hasNext.value) {
            next()
        } else {
            pause()
        }
    }
}

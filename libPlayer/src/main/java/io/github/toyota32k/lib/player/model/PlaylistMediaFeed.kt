package io.github.toyota32k.lib.player.model

import io.github.toyota32k.binder.list.ObservableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.min

class PlaylistMediaFeed : IMediaFeed {
    override val hasNext = MutableStateFlow(false)
    override val hasPrevious = MutableStateFlow(false)
    override val currentSource = MutableStateFlow<IMediaSource?>(null)
    val currentIndexFlow by lazy { currentSource.map { if(it==null) -1 else videoSources.indexOf(it) } }
    val currentIndex get() = currentSource.value?.let { videoSources.indexOf(it) } ?: -1
    private val videoSources = ObservableList<IMediaSource>()

    private fun sourceAt(index:Int):IMediaSource? {
        return if(0<=index&&index<videoSources.size) {
            videoSources[index]
        } else null
    }

    override fun next() {
        val index = videoSources.indexOf(currentSource.value)
        currentSource.value = sourceAt(index+1) ?: return
    }

    override fun previous() {
        val index = videoSources.indexOf(currentSource.value)
        currentSource.value = sourceAt(index-1) ?: return
    }

    private fun reset() {
        hasPrevious.value = false
        hasNext.value = false
        currentSource.value = null
        videoSources.clear()
    }

    fun setSources(sources:List<IMediaSource>, startIndex:Int=0, position:Long=0L) {
        reset()
        videoSources.addAll(sources)

        if (sources.isNotEmpty()) {
            val si = max(0, min(startIndex, sources.size))
            val pos = max(sources[si].trimming.start, position)
            val src = sources[si]
            src.startPosition.set(pos)

            hasNext.value = si<sources.size-1
            hasPrevious.value = 0<si
            currentSource.value = src
        }
    }

    fun isCurrentSource(index:Int):Boolean {
        return index == currentIndex
    }

    fun isCurrentSource(src:IMediaSource):Boolean {
        return src === currentSource.value
    }

    fun playAt(src:IMediaSource?, position: Long=0L):Boolean {
        val index = videoSources.indexOf(src?:return false)
        if(index<0) return false
        return playAt(index, position)
    }

    fun playAt(index:Int, position:Long=0L):Boolean {
        val src = sourceAt(index) ?: return false
        src.startPosition.set(position)
        currentSource.value = src
        hasPrevious.value = 0<index
        hasNext.value = index<videoSources.size-1
        return true
    }
}
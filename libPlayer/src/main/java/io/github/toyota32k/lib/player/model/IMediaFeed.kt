package io.github.toyota32k.lib.player.model

import kotlinx.coroutines.flow.StateFlow

interface IMediaFeed {
    fun next()
    fun previous()
    val hasNext: StateFlow<Boolean>
    val hasPrevious: StateFlow<Boolean>
    val currentSource: StateFlow<IMediaSource?>
}
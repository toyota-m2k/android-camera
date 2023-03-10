package io.github.toyota32k.lib.player.model

import kotlinx.coroutines.flow.MutableStateFlow

interface ISeekManager {
    val requestedPositionFromSlider : MutableStateFlow<Long>
}
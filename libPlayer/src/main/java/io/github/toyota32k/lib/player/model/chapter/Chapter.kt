package io.github.toyota32k.lib.player.model.chapter

import io.github.toyota32k.lib.player.model.IChapter
import kotlinx.serialization.Serializable

@Serializable
data class Chapter(
    override val position: Long,
    override val label: String = "",
    override val skip: Boolean = false
) : IChapter {
    constructor(src:IChapter):this(src.position, src.label, src.skip)
}

package io.github.toyota32k.lib.player.model.option

import io.github.toyota32k.bindit.LiteUnitCommand
import io.github.toyota32k.lib.player.model.IChapterHandler
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.lib.player.model.IPlayerModel

class ChapterHandlerImpl(
    val playerModel: IPlayerModel,
    override val hideChapterViewIfEmpty: Boolean) : IChapterHandler {
//    init {
//        playerModel.currentSource.onEach(::onSourceChanged).launchIn(playerModel.scope)
//    }

//    override val chapterList = MutableStateFlow<IChapterList?>(null)
//    override val hasChapters = MutableStateFlow(false)
    val chapterList:IChapterList? get() = (playerModel.currentSource.value as? IMediaSourceWithChapter)?.chapterList

//    override var disabledRanges:List<Range>? = null
//        private set

    override val commandNextChapter = LiteUnitCommand(::nextChapter)
    override val commandPrevChapter = LiteUnitCommand(::prevChapter)


//    private suspend fun onSourceChanged(src: IMediaSource?) {
//        chapterList.value = null
//        hasChapters.value = false
//        if(src !is IMediaSourceWithChapter) return
//
//        src.prepareChapterList()?.apply {
//            chapterList.value = this
//            hasChapters.value = chapters.isNotEmpty()
//        }
//    }

    private fun nextChapter() {
        val src = playerModel.currentSource.value as? IMediaSourceWithChapter ?: return
        val c = src.chapterList.run {
            next(playerModel.currentPosition)
        } ?: return
        playerModel.seekTo(c.position)
    }

    private fun prevChapter() {
        val src = playerModel.currentSource.value as? IMediaSourceWithChapter ?: return
        val c = src.chapterList.run {
            prev(playerModel.currentPosition)
        } ?: return
        playerModel.seekTo(c.position)
    }
}
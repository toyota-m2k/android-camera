package io.github.toyota32k.lib.player.model.chapter

import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMutableChapterList
import io.github.toyota32k.lib.player.model.addChapter
import io.github.toyota32k.lib.player.model.chapterAt
import io.github.toyota32k.lib.player.model.chapterOn
import io.github.toyota32k.lib.player.model.removeChapter
import io.github.toyota32k.utils.Listeners
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.lang.IllegalArgumentException

interface IChapterEditor : IMutableChapterList {
    fun undo()
    fun redo()
    val canUndo: Flow<Boolean>
    val canRedo: Flow<Boolean>
}

/**
 * IMutableChapterList にUndo/Redoの機能を授けるｗ
 */
class ChapterEditor(private val target:IMutableChapterList) : IChapterEditor, IChapterList by target {
    interface IOperation {
        fun redo()
        fun undo()
    }

    private val buffer = mutableListOf<IOperation>()
    private val current = MutableStateFlow<Int>(0)     // 次回挿入位置を指している（undoされていなければ buffer.size と等しい）

    inner class AddOperation(private val position:Long, private val label:String, private val skip:Boolean?):IOperation {
        override fun redo() {
            target.addChapter(position, label, skip)
        }
        override fun undo() {
            target.removeChapter(position)
        }
    }

    inner class RemoveOperation(private val index:Int, private val chapter:IChapter):IOperation {
        override fun redo() {
            target.removeChapterAt(index)
        }

        override fun undo() {
            target.addChapter(chapter)
        }
    }


    inner class UpdateOperation(private val position:Long, private val label:String?, private val skip:Boolean?, val chapter:IChapter) : IOperation {
        override fun redo() {
            target.updateChapter(position, label, skip)
        }
        override fun undo() {
            target.updateChapter(chapter.position, chapter.label, chapter.skip)
        }
    }

    private fun addBuffer(op:IOperation) {
        if(current.value<buffer.size) {
            buffer.subList(current.value,  buffer.size).clear()
        }
        buffer.add(op)
        current.value=buffer.size
    }


    override fun addChapter(position: Long, label: String, skip: Boolean?): Boolean {
        if(!target.addChapter(position, label, skip)) {
            return false
        }
        addBuffer(AddOperation(position,label,skip))
        return true
    }

    override fun updateChapter(position: Long, label: String?, skip: Boolean?): Boolean {
        val chapter = chapterOn(position) ?: return false
        if(!target.updateChapter(position, label, skip)) {
            return false
        }
        addBuffer(UpdateOperation(position,label,skip,chapter))
        return true
    }

    override fun removeChapterAt(index: Int): Boolean {
        val chapter: IChapter = target.chapterAt(index) ?: return false
        if(!target.removeChapterAt(index)) {
            return false
        }
        addBuffer(RemoveOperation(index, chapter))
        return true
    }

    override val canUndo = current.map { 0<it }

    override fun undo() {
        if(current.value<=0) return
        current.value--
        buffer[current.value].undo()
    }

    override val canRedo = current.map { it<buffer.size }

    override fun redo() {
        if(buffer.size<=current.value) return
        buffer[current.value].redo()
        current.value++
    }

    override val modifiedListener: Listeners<Unit>
        get() = target.modifiedListener
}
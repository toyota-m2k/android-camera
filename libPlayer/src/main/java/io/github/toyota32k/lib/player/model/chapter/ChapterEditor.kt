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
import kotlinx.coroutines.flow.map

interface IChapterEditor : IMutableChapterList {
    fun undo()
    fun redo()
    val canUndo: Flow<Boolean>
    val canRedo: Flow<Boolean>
    val isDirty:Boolean
    fun clearDirty()
}

/**
 * IMutableChapterList にUndo/Redoの機能を授けるｗ
 */
class ChapterEditor(private val target:IMutableChapterList) : IChapterEditor, IChapterList by target {
    interface IOperation {
        fun redo()
        fun undo()
    }

    private val history = mutableListOf<IOperation>()
    private val current = MutableStateFlow<Int>(0)     // 次回挿入位置を指している（undoされていなければ buffer.size と等しい）
    private var dirtyMark = 0   // 編集開始時はゼロ、編集後保存すると、保存時の"current" の値となる。current.value == dirtyMark なら編集ナシと判断する。

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

    override fun initChapters(chapters: List<IChapter>) {
        target.initChapters(chapters)
    }

    private fun addHistory(op:IOperation) {
        if(current.value<history.size) {
            // undo されているときは、current位置より後ろの履歴を削除してから、新しい履歴(op)を追加する。
            history.subList(current.value,  history.size).clear()
            if(current.value<dirtyMark) {
                // 前回保存した位置からUndoされて、履歴が追加される --> どうやっても保存データの状態にはもどらないので、isDirty が true を返すよう、dirtyMark に無効値(-1)を入れておく。
                dirtyMark = -1
            }
        }
        history.add(op)
        current.value=history.size
    }


    override fun addChapter(position: Long, label: String, skip: Boolean?): Boolean {
        if(!target.addChapter(position, label, skip)) {
            return false
        }
        addHistory(AddOperation(position,label,skip))
        return true
    }

    override fun updateChapter(position: Long, label: String?, skip: Boolean?): Boolean {
        val chapter = chapterOn(position) ?: return false
        if(!target.updateChapter(position, label, skip)) {
            return false
        }
        addHistory(UpdateOperation(position,label,skip,chapter))
        return true
    }

    override fun removeChapterAt(index: Int): Boolean {
        val chapter: IChapter = target.chapterAt(index) ?: return false
        if(!target.removeChapterAt(index)) {
            return false
        }
        addHistory(RemoveOperation(index, chapter))
        return true
    }

    override val canUndo = current.map { 0<it }

    override fun undo() {
        if(current.value<=0) return
        current.value--
        history[current.value].undo()
    }

    override val canRedo = current.map { it<history.size }

    override fun redo() {
        if(history.size<=current.value) return
        history[current.value].redo()
        current.value++
    }

    override val isDirty: Boolean
        get() = current.value != dirtyMark

    override fun clearDirty() {
        dirtyMark = current.value
    }

    override val modifiedListener: Listeners<Unit>
        get() = target.modifiedListener
}
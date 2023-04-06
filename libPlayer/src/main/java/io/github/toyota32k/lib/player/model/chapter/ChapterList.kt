package io.github.toyota32k.lib.player.model.chapter

import androidx.annotation.MainThread
import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.shared.UtSortedList
import io.github.toyota32k.shared.UtSorter
import io.github.toyota32k.utils.UtLog
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ChapterList(mutableList:MutableList<IChapter> = mutableListOf()) : IChapterList {
    private val sortedList = UtSortedList(mutableList, allowDuplication = false, comparator = ::chapterComparator)
    private val workPosition = UtSorter.Position()
    private val workChapter = DmyChapter()  // 検索用のダミー
    private class DmyChapter:IChapter {
        override var position: Long = 0
        override val label: String = ""
        override val skip: Boolean = false
    }

    companion object {
        fun chapterComparator(x:IChapter, y:IChapter):Int {
            val d = x.position - y.position
            return if(d<0) -1 else if(d>0) 1 else 0
        }
        val logger = UtLog("Chapter", null, this::class.java)
        var MIN_CHAPTER_INTERVAL = 500L // チャプターとチャプターの間隔の最小値（500ms） ... 利用者側から設定できるようvarにしておく
    }

    init {
        reset()
    }


    override val chapters: List<IChapter>
        get() = sortedList


    fun reset() {
        sortedList.clear()
        sortedList.add(Chapter(0))  // 先頭チャプターは必ず存在し削除できないこととする。
    }

    fun serialize(): String {
        val list:List<Chapter> = sortedList.map { if(it is Chapter) it else Chapter(it) }
        return Json.encodeToString(list)
    }

    fun deserialize(json:String?) {
        if(json.isNullOrEmpty()) {
            reset()
            return
        }
        try {
            val list = Json.decodeFromString<List<Chapter>>(json)
            sortedList.clear()
            sortedList.addAll(list)
            if(sortedList.size==0 || sortedList[0].position>0) {
                sortedList.add(Chapter(0))
            }
        } catch(e:Throwable) {
            logger.error(e)
            reset()
        }
    }

    override fun prev(current: Long): IChapter? {
        workChapter.position = current
        sortedList.sorter.findPosition(workChapter, workPosition)
        return if(0<=workPosition.prev&&workPosition.prev<sortedList.size) sortedList[workPosition.prev] else null
    }

    override fun next(current: Long): IChapter? {
        workChapter.position = current
        sortedList.sorter.findPosition(workChapter, workPosition)
        return if(0<=workPosition.next&&workPosition.next<sortedList.size) sortedList[workPosition.next] else null
    }

    data class NeighborChapter(val prev:Int, val hit:Int, val next:Int)

    fun NeighborChapter.prevChapter():IChapter? {
        return if(0<=prev&&prev<sortedList.size) sortedList[prev] else null
    }
    fun NeighborChapter.nextChapter():IChapter? {
        return if(0<=next&&next<sortedList.size) sortedList[next] else null
    }
    fun NeighborChapter.hitChapter():IChapter? {
        return if(0<=hit&&hit<sortedList.size) sortedList[hit] else null
    }

    fun getNeighborChapters(pivot:Long): NeighborChapter {
        val count:Int = sortedList.size
        fun clipIndex(index:Int):Int {
            return if(index in 0 until count) index else -1;
        }
        for (i in 0 until count) {
            if(pivot == sortedList[i].position) {
                // ヒットした
                return NeighborChapter(i-1, i, clipIndex(i + 1))
            }
            if(pivot<sortedList[i].position) {
                return NeighborChapter(i-1, -1, i)
            }
        }
        return NeighborChapter(count-1,-1,-1)
    }

    fun addChapter(position:Long, label:String="", skip:Boolean):Boolean {
        val neighbor = getNeighborChapters(position)
        if(neighbor.hit>=0) {
            return false
        }
        if(neighbor.prevChapter()?.let{ position - it.position < MIN_CHAPTER_INTERVAL} == true) {
            return false
        }
        if(neighbor.nextChapter()?.let { it.position - position < MIN_CHAPTER_INTERVAL} == true ) {
            return false
        }
        return sortedList.add(Chapter(position,label,skip))
    }

    fun updateChapter(chapter: Chapter):Boolean {
        return sortedList.replace(chapter)
    }
    fun updateChapter(chapter:IChapter, label:String?=null, skip:Boolean?=null):Boolean {
        return updateChapter(Chapter(chapter.position, label?:chapter.label, skip?:chapter.skip))
    }

    fun skipChapter(chapter:IChapter, skip: Boolean):Boolean {
        return updateChapter(chapter, null, skip)
    }

    fun removeChapter(chapter:IChapter):Boolean {
        if(chapter.position == 0L) return false  // 先頭のChapterは必ず存在し、削除は禁止
        val i = sortedList.sorter.find(chapter)
        if(i<0) return false
        return sortedList.remove(chapter)
    }

    fun getChapterAround(position:Long):IChapter {
        val neighbor = getNeighborChapters(position)
        neighbor.hitChapter()?.apply {
            return this
        }
        neighbor.prevChapter()?.apply {
            return this
        }
        throw java.lang.IllegalStateException("no chapters around $position")
    }

    fun enabledRangesNoTrimming() = sequence<Range> {
        var skipping = false
        var checking = 0L
        for(r in sortedList) {
            if(skipping != r.skip) {
                if(r.skip) {
                    // enabled --> disabled at r.position
                    skipping = true
                    if (checking < r.position) {
                        yield(Range(checking, r.position))
                        checking = r.position
                    }
                } else {
                    // disabled --> enabled at r.position
                    skipping = false
                    checking = r.position
                }
            }
        }
        if(!skipping) {
            yield(Range(checking))
        }
    }

    fun enabledRangesWithTrimming(trimming: Range) = sequence<Range> {
        for(r in enabledRangesNoTrimming()) {
            if(r.end>0 && r.end < trimming.start) {
                // 有効領域 r が、trimming.start によって無効化されるのでスキップ
                //   s         e
                //   |--- r ---|
                // ...............|--- trimming ---
                continue
            } else if(trimming.end>0L && trimming.end<r.start) {
                // 有効領域 r が、trimming.end によって無効化されるのでスキップ
                //                                    s         e
                //                                    |--- r ---|
                // ...............|--- trimming ---|....
                continue
            } else if (r.start <trimming.start) {
                // 有効領域 r の後半が trimming 範囲と重なる
                //   s         e
                //   |--- r ---|
                // ......| --- trimming ---|
                if(r.end>0 && trimming.contains(r.end)) {
                    yield(Range(trimming.start, r.end))
                }
                // trimming 範囲全体が、有効領域 rに含まれる
                //   s         e
                //   |--- r --------------------|
                // ......| --- trimming ---|
                else {
                    yield(trimming)
                }
            } else { // trimming.start < r.start
                // 有効領域 r の前半が trimming の範囲と重なる
                //                   s         e
                //                   |--- r ---|
                // ......| --- trimming ---|
                if(trimming.end>0L && r.contains(trimming.end)) {
                    yield(Range(r.start, trimming.end))
                }
                // 有効範囲 r 全体が　trimming範囲に含まれる
                //           s         e
                //           |--- r ---|
                // ......| --- trimming ---|
                else {
                    yield(r)
                }
            }
        }
    }

    fun enabledRanges(trimming: Range) :Sequence<Range> {
        return if(trimming.start==0L && trimming.end==0L) {
            enabledRangesNoTrimming()
        } else {
            enabledRangesWithTrimming(trimming)
        }
    }

    override fun disabledRanges(trimming: Range)= sequence<Range> {
        var checking = 0L
        for(r in enabledRanges(trimming)) {
            if(checking<r.start) {
                yield(Range(checking, r.start))
            }
            checking = r.end
            if(checking==0L) {
                return@sequence // 残りは最後まで有効（これ以降、無効領域はない）
            }
        }
        yield(Range(checking, 0))
    }
}
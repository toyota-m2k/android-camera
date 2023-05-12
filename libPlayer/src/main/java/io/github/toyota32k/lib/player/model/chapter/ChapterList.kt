package io.github.toyota32k.lib.player.model.chapter

import androidx.annotation.MainThread
import io.github.toyota32k.lib.player.model.*
import io.github.toyota32k.shared.UtSortedList
import io.github.toyota32k.shared.UtSorter
import io.github.toyota32k.utils.Listeners
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.onTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

open class ChapterList(mutableList:MutableList<IChapter> = mutableListOf()) : IChapterList {
    protected val sortedList = UtSortedList(mutableList, actionOnDuplicate = UtSorter.ActionOnDuplicate.REJECT, comparator = ::chapterComparator)
    private val workPosition = UtSorter.Position()
    protected val workChapter = DmyChapter()  // 検索用のダミー
    protected class DmyChapter:IChapter {
        override var position: Long = 0
        override val label: String = ""
        override val skip: Boolean = false
        fun at(p:Long) : IChapter {
            position = p
            return this
        }
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
        sortedList.sorter.findPosition(workChapter.at(current), workPosition)
        return if(0<=workPosition.prev&&workPosition.prev<sortedList.size) sortedList[workPosition.prev] else null
    }

    override fun next(current: Long): IChapter? {
        sortedList.sorter.findPosition(workChapter.at(current), workPosition)
        return if(0<=workPosition.next&&workPosition.next<sortedList.size) sortedList[workPosition.next] else null
    }

    fun NeighborChapter.prevChapter():IChapter? {
        return if(0<=prev&&prev<sortedList.size) sortedList[prev] else null
    }
    fun NeighborChapter.nextChapter():IChapter? {
        return if(0<=next&&next<sortedList.size) sortedList[next] else null
    }
    fun NeighborChapter.hitChapter():IChapter? {
        return if(0<=hit&&hit<sortedList.size) sortedList[hit] else null
    }

    override fun getNeighborChapters(pivot:Long): NeighborChapter {
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
    override fun indexOf(position: Long): Int {
        return sortedList.sorter.find(workChapter.at(position))
    }

    override fun getChapterAround(position:Long):IChapter {
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

    private fun enabledRangesSub(trimming: Range): Sequence<Range> {
        return if(trimming.start==0L && trimming.end==0L) {
            enabledRangesNoTrimming()
        } else {
            enabledRangesWithTrimming(trimming)
        }
    }

    private fun disabledRangesSub(trimming: Range)= sequence<Range> {
        var checking = 0L
        for(r in enabledRangesSub(trimming)) {
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

    inner class RangeCache {
        private var mEnabledRange:List<Range>? = null
        private var mDisabledRange:List<Range>? = null
        private var mTrimming:Range = Range.empty

        fun invalidate() {
            mEnabledRange = null
            mDisabledRange = null
            mTrimming = Range.empty
        }

        fun enabledRange(trimming:Range):List<Range> {
            if(mTrimming!==trimming) {
                mEnabledRange = null
            }
            return mEnabledRange ?: enabledRangesSub(trimming).toList().apply { mEnabledRange = this }
        }

        fun disabledRange(trimming:Range):List<Range> {
            if(mTrimming!==trimming) {
                mDisabledRange = null
            }
            return mDisabledRange ?: disabledRangesSub(trimming).toList().apply { mDisabledRange = this }
        }
    }
    protected val rangeCache by lazy { RangeCache() }

    override fun enabledRanges(trimming: Range) :List<Range> {
        return rangeCache.enabledRange(trimming)
    }

    override fun disabledRanges(trimming: Range): List<Range> {
        return rangeCache.disabledRange(trimming)
    }
}

class MutableChapterList : ChapterList(), IMutableChapterList {
    override val modifiedListener = Listeners<Unit>()
    /**
     * チャプターを挿入
     *
     * @param position  挿入位置
     * @param label チャプター名（任意）
     * @param skip false: スキップしない / true:スキップする / null: 挿入位置の状態を継承
     * @return  true: 挿入した / 挿入できなかった
     */
    override fun addChapter(position:Long, label:String, skip:Boolean?):Boolean {
        val neighbor = getNeighborChapters(position)
        if(neighbor.hit>=0) {
            return false
        }
        if(neighbor.prevChapter()?.let{ position - it.position < ChapterList.MIN_CHAPTER_INTERVAL } == true) {
            return false
        }
        if(neighbor.nextChapter()?.let { it.position - position < ChapterList.MIN_CHAPTER_INTERVAL } == true ) {
            return false
        }
        return sortedList.add(Chapter(position,label,skip?:neighbor.prevChapter()?.skip?:false)).onTrue(::invalidate)
    }

    /**
     * チャプターの属性を変更
     *
     * @param position  挿入位置
     * @param label チャプター名 / nullなら変更しない
     * @param skip false: スキップしない / true:スキップする / null: 変更しない
     * @return true: 変更した / false: 変更しなかった（チャプターが存在しない、or 属性が変化しない）
     */
    override fun updateChapter(position:Long, label:String?, skip:Boolean?):Boolean {
        val index = indexOf(position)
        if(index<0) return false
        if(label==null && skip==null) return false
        val chapter = sortedList[index]
        if((label==null || chapter.label == label) && (skip!=null && chapter.skip == skip)) return false
        return sortedList.replace(Chapter(position, label?:chapter.label, skip?:chapter.skip)).onTrue(::invalidate)
    }

    /**
     * チャプターを削除する
     * @param position 削除するチャプターのposition
     * @return true: 変更した / false: 変更しなかった（チャプターが存在しない　or 削除禁止の先頭チャプター）
     */
//    override fun removeChapter(position: Long): Boolean {
//        if(position == 0L) return false  // 先頭のChapterは必ず存在し、削除は禁止
//        val i = indexOf(position)
//        if(i<0) return false    // 存在しない
//        sortedList.removeAt(i)
//        invalidate()
//        return true
//    }

    override fun removeChapterAt(index: Int): Boolean {
        if(index<=0||sortedList.size<=index) return false
        sortedList.removeAt(index)
        invalidate()
        return true
    }

    private fun invalidate() {
        rangeCache.invalidate()
        modifiedListener.invoke(Unit)
    }
}
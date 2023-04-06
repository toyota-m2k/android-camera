package io.github.toyota32k.lib.player.model

import io.github.toyota32k.utils.Listeners

interface IChapter {
    val position:Long
    val label:String
    val skip:Boolean
}

interface IChapterList {
    val chapters:List<IChapter>
    fun prev(current:Long) : IChapter?
    fun next(current:Long) : IChapter?
    fun getChapterAround(position:Long):IChapter
    fun enabledRanges(trimming: Range) : Sequence<Range>
    fun disabledRanges(trimming: Range) : Sequence<Range>
}

interface IMutableChapterList : IChapterList {
    /**
     * チャプターを挿入
     *
     * @param position  挿入位置
     * @param label チャプター名（任意）
     * @param skip false: スキップしない / true:スキップする / null: 挿入位置の状態を継承
     * @return  true: 挿入した / 挿入できなかった
     */
    fun addChapter(position: Long, label:String="", skip:Boolean?=null):Boolean

    /**
     * チャプターの属性を変更
     *
     * @param position  挿入位置
     * @param label チャプター名 / nullなら変更しない
     * @param skip false: スキップしない / true:スキップする / null: 変更しない
     * @return true: 変更した / false: 変更しなかった（チャプターが存在しない、or 属性が変化しない）
     */
    fun updateChapter(position: Long, label:String?=null, skip:Boolean?=null):Boolean
    fun updateChapter(chapter:IChapter, label:String?=null, skip:Boolean?=null):Boolean
        = updateChapter(chapter.position, label, skip)

    fun skipChapter(position:Long, skip: Boolean):Boolean {
        return updateChapter(position, null, skip)
    }
    fun skipChapter(chapter: IChapter, skip: Boolean):Boolean
        = skipChapter(chapter.position, skip)
    /**
     * チャプターを削除する
     * @param position 削除するチャプターのposition
     * @return true: 変更した / false: 変更しなかった（チャプターが存在しない　or 削除禁止の先頭チャプター）
     */
    fun removeChapter(position:Long):Boolean
    fun removeChapter(chapter: IChapter):Boolean
        = removeChapter(chapter.position)

    val modifiedListener: Listeners<Unit>
}
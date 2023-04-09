package io.github.toyota32k.lib.player.model

import java.util.concurrent.atomic.AtomicLong

interface IMediaSource {
    val id:String
    val name:String
    val uri:String
    val trimming: Range
    val type: String    // 拡張子(.なし）
    var startPosition: AtomicLong
//    val disabledRanges: List<Range>    // null: 未定義（初期値） / empty: 無効領域なし
//
//    suspend fun getChapterList():IChapterList?
//

//    val chapterList: IChapterList?
//    val disabledRanges:List<Range> get() = chapterList?.disabledRanges(trimming)?.toList() ?: emptyList()
//    val hasChapter:Boolean get() = (chapterList?.chapters?.size ?: 0)>0
}

interface IMediaSourceWithChapter : IMediaSource {
    val chapterList:IChapterList
}
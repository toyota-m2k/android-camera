package io.github.toyota32k.player.lib

import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.lib.player.model.chapter.Chapter
import io.github.toyota32k.lib.player.model.chapter.ChapterList
import org.junit.Test
import org.junit.Assert.*

class ChapterTest {
    @Test
    fun addRemoveChapterTest() {
        ChapterList.MIN_CHAPTER_INTERVAL = 100L
        val chapterList = ChapterList()
        assertEquals(1, chapterList.chapters.size)
        for(i in 1..10) {
            chapterList.addChapter(i*1000L, "$i", false)
        }
        assertEquals(11, chapterList.chapters.size)
        assertEquals(0L, chapterList.chapters[0].position)
        assertEquals(10000L, chapterList.chapters[10].position)
        assertEquals(5000L, chapterList.chapters[5].position)
        var chapter = chapterList.getChapterAround(5500)
        assertEquals(5000L, chapter.position)
        assertTrue(chapterList.removeChapter(chapter))
        assertEquals(10, chapterList.chapters.size)
        assertFalse(chapterList.removeChapter(chapter))
        chapter = chapterList.getChapterAround(5500)
        assertEquals(4000L, chapter.position)
        chapterList.addChapter(8500, "850", false)
        assertEquals(11, chapterList.chapters.size)
        assertArrayEquals(longArrayOf(0,1000,2000,3000,4000,6000,7000,8000,8500,9000,10000), chapterList.chapters.map{it.position}.toLongArray())
    }

    @Test
    fun updateTest() {
        ChapterList.MIN_CHAPTER_INTERVAL = 100L
        val chapterList = ChapterList()
        assertEquals(1, chapterList.chapters.size)
        for(i in 1..10) {
            chapterList.addChapter(i*1000L, "$i", false)
        }
        assertTrue(chapterList.updateChapter(chapterList.getChapterAround(5500), "xxx", null))
        assertEquals("xxx", chapterList.chapters[5].label)
        assertFalse(chapterList.chapters[5].skip)
        assertTrue(chapterList.updateChapter(chapterList.getChapterAround(5500), null, true))
        assertEquals("xxx", chapterList.chapters[5].label)
        assertTrue(chapterList.chapters[5].skip)
        assertTrue(chapterList.updateChapter(chapterList.getChapterAround(5500), "yyy", false))
        assertEquals("yyy", chapterList.chapters[5].label)
        assertFalse(chapterList.chapters[5].skip)
        assertTrue(chapterList.updateChapter(Chapter(8000, "zzz", true)))
        assertEquals("zzz", chapterList.chapters[8].label)
        assertTrue(chapterList.chapters[8].skip)
        assertTrue(chapterList.updateChapter(chapterList.chapters[0], null, true))
        assertTrue(chapterList.chapters[0].skip)

        chapterList.reset()
        assertEquals(1, chapterList.chapters.size)
        assertEquals(0, chapterList.chapters[0].position)
        assertFalse(chapterList.chapters[0].skip)
    }

    @Test
    fun serializeTest() {
        ChapterList.MIN_CHAPTER_INTERVAL = 100L
        val chapterList = ChapterList()
        assertEquals(1, chapterList.chapters.size)
        for(i in 1..10) {
            chapterList.addChapter(i*1000L, "$i", i%3==0)
        }

        val json = chapterList.serialize()
        val cl2 = ChapterList()
        cl2.deserialize(json)

        assertArrayEquals(chapterList.chapters.map{it.position}.toLongArray(), cl2.chapters.map{it.position}.toLongArray())
        assertArrayEquals(chapterList.chapters.map{it.label}.toTypedArray(), cl2.chapters.map{it.label}.toTypedArray())
        assertArrayEquals(chapterList.chapters.map{it.skip}.toBooleanArray(), cl2.chapters.map{it.skip}.toBooleanArray())
    }

    @Test
    fun rangeNoTrimTest() {
        ChapterList.MIN_CHAPTER_INTERVAL = 100L
        val chapterList = ChapterList()
        assertEquals(1, chapterList.chapters.size)
        // 有効領域で始まり、有効領域で終わるケース
        for(i in 1..10) {
            chapterList.addChapter(i*1000L, "$i", i%3==0||i%4==0)
            // 0,1,2,*3,*4,5,*6,7,*8,*9,10
            // -----      --   --       --
            //      xxxxxx  xxx  xxxxxxx
        }

        var ranges = chapterList.enabledRangesNoTrimming().toList()
        assertEquals(4, ranges.size)
        assertEquals(0, ranges[0].start)
        assertEquals(3000, ranges[0].end)
        assertEquals(5000, ranges[1].start)
        assertEquals(6000, ranges[1].end)
        assertEquals(7000, ranges[2].start)
        assertEquals(8000, ranges[2].end)
        assertEquals(10000, ranges[3].start)
        assertEquals(0, ranges[3].end)

        ranges = chapterList.disabledRanges(Range.empty).toList()
        assertEquals(3, ranges.size)
        assertEquals(3000, ranges[0].start)
        assertEquals(5000, ranges[0].end)
        assertEquals(6000, ranges[1].start)
        assertEquals(7000, ranges[1].end)
        assertEquals(8000, ranges[2].start)
        assertEquals(10000, ranges[2].end)

        // 有効領域で始まり、無効領域で終わるケース
        chapterList.reset()
        for(i in 1..10) {
            chapterList.addChapter(i*1000L, "$i", i%3==0||i%4==0||i==10)
            // 0,1,2,*3,*4,5,*6,7,*8,*9,*10
            // -----      --   --
            //      xxxxxx  xxx  xxxxxxxxxx
        }
        ranges = chapterList.enabledRangesNoTrimming().toList()
        assertEquals(3, ranges.size)
        assertEquals(0, ranges[0].start)
        assertEquals(3000, ranges[0].end)
        assertEquals(5000, ranges[1].start)
        assertEquals(6000, ranges[1].end)
        assertEquals(7000, ranges[2].start)
        assertEquals(8000, ranges[2].end)

        ranges = chapterList.disabledRanges(Range.empty).toList()
        assertEquals(3, ranges.size)
        assertEquals(3000, ranges[0].start)
        assertEquals(5000, ranges[0].end)
        assertEquals(6000, ranges[1].start)
        assertEquals(7000, ranges[1].end)
        assertEquals(8000, ranges[2].start)
        assertEquals(0, ranges[2].end)

        // 無効領域で始まり、有効領域で終わるケース
        chapterList.reset()
        for(i in 1..10) {
            chapterList.addChapter(i*1000L, "$i", i<3||i%3==0||i%4==0)
            // *0,*1,*2,*3,*4,5,*6,7,*8,*9,10
            //                --   --      --
            // xxxxxxxxxxxxxxx  xxx  xxxxxxx
        }
        chapterList.updateChapter(Chapter(0,"x", true))
        ranges = chapterList.enabledRangesNoTrimming().toList()
        assertEquals(3, ranges.size)
        assertEquals(5000, ranges[0].start)
        assertEquals(6000, ranges[0].end)
        assertEquals(7000, ranges[1].start)
        assertEquals(8000, ranges[1].end)
        assertEquals(10000, ranges[2].start)
        assertEquals(0, ranges[2].end)

        ranges = chapterList.disabledRanges(Range.empty).toList()
        assertEquals(3, ranges.size)
        assertEquals(0, ranges[0].start)
        assertEquals(5000, ranges[0].end)
        assertEquals(6000, ranges[1].start)
        assertEquals(7000, ranges[1].end)
        assertEquals(8000, ranges[2].start)
        assertEquals(10000, ranges[2].end)

        // 無効領域で始まり、無効領域で終わるケース
        chapterList.reset()
        for(i in 1..10) {
            chapterList.addChapter(i*1000L, "$i", i<3||i%3==0||i%4==0||i==10)
            // *0,*1,*2,*3,*4,5,*6,7,*8,*9,10
            //                --   --
            // xxxxxxxxxxxxxxx  xxx  xxxxxxxx
        }
        chapterList.updateChapter(Chapter(0,"x", true))
        ranges = chapterList.enabledRangesNoTrimming().toList()
        assertEquals(2, ranges.size)
        assertEquals(5000, ranges[0].start)
        assertEquals(6000, ranges[0].end)
        assertEquals(7000, ranges[1].start)
        assertEquals(8000, ranges[1].end)

        ranges = chapterList.disabledRanges(Range.empty).toList()
        assertEquals(3, ranges.size)
        assertEquals(0, ranges[0].start)
        assertEquals(5000, ranges[0].end)
        assertEquals(6000, ranges[1].start)
        assertEquals(7000, ranges[1].end)
        assertEquals(8000, ranges[2].start)
        assertEquals(0, ranges[2].end)
    }

    @Test
    fun rangeWithTrimming() {
        ChapterList.MIN_CHAPTER_INTERVAL = 100L
        val chapterList = ChapterList()

        // ------|== trimming ==|-----
        // ---|=== Chapter =======|---
        chapterList.skipChapter(chapterList.chapters[0], true)
        chapterList.addChapter(2000, "enabled", false)
        chapterList.addChapter(8000, "disabled", true)
        var ranges = chapterList.enabledRanges(Range(3000,5000)).toList()
        assertEquals(1, ranges.size)
        assertEquals(Range(3000,5000), ranges[0])
        ranges = chapterList.disabledRanges(Range(3000,5000)).toList()
        assertEquals(2, ranges.size)
        assertEquals(Range(0,3000), ranges[0])
        assertEquals(Range(5000,0), ranges[1])

        // ----|====== trimming =====|-----
        // -------|== Chapter ==|----------
        chapterList.reset()
        chapterList.skipChapter(chapterList.chapters[0], true)
        chapterList.addChapter(3000, "enabled", false)
        chapterList.addChapter(5000, "disabled", true)
        ranges = chapterList.enabledRanges(Range(2000,8000)).toList()
        assertEquals(1, ranges.size)
        assertEquals(Range(3000,5000), ranges[0])
        ranges = chapterList.disabledRanges(Range(2000,8000)).toList()
        assertEquals(2, ranges.size)
        assertEquals(Range(0,3000), ranges[0])
        assertEquals(Range(5000,0), ranges[1])


        // ----|====== trimming =====|-----
        // ======= Chapter ==|----------
        chapterList.reset()
        chapterList.addChapter(5000, "disabled", true)
        ranges = chapterList.enabledRanges(Range(2000,8000)).toList()
        assertEquals(1, ranges.size)
        assertEquals(Range(2000,5000), ranges[0])
        ranges = chapterList.disabledRanges(Range(2000,8000)).toList()
        assertEquals(2, ranges.size)
        assertEquals(Range(0,2000), ranges[0])
        assertEquals(Range(5000,0), ranges[1])

        // ----|=== trimming ===|---------------
        // ----------|=== Chapter ===|----------
        chapterList.reset()
        chapterList.skipChapter(chapterList.chapters[0], true)
        chapterList.addChapter(4000, "enabled", false)
        chapterList.addChapter(8000, "disabled", true)
        ranges = chapterList.enabledRanges(Range(3000,5000)).toList()
        assertEquals(1, ranges.size)
        assertEquals(Range(4000,5000), ranges[0])
        ranges = chapterList.disabledRanges(Range(3000,5000)).toList()
        assertEquals(2, ranges.size)
        assertEquals(Range(0,4000), ranges[0])
        assertEquals(Range(5000,0), ranges[1])

    }
}
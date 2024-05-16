package io.github.toyota32k.secureCamera

import io.github.toyota32k.shared.UtSorter
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun sorterTest() {
        val list = mutableListOf<Int>(10, 5, 30, 15, 20)
        val comparator = Comparator<Int> { a,b-> a-b }  // 昇順
        val utSorter = io.github.toyota32k.shared.UtSorter<Int>(list, UtSorter.ActionOnDuplicate.REJECT, comparator)

        assertEquals(5, list.first())
        assertEquals(30, list.last())
        assertEquals(2, utSorter.find(15))

        val i = utSorter.add(25)
        assertEquals(6, list.size)
        assertEquals(4, i)

        val j = utSorter.add(5)
        assertEquals(-1, j)
    }
}
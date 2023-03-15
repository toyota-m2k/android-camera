package io.github.toyota32k.camera

import io.github.toyota32k.lib.camera.usecase.ITcUseCase
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun filenameToDateTest() {
        val filename = ITcUseCase.defaultFileName("img-", ".jpg")
        val dateString = filename.substringAfter("img-").substringBefore(".jpg")
        val date = ITcUseCase.dateFormatForFilename.parse(dateString)
        assertNotNull(date)
        val dateString2 = ITcUseCase.dateFormatForFilename.format(date!!)
        assertEquals(dateString, dateString2)
    }
}
package kr.hebi.comicreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderTest {
    @Test
    fun numericPageNamesFollowNaturalOrder() {
        assertTrue(compareNatural("1.jpg", "2.jpg") < 0)
        assertTrue(compareNatural("2.jpg", "10.jpg") < 0)
        assertTrue(compareNatural("page9.webp", "page10.webp") < 0)
    }

    @Test
    fun numbersDoNotOverflow() {
        assertTrue(compareNatural("page99999999999999999999.jpg", "page100000000000000000000.jpg") < 0)
    }

    @Test
    fun ignoresCase() {
        assertEquals(0, compareNatural("PAGE1.JPG", "page1.jpg"))
    }
}

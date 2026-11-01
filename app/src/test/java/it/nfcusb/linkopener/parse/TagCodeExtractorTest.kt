package it.nfcusb.linkopener.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagCodeExtractorTest {
    private val pattern = TagCodeExtractor.compileOrDefault(TagCodeExtractor.DEFAULT_PATTERN)

    @Test
    fun extractsLastPathSegment() {
        assertEquals("P04a", TagCodeExtractor.extract("https://esempio.it/tags/P04a"))
        assertEquals("C02b", TagCodeExtractor.extract("http://esempio.it/tags/C02b/"))
        assertEquals("P7B", TagCodeExtractor.extract("https://esempio.it/a/b/P7B?x=1"))
    }

    @Test
    fun acceptsDefaultCodeShape() {
        assertTrue(pattern.matches("P04a"))
        assertTrue(pattern.matches("P7B"))
        assertTrue(pattern.matches("C02b"))
        assertTrue(pattern.matches("AB12z"))
    }

    @Test
    fun rejectsCodesOutsideTheDefaultShape() {
        assertFalse(pattern.matches("P04"))
        assertFalse(pattern.matches("04a"))
        assertFalse(pattern.matches("P04aa"))
        assertFalse(pattern.matches("tag"))
        assertFalse(pattern.matches(""))
    }

    @Test
    fun missingPathHasNoCode() {
        assertNull(TagCodeExtractor.extract("https://esempio.it"))
        assertNull(TagCodeExtractor.extract("https://esempio.it/"))
    }

    @Test
    fun invalidPatternFallsBackToDefault() {
        val fallback = TagCodeExtractor.compileOrDefault("[")
        assertTrue(fallback.matches("P04a"))
        assertFalse(TagCodeExtractor.isPatternValid("["))
        assertTrue(TagCodeExtractor.isPatternValid(TagCodeExtractor.DEFAULT_PATTERN))
    }
}

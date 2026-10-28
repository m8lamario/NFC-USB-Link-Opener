package it.nfcusb.linkopener.parse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlDebouncerTest {
    private val url = "https://esempio.it/tags/P04a"

    @Test
    fun ignoresSameUrlInsideWindowAndAcceptsItAgainAtBoundary() {
        var time = 0L
        val debouncer = UrlDebouncer(windowMs = 1_500L) { time }

        assertTrue(debouncer.shouldOpen(url))
        time = 1_499L
        assertFalse(debouncer.shouldOpen(url))
        time = 1_500L
        assertTrue(debouncer.shouldOpen(url))
    }

    @Test
    fun ignoredDuplicateDoesNotExtendWindow() {
        var time = 0L
        val debouncer = UrlDebouncer(windowMs = 1_500L) { time }

        assertTrue(debouncer.shouldOpen(url))
        time = 1_000L
        assertFalse(debouncer.shouldOpen(url))
        time = 1_500L
        assertTrue(debouncer.shouldOpen(url))
    }

    @Test
    fun differentUrlIsAlwaysAccepted() {
        var time = 0L
        val debouncer = UrlDebouncer(windowMs = 1_500L) { time }

        assertTrue(debouncer.shouldOpen(url))
        assertTrue(debouncer.shouldOpen("https://altro.it/x"))
        assertFalse(debouncer.shouldOpen(url))
    }

    @Test
    fun sampleDuplicateIsIgnoredWhenDeliveredImmediately() {
        var time = 10L
        val debouncer = UrlDebouncer { time }
        assertTrue(debouncer.shouldOpen(SampleLines.VALID.removePrefix("URL:")))
        assertFalse(debouncer.shouldOpen(SampleLines.DUPLICATE.removePrefix("URL:")))
    }
}

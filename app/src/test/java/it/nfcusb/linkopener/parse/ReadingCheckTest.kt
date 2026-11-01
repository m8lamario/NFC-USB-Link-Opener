package it.nfcusb.linkopener.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingCheckTest {
    private val openAll = DomainPolicy(enabled = false, domains = emptySet(), openUnlisted = false)
    private val pattern = TagCodeExtractor.DEFAULT_PATTERN

    @Test
    fun acceptsSampleTag() {
        val result = ReadingCheck.check(SampleLines.VALID, openAll, pattern)
        assertTrue(result is CheckResult.Accepted)
        assertEquals("P04a", (result as CheckResult.Accepted).code)
    }

    @Test
    fun rejectsInvalidCodeShape() {
        val result = ReadingCheck.check("https://esempio.it/tags/P04", openAll, pattern)
        assertTrue(result is CheckResult.Rejected)
        assertEquals(CheckResult.Kind.Code, (result as CheckResult.Rejected).kind)
    }

    @Test
    fun rejectsUnlistedDomainUnlessOpeningIsAllowed() {
        val locked = DomainPolicy(enabled = true, domains = setOf("esempio.it"), openUnlisted = false)
        val rejected = ReadingCheck.check("https://altro.it/tags/P04a", locked, pattern)
        assertTrue(rejected is CheckResult.Rejected)
        assertEquals(CheckResult.Kind.Domain, (rejected as CheckResult.Rejected).kind)

        val open = locked.copy(openUnlisted = true)
        assertTrue(ReadingCheck.check("https://altro.it/tags/P04a", open, pattern) is CheckResult.Accepted)
    }

    @Test
    fun whitelistDisabledIgnoresTheDomainList() {
        val policy = DomainPolicy(enabled = false, domains = setOf("esempio.it"), openUnlisted = false)
        assertTrue(ReadingCheck.check("https://altro.it/tags/P7B", policy, pattern) is CheckResult.Accepted)
    }
}

package it.nfcusb.linkopener.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlLineParserTest {
    private val anyHost = emptySet<String>()

    @Test
    fun acceptsSampleHttpsLine() {
        val parsed = UrlLineParser.parse(SampleLines.VALID, anyHost)
        assertTrue(parsed is ParsedLine.Valid)
        assertEquals("https://esempio.it/tags/P04a", (parsed as ParsedLine.Valid).url)
    }

    @Test
    fun trimsUrlButRequiresExactPrefix() {
        val parsed = UrlLineParser.parse("URL:  http://esempio.it/a  ", anyHost)
        assertEquals("http://esempio.it/a", (parsed as ParsedLine.Valid).url)
        assertTrue(UrlLineParser.parse(" URL:https://esempio.it", anyHost) is ParsedLine.Ignored)
    }

    @Test
    fun ignoresDebugLinesAndBlankUrlIsMalformed() {
        assertTrue(UrlLineParser.parse(SampleLines.DEBUG, anyHost) is ParsedLine.Ignored)
        assertTrue(UrlLineParser.parse("boot: pronto", anyHost) is ParsedLine.Ignored)
        assertTrue(UrlLineParser.parse("URL:", anyHost) is ParsedLine.Malformed)
        assertTrue(UrlLineParser.parse("URL:   ", anyHost) is ParsedLine.Malformed)
    }

    @Test
    fun rejectsNonHttpSchemesAndMissingHost() {
        assertTrue(UrlLineParser.parse(SampleLines.INVALID, anyHost) is ParsedLine.Malformed)
        assertTrue(UrlLineParser.parse("URL:ftp://esempio.it/a", anyHost) is ParsedLine.Malformed)
        assertTrue(UrlLineParser.parse("URL:HTTPS://esempio.it", anyHost) is ParsedLine.Malformed)
        assertTrue(UrlLineParser.parse("URL:https://", anyHost) is ParsedLine.Malformed)
        assertTrue(UrlLineParser.parse("URL:javascript:alert(1)", anyHost) is ParsedLine.Malformed)
    }

    @Test
    fun emptyAllowListAcceptsAnyHttpHost() {
        val parsed = UrlLineParser.parse("URL:https://altro.it/x", anyHost)
        assertTrue(parsed is ParsedLine.Valid)
    }

    @Test
    fun allowListMatchesHostAndSubdomainOnly() {
        val allowed = UrlLineParser.normalizeDomains("*.esempio.it\n# commento\nnon valido")
        assertEquals(setOf("esempio.it"), allowed)
        assertTrue(UrlLineParser.parse("URL:https://esempio.it/tags/P04a", allowed) is ParsedLine.Valid)
        assertTrue(UrlLineParser.parse("URL:https://tag.esempio.it/a", allowed) is ParsedLine.Valid)
        assertTrue(UrlLineParser.parse("URL:https://altro.it/a", allowed) is ParsedLine.RejectedDomain)
        assertTrue(UrlLineParser.parse("URL:https://notesempio.it/a", allowed) is ParsedLine.RejectedDomain)
        assertEquals(listOf("non valido"), UrlLineParser.invalidDomainLines("non valido\nesempio.it"))
    }

    @Test
    fun hostMatchIsCaseInsensitive() {
        val allowed = UrlLineParser.normalizeDomains("Esempio.IT")
        val parsed = UrlLineParser.parse("URL:https://Tag.Esempio.IT/a", allowed)
        assertTrue(parsed is ParsedLine.Valid)
    }
}

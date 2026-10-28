package it.nfcusb.linkopener.parse

import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

sealed class ParsedLine {
    data object Ignored : ParsedLine()

    data class Malformed(val rawUrl: String) : ParsedLine()

    data class RejectedDomain(val url: String, val host: String) : ParsedLine()

    data class Valid(val url: String) : ParsedLine()
}

object UrlLineParser {
    const val PREFIX = "URL:"
    private const val MAX_URL_LENGTH = 2048
    private val hostRegex = Regex(
        "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$",
    )

    fun parse(line: String, allowedDomains: Set<String>): ParsedLine {
        if (!line.startsWith(PREFIX)) return ParsedLine.Ignored
        val rawUrl = line.removePrefix(PREFIX).trim()
        if (rawUrl.isEmpty() || rawUrl.length > MAX_URL_LENGTH) {
            return ParsedLine.Malformed(rawUrl)
        }
        if (!rawUrl.startsWith("https://") && !rawUrl.startsWith("http://")) {
            return ParsedLine.Malformed(rawUrl)
        }
        if (rawUrl.any { it.isWhitespace() || it.isISOControl() }) {
            return ParsedLine.Malformed(rawUrl)
        }
        val uri = try {
            URI(rawUrl)
        } catch (_: URISyntaxException) {
            return ParsedLine.Malformed(rawUrl)
        }
        if (uri.scheme != "https" && uri.scheme != "http") {
            return ParsedLine.Malformed(rawUrl)
        }
        val host = asciiHost(uri.host) ?: return ParsedLine.Malformed(rawUrl)
        if (!isHostAllowed(host, allowedDomains)) {
            return ParsedLine.RejectedDomain(rawUrl, host)
        }
        return ParsedLine.Valid(rawUrl)
    }

    fun normalizeDomains(text: String): Set<String> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { normalizeDomainToken(it) }
            .toSet()
    }

    fun invalidDomainLines(text: String): List<String> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .filter { normalizeDomainToken(it) == null }
            .toList()
    }

    fun isHostAllowed(host: String, allowed: Set<String>): Boolean {
        if (allowed.isEmpty()) return true
        val normalized = asciiHost(host) ?: return false
        return allowed.any { domain ->
            normalized == domain || normalized.endsWith(".$domain")
        }
    }

    private fun normalizeDomainToken(token: String): String? {
        val stripped = token
            .trim()
            .lowercase(Locale.ROOT)
            .removePrefix("*.")
            .removePrefix(".")
            .trimEnd('.')
        return asciiHost(stripped)
    }

    private fun asciiHost(host: String?): String? {
        if (host.isNullOrBlank()) return null
        val ascii = try {
            IDN.toASCII(host.trim().trimEnd('.'), IDN.USE_STD3_ASCII_RULES)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val normalized = ascii.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (normalized.isEmpty() || !hostRegex.matches(normalized)) return null
        return normalized
    }
}

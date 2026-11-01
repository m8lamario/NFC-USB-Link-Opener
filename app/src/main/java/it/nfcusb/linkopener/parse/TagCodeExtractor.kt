package it.nfcusb.linkopener.parse

import java.net.URI
import java.net.URLDecoder

object TagCodeExtractor {
    const val DEFAULT_PATTERN = "^[A-Za-z]+[0-9]+[A-Za-z]$"

    fun extract(url: String): String? {
        val path = try {
            URI(url).path
        } catch (_: Exception) {
            return null
        } ?: return null
        val segment = path.trimEnd('/').substringAfterLast('/').trim()
        if (segment.isEmpty()) return null
        val decoded = try {
            URLDecoder.decode(segment, Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            segment
        }
        return decoded.trim().takeIf { it.isNotEmpty() }
    }

    fun compileOrDefault(pattern: String): Regex {
        val source = pattern.trim().ifBlank { DEFAULT_PATTERN }
        return try {
            Regex(source)
        } catch (_: IllegalArgumentException) {
            Regex(DEFAULT_PATTERN)
        }
    }

    fun isPatternValid(pattern: String): Boolean {
        val source = pattern.trim()
        if (source.isEmpty()) return false
        return try {
            Regex(source)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

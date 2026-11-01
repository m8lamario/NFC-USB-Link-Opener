package it.nfcusb.linkopener.parse

sealed class CheckResult {
    data class Accepted(val url: String, val code: String) : CheckResult()

    data class Rejected(
        val url: String,
        val code: String?,
        val kind: Kind,
    ) : CheckResult()

    enum class Kind {
        Malformed,
        Domain,
        Code,
    }
}

object ReadingCheck {
    fun check(url: String, policy: DomainPolicy, pattern: String): CheckResult {
        val line = if (url.startsWith(UrlLineParser.PREFIX)) url else "${UrlLineParser.PREFIX}$url"
        return when (val parsed = UrlLineParser.parse(line, policy)) {
            ParsedLine.Ignored,
            is ParsedLine.Malformed,
            -> CheckResult.Rejected(url.trim(), null, CheckResult.Kind.Malformed)
            is ParsedLine.RejectedDomain -> CheckResult.Rejected(parsed.url, TagCodeExtractor.extract(parsed.url), CheckResult.Kind.Domain)
            is ParsedLine.Valid -> {
                val code = TagCodeExtractor.extract(parsed.url)
                if (code == null || !TagCodeExtractor.compileOrDefault(pattern).matches(code)) {
                    CheckResult.Rejected(parsed.url, code, CheckResult.Kind.Code)
                } else {
                    CheckResult.Accepted(parsed.url, code)
                }
            }
        }
    }
}

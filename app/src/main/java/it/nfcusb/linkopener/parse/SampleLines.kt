package it.nfcusb.linkopener.parse

object SampleLines {
    const val VALID = "URL:https://esempio.it/tags/P04a"
    const val DUPLICATE = VALID
    const val INVALID = "URL:non-e-un-url"
    const val UNAUTHORIZED = "URL:https://non-autorizzato.test/tags/P04a"
    const val DEBUG = "DEBUG: lettura tag senza URL"

    val sequence: List<String> = listOf(VALID, DUPLICATE, INVALID, DEBUG)
}

package it.nfcusb.linkopener.parse

data class DomainPolicy(
    val enabled: Boolean,
    val domains: Set<String>,
    val openUnlisted: Boolean,
)

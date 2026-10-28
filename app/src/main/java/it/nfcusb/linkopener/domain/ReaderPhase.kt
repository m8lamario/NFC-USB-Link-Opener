package it.nfcusb.linkopener.domain

enum class ReaderPhase {
    NotConnected,
    WaitingPermission,
    Active,
    Unplugged,
}

package it.nfcusb.linkopener.domain

data class SerialDeviceChoice(
    val key: String,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val portIndex: Int,
    val label: String,
)

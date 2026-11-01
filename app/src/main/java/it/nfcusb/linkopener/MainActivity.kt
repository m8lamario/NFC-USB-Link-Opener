package it.nfcusb.linkopener

import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import it.nfcusb.linkopener.ui.AppRoot
import it.nfcusb.linkopener.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deliverUsbAttach(intent)
        setContent {
            AppTheme {
                AppRoot(application as NfcUsbLinkApp)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as NfcUsbLinkApp).runtime.onActivityStarted()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverUsbAttach(intent)
    }

    private fun deliverUsbAttach(intent: Intent) {
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            (application as NfcUsbLinkApp).runtime.onUsbAttach()
        }
    }
}

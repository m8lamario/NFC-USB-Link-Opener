package it.nfcusb.linkopener

import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import it.nfcusb.linkopener.ui.AppTheme
import it.nfcusb.linkopener.ui.ReaderScreen
import it.nfcusb.linkopener.ui.ReaderViewModel
import it.nfcusb.linkopener.ui.ReaderViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deliverUsbAttach(intent)
        setContent {
            AppTheme {
                val runtime = (application as NfcUsbLinkApp).runtime
                val viewModel: ReaderViewModel = viewModel(factory = ReaderViewModelFactory(runtime))
                ReaderScreen(viewModel)
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

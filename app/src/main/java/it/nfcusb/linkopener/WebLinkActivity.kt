package it.nfcusb.linkopener

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import it.nfcusb.linkopener.parse.CheckResult
import it.nfcusb.linkopener.parse.DomainPolicy
import it.nfcusb.linkopener.parse.ReadingCheck
import it.nfcusb.linkopener.parse.UrlLineParser
import it.nfcusb.linkopener.ui.AppTheme

class WebLinkActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runtime = (application as NfcUsbLinkApp).runtime
        val readerSettings = runtime.settings.value
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val policy = DomainPolicy(
            enabled = readerSettings.whitelistEnabled,
            domains = UrlLineParser.normalizeDomains(readerSettings.allowedDomainsText),
            openUnlisted = readerSettings.openUnlisted,
        )
        val accepted = ReadingCheck.check(url, policy, readerSettings.codePattern) as? CheckResult.Accepted
        if (accepted == null) {
            Toast.makeText(this, R.string.error_link_blocked, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val activity = this
        setContent {
            AppTheme {
                val webView = remember {
                    WebView(activity).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        loadUrl(accepted.url)
                    }
                }
                BackHandler(enabled = true) {
                    if (webView.canGoBack()) webView.goBack() else activity.finish()
                }
                Scaffold(
                    topBar = { TopAppBar(title = { Text(stringResource(R.string.internal_browser)) }) },
                ) { padding ->
                    AndroidView(
                        factory = { webView },
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize(),
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}

package it.nfcusb.linkopener

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import it.nfcusb.linkopener.parse.ParsedLine
import it.nfcusb.linkopener.parse.UrlLineParser

/**
 * Si apre dal tocco sulla notifica. Il gesto dell'utente rende valida l'apertura del browser.
 */
class OpenLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val domains = UrlLineParser.normalizeDomains(
            (application as NfcUsbLinkApp).runtime.settings.value.allowedDomainsText,
        )
        when (val parsed = UrlLineParser.parse("URL:$url", domains)) {
            is ParsedLine.Valid -> openBrowser(parsed.url)
            else -> Toast.makeText(this, R.string.error_link_blocked, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun openBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_browser, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}

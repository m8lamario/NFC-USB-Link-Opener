package it.nfcusb.linkopener

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import it.nfcusb.linkopener.parse.CheckResult
import it.nfcusb.linkopener.parse.DomainPolicy
import it.nfcusb.linkopener.parse.ReadingCheck
import it.nfcusb.linkopener.parse.UrlLineParser

/**
 * Si apre dal tocco sulla notifica. Il gesto dell'utente rende valida l'apertura del browser.
 */
class OpenLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runtime = (application as NfcUsbLinkApp).runtime
        val settings = runtime.settings.value
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val policy = DomainPolicy(
            enabled = settings.whitelistEnabled,
            domains = UrlLineParser.normalizeDomains(settings.allowedDomainsText),
            openUnlisted = settings.openUnlisted,
        )
        when (val check = ReadingCheck.check(url, policy, settings.codePattern)) {
            is CheckResult.Accepted -> open(check.url, settings.internalBrowser)
            is CheckResult.Rejected -> Toast.makeText(this, R.string.error_link_blocked, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun open(url: String, internalBrowser: Boolean) {
        try {
            if (internalBrowser) {
                startActivity(
                    Intent(this, WebLinkActivity::class.java).putExtra(WebLinkActivity.EXTRA_URL, url),
                )
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_browser, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}

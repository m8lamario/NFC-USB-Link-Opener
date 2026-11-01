package it.nfcusb.linkopener.session

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import it.nfcusb.linkopener.WebLinkActivity
import it.nfcusb.linkopener.service.ReaderNotifier

enum class OpenOutcome {
    Opened,
    Notified,
}

class LinkOpener(
    private val context: Context,
    private val notifier: ReaderNotifier,
) {
    fun open(url: String, internalBrowser: Boolean): OpenOutcome {
        val foreground = ProcessLifecycleOwner.get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)
        if (foreground && launch(url, internalBrowser)) {
            return OpenOutcome.Opened
        }
        if (!foreground) {
            launch(url, internalBrowser)
        }
        notifier.showLink(url)
        return OpenOutcome.Notified
    }

    private fun launch(url: String, internalBrowser: Boolean): Boolean {
        val intent = if (internalBrowser) {
            Intent(context, WebLinkActivity::class.java)
                .putExtra(WebLinkActivity.EXTRA_URL, url)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }
}

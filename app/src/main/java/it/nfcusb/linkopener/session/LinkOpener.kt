package it.nfcusb.linkopener.session

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import it.nfcusb.linkopener.service.ReaderNotifier

enum class OpenOutcome {
    Opened,
    Notified,
}

class LinkOpener(
    private val context: Context,
    private val notifier: ReaderNotifier,
) {
    fun open(url: String): OpenOutcome {
        val foreground = ProcessLifecycleOwner.get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)
        if (foreground && launch(url)) {
            return OpenOutcome.Opened
        }
        // Dal background startActivity spesso torna senza eccezione e viene comunque bloccato.
        if (!foreground) {
            launch(url)
        }
        notifier.showLink(url)
        return OpenOutcome.Notified
    }

    private fun launch(url: String): Boolean {
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }
}

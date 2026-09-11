package com.mtga.app.core.link

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mtga.app.MainActivity
import com.mtga.app.data.instances.InstancePool
import com.mtga.app.data.twstalker.TwstalkerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries a link from outside the app, or from a tap inside it, to the
 * screen that shows it.
 *
 * The activity cannot navigate by itself, the navigation graph lives in
 * Compose. So it drops the link here, and the app consumes it once.
 */
class LinkRouter(private val pool: InstancePool) {

    private val _pending = MutableStateFlow<XLink?>(null)
    val pending: StateFlow<XLink?> = _pending.asStateFlow()

    /** Returns false when the intent holds no link MTGA can show. */
    fun offer(intent: Intent?): Boolean {
        val url = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(XLink::firstUrlIn)
            else -> null
        } ?: return false
        val link = parse(url) ?: return false
        _pending.value = link
        return true
    }

    fun consume() {
        _pending.value = null
    }

    /**
     * x.com and twitter.com, plus the Nitter servers in the pool and
     * twstalker, whose post and profile paths are the same. A mention inside
     * a Nitter page links to the server it came from, and should open here.
     */
    fun parse(url: String): XLink? =
        XLink.parse(url, pool.instances.value.map { it.host.lowercase() }.toSet() + TwstalkerSource.HOST)

    companion object {
        /**
         * Opens [url] anywhere but MTGA. "Open on X" must reach X or a browser,
         * and once X links open in MTGA by default, a plain view intent would
         * come straight back here.
         */
        fun openOutside(context: Context, url: String) {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            val chooser = Intent.createChooser(view, null).apply {
                putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(ComponentName(context, MainActivity::class.java))
                )
            }
            // No browser at all leaves nothing sensible to do.
            runCatching { context.startActivity(chooser) }
        }
    }
}

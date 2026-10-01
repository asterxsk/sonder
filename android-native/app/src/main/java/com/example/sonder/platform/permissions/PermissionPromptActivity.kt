package com.example.sonder.platform.permissions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.example.sonder.data.settings.SettingsRepository
import com.example.sonder.theme.SonderTheme
import com.example.sonder.theme.enablePixelEdgeToEdge
import com.example.sonder.ui.permissions.PermissionPromptContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Pixel-styled popup shown when the on-open audit finds a missing permission. One button per
 * missing permission, deep-linked to the right settings page.
 *
 * The view is rebuilt from a fresh audit on every resume rather than once in [onCreate]. It
 * used to be built once, which meant that after the user went to Settings and granted the
 * thing, they came back to a dialog still listing it as missing — the reason it looked like
 * each permission had to be granted twice. When the last one lands, the dialog removes itself.
 *
 * The auditing and the drawing are kept apart: this class owns the state (what is outstanding,
 * and the two ways out), and [PermissionPromptContent] owns the look. It used to build the
 * whole card out of platform [android.widget.Button]s and [android.widget.TextView]s, which
 * put a stock Material dialog in front of a user whose whole app is amber-on-brown-black hard
 * frames — the one screen in Sonder that did not look like Sonder. Everything visible now
 * comes from the same kit as the rest of the app.
 */
@AndroidEntryPoint
class PermissionPromptActivity : ComponentActivity() {

    @Inject lateinit var audit: PermissionAudit
    @Inject lateinit var settings: SettingsRepository

    /**
     * What the card is currently listing. Null until the first audit answers, which is why the
     * composable draws nothing for that frame: the card must not appear listing permissions
     * that the read is about to say are all granted.
     */
    private val outstanding = mutableStateOf<Set<SonderPermission>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enablePixelEdgeToEdge()
        setContent {
            SonderTheme {
                PermissionPromptContent(
                    missing = outstanding.value.orEmpty(),
                    // Grant it and the watcher brings Sonder forward, re-auditing first.
                    // Landing back on this card is right while it still has something to
                    // list — closing on the first grant dropped every later one until the
                    // next cold start, because MainActivity audits once, in onCreate.
                    onGrant = { permission ->
                        PermissionHandoff.request(this, permission) { refresh() }
                    },
                    onLater = {
                        PermissionHandoff.cancel()
                        finish()
                    },
                )
            }
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        // Back in charge again, so the watcher's job is done either way — without this
        // a user who returned by hand could be pulled forward again seconds later.
        PermissionHandoff.cancel()
        refresh()
    }

    override fun onDestroy() {
        PermissionHandoff.cancel()
        super.onDestroy()
    }

    /**
     * Read the declined-optional set, then redraw. The audit itself is synchronous, but the
     * skip list is DataStore-backed, so the frame waits on one preferences read — that read
     * is what keeps a deliberately-skipped optional permission from being nagged about on
     * every launch, which is the whole point of it being optional.
     *
     * This is also the watcher's landing: an empty set finishes the activity rather than
     * leaving an empty card up, which is what makes a grant that satisfies the last
     * outstanding permission take the dialog down on its own.
     */
    private fun refresh() {
        lifecycleScope.launch {
            val missing = outstandingPermissions(
                audit.missingPermissions(),
                settings.skippedPermissionNames.first(),
            )
            if (missing.isEmpty()) {
                finish()
            } else {
                outstanding.value = missing
            }
        }
    }

    companion object {
        /**
         * Shows the prompt only when [missing] still holds something worth asking about.
         *
         * Takes the set rather than the audit so the caller applies the optional-skip rule
         * first: launching a translucent activity that instantly finishes itself is a
         * flicker on the user's screen for a permission they already declined.
         */
        fun launchIfMissing(context: Context, missing: Set<SonderPermission>) {
            if (missing.isEmpty()) return
            runCatching {
                context.startActivity(
                    Intent(context, PermissionPromptActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

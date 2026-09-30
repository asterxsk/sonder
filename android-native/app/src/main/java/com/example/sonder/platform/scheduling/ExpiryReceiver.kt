package com.example.sonder.platform.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.platform.notifications.Notifications
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Fires at grant end: revoke the grant and post the "time's up" toast. */
@AndroidEntryPoint
class ExpiryReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: EnforcementRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val endAtMillis = intent.getLongExtra(EXTRA_END_AT, 0L)
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Only the grant this alarm was actually set for. An alarm left over from a
                // grant that has since been replaced must not cut the newer one short, so
                // the decision is left to the repository, which can compare against what is
                // stored now. Nothing is announced unless something was really revoked.
                if (repository.revokeGrantIfExpiredAt(pkg, endAtMillis)) {
                    Notifications.notifyAccessExpired(context, pkg)
                }
            } catch (failure: Throwable) {
                // A receiver has no caller to throw to: an escaping exception here takes the
                // process down during a boot or an alarm, which is the worst place for it.
                Log.w(TAG, "grant expiry handling failed for $pkg", failure)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SonderGate"
        const val EXTRA_PACKAGE = "extra_package"

        /** The end time the alarm was set for, so a stale alarm can be recognised. */
        const val EXTRA_END_AT = "extra_end_at"
    }
}

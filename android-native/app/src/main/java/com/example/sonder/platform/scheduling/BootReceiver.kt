package com.example.sonder.platform.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.sonder.data.repo.EnforcementRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * After a reboot — or an update, which replaces the app without cancelling its alarms —
 * purge anything that expired while the device was off and re-arm the expiry alarms that
 * are still due.
 *
 * Timers themselves stay correct across a reboot, because they are absolute epoch millis;
 * what does not survive is the *alarm*, and without re-arming it a grant still running
 * would never announce its own expiry, nor be swept when it lapses.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: EnforcementRepository
    @Inject lateinit var expiryScheduler: GrantExpiryScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.purgeExpired()
                repository.liveGrants().forEach { (pkg, endAtMillis) ->
                    expiryScheduler.scheduleExpiry(pkg, endAtMillis)
                }
            } catch (failure: Throwable) {
                // A receiver has no caller to throw to: an escaping exception here takes the
                // process down on every boot.
                Log.w(TAG, "post-boot sweep failed", failure)
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SonderGate"
    }
}

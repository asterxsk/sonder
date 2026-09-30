package com.example.sonder.platform.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.sonder.data.repo.EnforcementRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * After a reboot — or an update — warm the enforcement cache.
 *
 * This used to purge expired grants and re-arm their expiry alarms, because a grant was an
 * absolute wall-clock deadline that nobody would otherwise sweep. There are no alarms left:
 * a bank only moves while the app it belongs to is in front, and the drain that moves it is
 * driven by the accessibility service's own heartbeat. All that survives a reboot is the
 * cache, which starts cold and has to answer before the first foreground event is decided.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: EnforcementRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        try {
            repository.start()
        } catch (failure: Throwable) {
            // A receiver has no caller to throw to: an escaping exception here takes the
            // process down on every boot. A cold cache is survivable — the next foreground
            // event warms it — so the failure is logged and dropped.
            Log.w(TAG, "cache warm after boot failed", failure)
        }
    }

    private companion object {
        const val TAG = "SonderGate"
    }
}

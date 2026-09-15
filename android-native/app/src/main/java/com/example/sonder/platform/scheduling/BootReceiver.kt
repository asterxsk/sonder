package com.example.sonder.platform.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.sonder.data.repo.EnforcementRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * After reboot, timers stay correct (absolute epoch millis); this just purges
 * anything that expired while the device was off.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: EnforcementRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.purgeExpired()
            } finally {
                result.finish()
            }
        }
    }
}

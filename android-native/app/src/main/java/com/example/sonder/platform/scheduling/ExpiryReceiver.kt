package com.example.sonder.platform.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.revokeGrant(pkg, reason = "expired")
                Notifications.notifyAccessExpired(context, pkg)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }
}

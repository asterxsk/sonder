package com.example.sonder

import android.app.Application
import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.platform.notifications.Notifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SonderApp : Application() {

    @Inject lateinit var enforcementRepository: EnforcementRepository

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        // Warm the in-memory enforcement snapshot (targets/grants/lockouts) so the
        // accessibility hot path never waits on Room before showing the block.
        enforcementRepository.start()
    }
}

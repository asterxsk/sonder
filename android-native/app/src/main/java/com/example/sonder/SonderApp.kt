package com.example.sonder

import android.app.Application
import com.example.sonder.platform.notifications.Notifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SonderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }
}

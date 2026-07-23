package com.ethred.panorama

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EthredApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize logging, background workers, or crash tracking if enabled
    }
}

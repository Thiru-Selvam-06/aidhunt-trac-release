package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class TracApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEVICE_TEST || BuildConfig.LIVE_TEST || BuildConfig.PHYSICAL_TEST) {
            val demoApp = FirebaseApp.getInstance()
            com.example.data.firebase.FirebaseEnvironment.initialize(demoApp)
            com.example.data.sync.WorkspaceSyncScheduler.start(this)
            return
        }
        try {
            val app = if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            } else {
                FirebaseApp.getInstance()
            }
            val opts = app?.options
            Log.i("TRAC_FIREBASE_VERIFY", "=== RUNTIME FIREBASE VERIFICATION ===")
            Log.i("TRAC_FIREBASE_VERIFY", "Package Name: $packageName")
            Log.i("TRAC_FIREBASE_VERIFY", "Firebase Application ID (App ID): ${opts?.applicationId}")
            Log.i("TRAC_FIREBASE_VERIFY", "Firebase Project ID: ${opts?.projectId}")
            Log.i("TRAC_FIREBASE_VERIFY", "Firebase GCM Sender ID (Project Number): ${opts?.gcmSenderId}")
            Log.i("TRAC_FIREBASE_VERIFY", "Firebase Storage Bucket: ${opts?.storageBucket}")
            Log.i("TRAC_FIREBASE_VERIFY", "========================================")
            com.example.data.sync.WorkspaceSyncScheduler.start(this)
        } catch (e: Exception) {
            Log.e("TRAC_FIREBASE_VERIFY", "Error initializing/verifying FirebaseApp: ${e.message}", e)
        }
    }
}

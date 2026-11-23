package com.example

import android.app.Application
import android.app.ActivityManager
import android.os.Process
import com.google.firebase.FirebaseApp

/** Keeps normal demo startup in the test host; isolates the synthetic cash peer from WorkManager. */
class CashTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        check(BuildConfig.DEVICE_TEST && packageName.endsWith(".devicetest"))
        val app=FirebaseApp.initializeApp(this) ?: error("Demo Firebase configuration required")
        check(app.options.projectId=="demo-aidhunt-v16")
        val processName=(getSystemService(ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
            ?.firstOrNull { it.pid==Process.myPid() }?.processName.orEmpty()
        check(processName.isNotBlank())
        if(!processName.endsWith(":cash_test_peer")) {
            // Same three calls as TracApplication's existing DEVICE_TEST branch.
            com.example.data.firebase.FirebaseEnvironment.initialize(app)
            com.example.data.firebase.OwnerActivityPush.observeAuthentication(this)
            com.example.data.sync.WorkspaceSyncScheduler.start(this)
        }
    }
}

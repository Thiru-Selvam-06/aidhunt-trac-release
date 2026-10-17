package com.example.data.firebase

import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Demo builds have no production endpoints or fallback; USB reverse supplies loopback. */
object FirebaseEnvironment {
    const val DEMO_PROJECT = "demo-aidhunt-v16"
    private const val LIVE_PROJECT = "trac-aidhunt"
    const val STAGING_WORKSPACE = "ws_cf_staging_20261015_login_a62e71"
    private const val STAGING_WORKER = "https://aidhunt-trac-trusted-staging.aidhuntinformatics.workers.dev"

    val AUTHORIZED_STAGING_WORKSPACES = setOf(
        "ws_cf_staging_beb841c2f4d9475a",
        "ws_cf_staging_20261015_7361_7f4c92",
        "ws_cf_staging_20261015_login_a62e71"
    )

    fun isStagingWorkspace(workspaceId: String?): Boolean =
        !workspaceId.isNullOrBlank() &&
            workspaceId.startsWith("ws_cf_staging_") &&
            workspaceId in AUTHORIZED_STAGING_WORKSPACES

    fun initialize(app: FirebaseApp) {
        if (BuildConfig.PHYSICAL_TEST) {
            check(BuildConfig.APPLICATION_ID.endsWith(".physicaltest") && app.options.projectId == LIVE_PROJECT) {
                "Physical test project isolation failed"
            }
            android.util.Log.i("TRAC_PHYSICAL_TEST", "Project=$LIVE_PROJECT package=${BuildConfig.APPLICATION_ID} DB=aidhunt_physical_test_v18.db workspace=$STAGING_WORKSPACE")
            return
        }
        if (BuildConfig.LIVE_TEST) {
            check(BuildConfig.APPLICATION_ID.endsWith(".livetest") && app.options.projectId == "trac-aidhunt") { "Live test project isolation failed" }
            java.io.File(app.applicationContext.filesDir, "live-test-environment.json").writeText(
                org.json.JSONObject().put("pid", android.os.Process.myPid()).put("projectId", app.options.projectId)
                    .put("packageName", app.applicationContext.packageName).toString()
            )
            android.util.Log.i("TRAC_LIVE_TEST", "trac-aidhunt DB=aidhunt_live_test_v18.db package=${BuildConfig.APPLICATION_ID}")
            return
        }
        if (!BuildConfig.DEVICE_TEST) return
        check(BuildConfig.APPLICATION_ID.endsWith(".devicetest") && app.options.projectId == DEMO_PROJECT) { "Device test isolation failed" }
        FirebaseAuth.getInstance(app).useEmulator("127.0.0.1", 19099)
        FirebaseFirestore.getInstance(app).useEmulator("127.0.0.1", 18180)
        java.io.File(app.applicationContext.filesDir, "device-test-environment.json").writeText(
            org.json.JSONObject().put("pid", android.os.Process.myPid()).put("projectId", app.options.projectId)
                .put("packageName", app.applicationContext.packageName).toString()
        )
        android.util.Log.i("TRAC_DEVICE_TEST", "demo-aidhunt-v16 Auth=127.0.0.1:19099 Firestore=127.0.0.1:18180 Functions=127.0.0.1:15001 DB=aidhunt_device_test_v18.db")
    }
    fun commandUrl(project: String, name: String, workspaceId: String? = null): String = resolveCommandUrl(
        project, name, workspaceId, BuildConfig.DEVICE_TEST, BuildConfig.LIVE_TEST, BuildConfig.PHYSICAL_TEST)

    internal fun resolveCommandUrl(project: String, name: String, workspaceId: String?,
        deviceTest: Boolean, liveTest: Boolean, physicalTest: Boolean = false): String {
        require(name.matches(Regex("[A-Za-z0-9]+")))
        if (deviceTest) {
            check(project == DEMO_PROJECT) { "Device test project mismatch" }
            return "http://127.0.0.1:15001/$DEMO_PROJECT/us-central1/$name"
        }
        if (liveTest) {
            check(project == LIVE_PROJECT) { "Live test project mismatch" }
            if (isStagingWorkspace(workspaceId)) return "$STAGING_WORKER/$name"
        }
        if (physicalTest) {
            check(project == LIVE_PROJECT && isStagingWorkspace(workspaceId)) { "Physical test requires an isolated staging workspace" }
            return "$STAGING_WORKER/$name"
        }
        return "https://us-central1-$project.cloudfunctions.net/$name"
    }
}

package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.firebase.FirebaseEnvironment
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneRegistrationDeviceTest {
    @Test(timeout = 90000) fun verifiedPhoneCanCreateFirstWorkspaceWithoutWritingPrivateDirectory() {
        check(BuildConfig.DEVICE_TEST && FirebaseApp.getInstance().options.projectId == FirebaseEnvironment.DEMO_PROJECT)
        val user = FirebaseAuth.getInstance().currentUser ?: error("Verify disposable phone OTP first")
        check(user.phoneNumber in setOf("+919000001111", "+919000001112", "+919000001113") && !user.isAnonymous)
        val cloud = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        check(!com.google.android.gms.tasks.Tasks.await(cloud.document("users/${user.uid}").get(com.google.firebase.firestore.Source.SERVER), 10, java.util.concurrent.TimeUnit.SECONDS).exists()) { "Fresh verified fixture identity required" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val created = java.util.concurrent.CountDownLatch(1)
        val complete = java.util.concurrent.CountDownLatch(1)
        val error = java.util.concurrent.atomic.AtomicReference<String?>()
        val result = java.util.concurrent.atomic.AtomicReference<com.example.data.firebase.UserProfile?>()
        lateinit var model: MainViewModel
        main.post { model = MainViewModel(context.applicationContext as android.app.Application); created.countDown() }
        assertTrue(created.await(10, java.util.concurrent.TimeUnit.SECONDS))
        main.post { model.createAccountForPhoneUser("Phone Audit Owner", "Phone Audit Workspace", onSuccess = { result.set(it); complete.countDown() }, onError = { error.set(it); complete.countDown() }) }
        try {
            assertTrue("Registration callback must finish", complete.await(30, java.util.concurrent.TimeUnit.SECONDS))
            assertNull("Verified registration must not fail on a forbidden directory write", error.get())
            assertEquals(user.uid, result.get()!!.uid)
            val ws = result.get()!!.defaultWorkspaceId!!
            val serverWorkspace = com.google.android.gms.tasks.Tasks.await(cloud.document("workspaces/$ws").get(com.google.firebase.firestore.Source.SERVER), 10, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(user.uid, serverWorkspace.getString("ownerUid"))
            val member = com.google.android.gms.tasks.Tasks.await(cloud.document("workspaces/$ws/members/${user.uid}").get(com.google.firebase.firestore.Source.SERVER), 10, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(com.example.data.auth.RoleUtils.ROLE_OWNER, com.example.data.auth.RoleUtils.normalizeRole(member.getString("role")))
            assertFalse(com.google.android.gms.tasks.Tasks.await(cloud.document("phoneDirectory/${user.phoneNumber}").get(com.google.firebase.firestore.Source.SERVER), 10, java.util.concurrent.TimeUnit.SECONDS).exists())
        } finally {
            main.post { androidx.lifecycle.ViewModelStore().apply { put("phone-registration", model); clear() } }
        }
    }
}

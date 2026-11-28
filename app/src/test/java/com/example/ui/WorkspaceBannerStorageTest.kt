package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.ui.util.WorkspaceBannerStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
class WorkspaceBannerStorageTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clean up banner dir before each test
        File(context.noBackupFilesDir, "workspace_banners").deleteRecursively()
    }

    @Test
    fun loadBannerReturnsNullWhenNoBannerExists() {
        assertNull(WorkspaceBannerStorage.loadBanner(context, "workspace_123"))
        assertNull(WorkspaceBannerStorage.loadBanner(context, ""))
    }

    @Test
    fun saveAndLoadBannerSucceeds() {
        val testFile = File(context.cacheDir, "sample_banner.jpg").apply {
            FileOutputStream(this).use { it.write("test_image_bytes".toByteArray()) }
        }
        val uri = Uri.fromFile(testFile)

        val saved = WorkspaceBannerStorage.saveBanner(context, "ws_alpha", uri)
        assertNotNull(saved)
        assertTrue(saved!!.exists())
        assertEquals("test_image_bytes", saved.readText())

        val loaded = WorkspaceBannerStorage.loadBanner(context, "ws_alpha")
        assertNotNull(loaded)
        assertEquals(saved.absolutePath, loaded!!.absolutePath)
        assertEquals("test_image_bytes", loaded.readText())
    }

    @Test
    fun workspaceIsolationIsMaintained() {
        val testFile1 = File(context.cacheDir, "img1.jpg").apply {
            FileOutputStream(this).use { it.write("ws1_data".toByteArray()) }
        }
        val testFile2 = File(context.cacheDir, "img2.jpg").apply {
            FileOutputStream(this).use { it.write("ws2_data".toByteArray()) }
        }

        WorkspaceBannerStorage.saveBanner(context, "ws_1", Uri.fromFile(testFile1))
        WorkspaceBannerStorage.saveBanner(context, "ws_2", Uri.fromFile(testFile2))

        val loaded1 = WorkspaceBannerStorage.loadBanner(context, "ws_1")
        val loaded2 = WorkspaceBannerStorage.loadBanner(context, "ws_2")

        assertNotNull(loaded1)
        assertNotNull(loaded2)
        assertNotEquals(loaded1!!.absolutePath, loaded2!!.absolutePath)
        assertEquals("ws1_data", loaded1.readText())
        assertEquals("ws2_data", loaded2.readText())
    }

    @Test
    fun removeBannerDeletesFileAndRestoresDefault() {
        val testFile = File(context.cacheDir, "img_to_remove.jpg").apply {
            FileOutputStream(this).use { it.write("banner_bytes".toByteArray()) }
        }
        WorkspaceBannerStorage.saveBanner(context, "ws_del", Uri.fromFile(testFile))
        assertNotNull(WorkspaceBannerStorage.loadBanner(context, "ws_del"))

        WorkspaceBannerStorage.removeBanner(context, "ws_del")
        assertNull(WorkspaceBannerStorage.loadBanner(context, "ws_del"))
    }

    @Test
    fun bannerIsStoredInNoBackupFilesDir() {
        val testFile = File(context.cacheDir, "test_nobackup.jpg").apply {
            FileOutputStream(this).use { it.write("data".toByteArray()) }
        }
        val saved = WorkspaceBannerStorage.saveBanner(context, "ws_nobackup", Uri.fromFile(testFile))
        assertNotNull(saved)
        assertTrue(saved!!.absolutePath.startsWith(context.noBackupFilesDir.absolutePath))
    }
}

package com.example.ui.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Local-only storage for workspace banner images.
 *
 * Images are stored under [Context.noBackupFilesDir]/workspace_banners/
 * so they are excluded from Android Auto Backup / cloud backup.
 *
 * No data is ever uploaded to Firebase, Firestore, Firebase Storage,
 * Cloudflare, or any remote service.
 */
object WorkspaceBannerStorage {

    private const val DIR_NAME = "workspace_banners"

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Save a banner image for the given workspace.
     *
     * Copies the image bytes from [uri] into app-private storage using an
     * atomic write (temp file + rename) so a crash during write never leaves
     * a corrupt file.
     *
     * @return the saved [File], or null if the write failed.
     */
    fun saveBanner(context: Context, workspaceId: String, uri: Uri): File? {
        if (workspaceId.isBlank()) return null
        return try {
            val dir = bannerDir(context)
            val target = bannerFile(dir, workspaceId)
            val tmp = File(dir, "${safeId(workspaceId)}.tmp")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            // Atomic replace
            if (target.exists()) target.delete()
            tmp.renameTo(target)
            target
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Load the banner file for the given workspace.
     *
     * @return a [File] pointing to the saved image, or null if none exists.
     */
    fun loadBanner(context: Context, workspaceId: String): File? {
        if (workspaceId.isBlank()) return null
        val file = bannerFile(bannerDir(context), workspaceId)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Delete the banner for the given workspace, restoring the default
     * green card background.
     */
    fun removeBanner(context: Context, workspaceId: String) {
        if (workspaceId.isBlank()) return
        try {
            bannerFile(bannerDir(context), workspaceId).delete()
        } catch (_: Exception) {}
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun bannerDir(context: Context): File =
        File(context.noBackupFilesDir, DIR_NAME).also { it.mkdirs() }

    private fun bannerFile(dir: File, workspaceId: String): File =
        File(dir, "${safeId(workspaceId)}.jpg")

    /** Converts a workspace ID into a safe filename component. */
    private fun safeId(workspaceId: String): String =
        workspaceId.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(64)
}

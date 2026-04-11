package com.securecall.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Wraps Android's [DownloadManager] to download an update APK to a predictable
 * location under the app's external files dir:
 *
 *   {ExternalFilesDir}/update/securecall-update.apk
 *
 * Using the app's own external files dir means:
 *   - No storage permission needed
 *   - File is removed when the app is uninstalled
 *   - Accessible via FileProvider for the install intent (see [UpdateInstaller])
 *
 * Callers receive a completion callback via [onComplete] with the downloaded
 * [File] on success, or `null` on failure.
 */
object UpdateDownloader {

    private const val TAG = "UpdateDownloader"
    private const val SUBDIR = "update"
    private const val FILENAME = "securecall-update.apk"

    @Volatile
    private var currentId: Long = -1L

    @Volatile
    private var receiver: BroadcastReceiver? = null

    /**
     * Kick off the download for [apkUrl]. Deletes any previous update file and
     * re-registers a completion receiver.
     *
     * [onProgress] is called immediately with the enqueued download id (-1 on
     * failure) so the caller can show progress or a cancel UI.
     * [onComplete] is called exactly once with the downloaded file, or null on
     * failure / cancellation.
     */
    fun download(
        context: Context,
        apkUrl: String,
        onProgress: (Long) -> Unit,
        onComplete: (File?) -> Unit,
    ) {
        try {
            val targetFile = targetFile(context)
            targetFile.parentFile?.mkdirs()
            if (targetFile.exists()) targetFile.delete()

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("SecureCall Update")
                .setDescription("Downloading new version…")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "$SUBDIR/$FILENAME")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setMimeType("application/vnd.android.package-archive")

            val id = dm.enqueue(request)
            currentId = id
            Log.d(TAG, "Enqueued download id=$id url=$apkUrl")
            onProgress(id)

            // Register a one-shot receiver for DOWNLOAD_COMPLETE
            unregister(context)
            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (finishedId != id) return
                    val (ok, localFile) = queryStatus(dm, id)
                    Log.d(TAG, "Download id=$id finished ok=$ok file=$localFile")
                    try {
                        ctx.unregisterReceiver(this)
                    } catch (_: Throwable) {
                    }
                    receiver = null
                    onComplete(if (ok && localFile != null && localFile.exists()) localFile else null)
                }
            }
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(
                    receiver,
                    filter,
                    Context.RECEIVER_EXPORTED,
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.applicationContext.registerReceiver(receiver, filter)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "download() failed: ${t.message}", t)
            onProgress(-1L)
            onComplete(null)
        }
    }

    /**
     * Query the DownloadManager for [id] and return (success, localFile).
     */
    private fun queryStatus(dm: DownloadManager, id: Long): Pair<Boolean, File?> {
        val query = DownloadManager.Query().setFilterById(id)
        var cursor: Cursor? = null
        try {
            cursor = dm.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                val localUri = if (uriIdx >= 0) cursor.getString(uriIdx) else null
                if (status == DownloadManager.STATUS_SUCCESSFUL && !localUri.isNullOrEmpty()) {
                    val file = File(Uri.parse(localUri).path ?: return false to null)
                    return true to file
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "queryStatus failed: ${t.message}")
        } finally {
            cursor?.close()
        }
        return false to null
    }

    /**
     * Cancel any active download and clear the cached APK.
     */
    fun cancel(context: Context) {
        try {
            if (currentId > 0) {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.remove(currentId)
                currentId = -1L
            }
            unregister(context)
            targetFile(context).delete()
        } catch (t: Throwable) {
            Log.w(TAG, "cancel failed: ${t.message}")
        }
    }

    private fun unregister(context: Context) {
        val r = receiver ?: return
        try {
            context.applicationContext.unregisterReceiver(r)
        } catch (_: Throwable) {
        }
        receiver = null
    }

    /** Path where the update APK will be stored. */
    fun targetFile(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir
        return File(File(base, SUBDIR), FILENAME)
    }
}

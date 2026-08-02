package com.starkbrowser.app.browser

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.starkbrowser.app.data.AppDatabase
import com.starkbrowser.app.data.DownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query) ?: return

        if (cursor.moveToFirst()) {
            val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val localUriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val sizeCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val status = if (statusCol >= 0) cursor.getInt(statusCol) else DownloadManager.STATUS_FAILED
            val localUri = if (localUriCol >= 0) cursor.getString(localUriCol) ?: "" else ""
            val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L

            val dbStatus = when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadItem.STATUS_COMPLETE
                DownloadManager.STATUS_FAILED -> DownloadItem.STATUS_FAILED
                else -> DownloadItem.STATUS_RUNNING
            }

            val db = AppDatabase.getInstance(context)
            CoroutineScope(Dispatchers.IO).launch {
                db.downloadDao().updateStatus(downloadId, dbStatus, localUri, size)
            }
        }
        cursor.close()
    }
}

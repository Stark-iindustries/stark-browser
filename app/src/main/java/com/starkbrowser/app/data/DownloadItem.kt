package com.starkbrowser.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filename: String,
    val url: String,
    val mimeType: String = "",
    val filePath: String = "",
    val fileSize: Long = 0L,
    val downloadId: Long = -1L,
    val status: Int = STATUS_PENDING,
    val downloadedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_RUNNING = 1
        const val STATUS_COMPLETE = 2
        const val STATUS_FAILED = 3
    }
}

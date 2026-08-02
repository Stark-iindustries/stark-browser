package com.starkbrowser.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getAllFlow(): Flow<List<DownloadItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadItem): Long

    @Delete
    suspend fun delete(item: DownloadItem)

    @Query("UPDATE downloads SET status = :status, filePath = :filePath, fileSize = :fileSize WHERE downloadId = :downloadId")
    suspend fun updateStatus(downloadId: Long, status: Int, filePath: String, fileSize: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)
}

package com.helm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Aggregated foreground time per app for a single day, ordered by time desc. */
data class AppDayTotal(val packageName: String, val appLabel: String, val totalMillis: Long, val launchCount: Int)

@Dao
interface HelmDao {

    // --- Usage ---
    // INSERT OR REPLACE so re-collecting a day overwrites via the (day, packageName) unique index.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsage(records: List<UsageRecord>)

    @Query("SELECT packageName, appLabel, totalMillis, launchCount FROM usage_records WHERE day = :day ORDER BY totalMillis DESC")
    fun usageForDay(day: String): Flow<List<AppDayTotal>>

    @Query("SELECT packageName, appLabel, SUM(totalMillis) AS totalMillis, SUM(launchCount) AS launchCount FROM usage_records WHERE day BETWEEN :from AND :to GROUP BY packageName ORDER BY totalMillis DESC")
    fun usageBetween(from: String, to: String): Flow<List<AppDayTotal>>

    @Query("SELECT IFNULL(SUM(totalMillis), 0) FROM usage_records WHERE day = :day")
    fun totalMillisForDay(day: String): Flow<Long>

    @Query("SELECT IFNULL(SUM(totalMillis), 0) FROM usage_records WHERE day = :day AND packageName = :pkg")
    suspend fun millisForApp(day: String, pkg: String): Long

    @Query("SELECT day, IFNULL(SUM(totalMillis),0) AS totalMillis FROM usage_records WHERE day BETWEEN :from AND :to GROUP BY day ORDER BY day")
    fun dailyTotals(from: String, to: String): Flow<List<DayTotal>>

    // --- Manual logs ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entry: LogEntry): Long

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("DELETE FROM log_entries WHERE note = :note AND timestamp BETWEEN :from AND :to")
    suspend fun deleteLogsByNote(note: String, from: Long, to: Long)

    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC LIMIT :limit")
    fun recentLogs(limit: Int = 100): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE kind IN (:kinds) ORDER BY timestamp DESC LIMIT :limit")
    fun logsOfKinds(kinds: List<LogKind>, limit: Int = 200): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    suspend fun logsBetween(from: Long, to: Long): List<LogEntry>

    // --- Limits & focus ---
    @Upsert
    suspend fun upsertLimit(limit: AppLimit)

    @Query("DELETE FROM app_limits WHERE packageName = :pkg")
    suspend fun deleteLimit(pkg: String)

    @Query("SELECT * FROM app_limits ORDER BY appLabel")
    fun limits(): Flow<List<AppLimit>>

    @Query("SELECT * FROM app_limits WHERE enabled = 1")
    suspend fun activeLimits(): List<AppLimit>

    @Upsert
    suspend fun upsertFocus(window: FocusWindow)

    @Query("DELETE FROM focus_windows WHERE id = :id")
    suspend fun deleteFocus(id: Long)

    @Query("SELECT * FROM focus_windows ORDER BY startMinute")
    fun focusWindows(): Flow<List<FocusWindow>>

    @Query("SELECT * FROM focus_windows WHERE enabled = 1")
    suspend fun activeFocusWindows(): List<FocusWindow>

    // --- Dispatch ---
    @Insert
    suspend fun insertDispatch(report: DispatchReport): Long

    @Query("SELECT * FROM dispatch_reports ORDER BY createdAt DESC LIMIT :limit")
    fun dispatches(limit: Int = 30): Flow<List<DispatchReport>>
}

data class DayTotal(val day: String, val totalMillis: Long)

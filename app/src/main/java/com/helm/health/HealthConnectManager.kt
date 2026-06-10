package com.helm.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.helm.data.HelmRepository
import com.helm.data.LogEntry
import com.helm.data.LogKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads selected metrics from Health Connect and stores them as Helm logs.
 *
 * Health Connect's API surface is version-sensitive — this targets the
 * `androidx.health.connect:connect-client` version pinned in libs.versions.toml.
 */
class HealthConnectManager(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    /** SDK_AVAILABLE / SDK_UNAVAILABLE / SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED. */
    fun availability(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean = availability() == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasAllPermissions(): Boolean {
        if (!isAvailable()) return false
        val granted = client().permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    /** Pull today's metrics into Helm. Returns a short human summary. */
    suspend fun syncToday(repo: HelmRepository): String {
        if (!isAvailable()) return "Health Connect isn't available on this device."
        val c = client()
        if (!c.permissionController.getGrantedPermissions().containsAll(permissions)) {
            return "Health permissions not granted yet."
        }

        val zone = ZoneId.systemDefault()
        val startInstant = LocalDate.now().atStartOfDay(zone).toInstant()
        val endInstant = Instant.now()
        val range = TimeRangeFilter.between(startInstant, endInstant)
        val now = System.currentTimeMillis()
        val dayStartMillis = startInstant.toEpochMilli()

        // Replace any previously synced entries for today to avoid duplicates.
        repo.deleteSyncedLogs(NOTE, dayStartMillis, now)

        val entries = ArrayList<LogEntry>()

        // Steps + sleep via aggregation.
        val agg = c.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL, SleepSessionRecord.SLEEP_DURATION_TOTAL),
                timeRangeFilter = range,
            ),
        )
        agg[StepsRecord.COUNT_TOTAL]?.let { steps ->
            entries.add(LogEntry(kind = LogKind.STEPS, timestamp = now, title = "Steps", value = steps.toDouble(), unit = "steps", note = NOTE))
        }
        agg[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.let { dur ->
            val hours = dur.toMinutes() / 60.0
            if (hours > 0) entries.add(LogEntry(kind = LogKind.SLEEP, timestamp = now, title = "Sleep", value = round1(hours), unit = "h", note = NOTE))
        }

        // Latest weight today.
        val weights = c.readRecords(ReadRecordsRequest(WeightRecord::class, timeRangeFilter = range)).records
        weights.maxByOrNull { it.time }?.let { w ->
            entries.add(LogEntry(kind = LogKind.WEIGHT, timestamp = now, title = "Weight", value = round1(w.weight.inKilograms), unit = "kg", note = NOTE))
        }

        // Exercise sessions.
        val sessions = c.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, timeRangeFilter = range)).records
        for (s in sessions) {
            val minutes = (s.endTime.toEpochMilli() - s.startTime.toEpochMilli()) / 60000.0
            entries.add(
                LogEntry(
                    kind = LogKind.WORKOUT, timestamp = s.startTime.toEpochMilli(),
                    title = s.title ?: "Workout", value = round1(minutes), unit = "min",
                    category = exerciseName(s.exerciseType), note = NOTE,
                ),
            )
        }

        repo.addLogs(entries)
        return if (entries.isEmpty()) "Synced — no new health data for today." else "Synced ${entries.size} health entries from Health Connect."
    }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
    private fun exerciseName(type: Int): String = "Exercise #$type"

    companion object {
        const val NOTE = "HealthConnect"
    }
}

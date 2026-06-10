package com.helm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** A day's aggregated foreground time + launches for one app. */
@Entity(
    tableName = "usage_records",
    indices = [Index(value = ["day", "packageName"], unique = true)],
)
data class UsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,            // local date, yyyy-MM-dd
    val packageName: String,
    val appLabel: String,
    val totalMillis: Long,
    val launchCount: Int = 0,
)

/** The kind of manual entry — one flexible model covers health, money, habits, mood & notes. */
enum class LogKind(val display: String, val group: LogGroup) {
    MOOD("Mood", LogGroup.WELLBEING),
    HABIT("Habit", LogGroup.WELLBEING),
    NOTE("Note", LogGroup.WELLBEING),
    WEIGHT("Weight", LogGroup.HEALTH),
    SLEEP("Sleep", LogGroup.HEALTH),
    STEPS("Steps", LogGroup.HEALTH),
    WATER("Water", LogGroup.HEALTH),
    WORKOUT("Workout", LogGroup.HEALTH),
    EXPENSE("Expense", LogGroup.MONEY),
    INCOME("Income", LogGroup.MONEY),
    CUSTOM("Custom", LogGroup.WELLBEING),
}

enum class LogGroup { WELLBEING, HEALTH, MONEY }

/** A single manual log entry. */
@Entity(tableName = "log_entries", indices = [Index(value = ["timestamp"]), Index(value = ["kind"])])
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: LogKind,
    val timestamp: Long,        // epoch millis
    val title: String,          // e.g. "Coffee", "Morning run", habit name, mood label
    val value: Double? = null,  // amount / weight(kg) / hours / steps / mood(1-5)
    val unit: String? = null,   // "₹", "kg", "h", "steps", "ml"
    val category: String? = null,
    val note: String? = null,
)

/** A daily screen-time budget for one app. */
@Entity(tableName = "app_limits")
data class AppLimit(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean = true,
)

/** A recurring window during which chosen apps should be blocked (focus / sleep / study). */
@Entity(tableName = "focus_windows")
data class FocusWindow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startMinute: Int,             // minutes from local midnight
    val endMinute: Int,
    val daysMask: Int,                // bit 0 = Sunday … bit 6 = Saturday
    val blockedPackages: String,      // comma-separated package names
    val enabled: Boolean = true,
)

/** A generated "Dispatch" self-newsletter. */
@Entity(tableName = "dispatch_reports")
data class DispatchReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val periodLabel: String,
    val headline: String,
    val markdown: String,
)

class Converters {
    @TypeConverter fun fromKind(k: LogKind): String = k.name
    @TypeConverter fun toKind(s: String): LogKind = LogKind.valueOf(s)
}

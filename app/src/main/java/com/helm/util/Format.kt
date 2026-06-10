package com.helm.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {
    fun duration(millis: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(millis)
        val m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m"
            else -> "<1m"
        }
    }

    fun minutes(millis: Long): Long = TimeUnit.MILLISECONDS.toMinutes(millis)

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val prettyFmt = SimpleDateFormat("MMM d", Locale.US)
    private val timeFmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    fun dayKey(millis: Long): String = dayFmt.format(millis)
    fun pretty(millis: Long): String = prettyFmt.format(millis)
    fun dateTime(millis: Long): String = timeFmt.format(millis)

    /** yyyy-MM-dd for [daysAgo] days before [from] (local). */
    fun dayKeyDaysAgo(daysAgo: Int, from: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = from; add(Calendar.DAY_OF_MONTH, -daysAgo) }
        return dayFmt.format(cal.timeInMillis)
    }

    fun startOfDaysAgo(daysAgo: Int, from: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = from
            add(Calendar.DAY_OF_MONTH, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun minuteOfDay(hour: Int, minute: Int): Int = hour * 60 + minute
    fun formatMinuteOfDay(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
}

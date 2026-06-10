package com.helm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [UsageRecord::class, LogEntry::class, AppLimit::class, FocusWindow::class, DispatchReport::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class HelmDatabase : RoomDatabase() {
    abstract fun dao(): HelmDao

    companion object {
        @Volatile private var instance: HelmDatabase? = null

        fun get(context: Context): HelmDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HelmDatabase::class.java,
                "helm.db",
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}

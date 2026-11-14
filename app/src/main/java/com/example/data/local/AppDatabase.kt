package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class NovaTypeConverters {
    @TypeConverter
    fun fromSourceType(value: PlaylistSourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): PlaylistSourceType =
        runCatching { PlaylistSourceType.valueOf(value) }.getOrDefault(PlaylistSourceType.M3U_URL)

    @TypeConverter
    fun fromMediaType(value: MediaCategoryType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaCategoryType =
        runCatching { MediaCategoryType.valueOf(value) }.getOrDefault(MediaCategoryType.LIVE)
}

@Database(
    entities = [
        PlaylistProfileEntity::class,
        CategoryEntity::class,
        StreamItemEntity::class,
        SeriesEpisodeEntity::class,
        EpgProgramEntity::class,
        WatchProgressEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(NovaTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): PlaylistProfileDao
    abstract fun categoryDao(): CategoryDao
    abstract fun streamDao(): StreamItemDao
    abstract fun seriesEpisodeDao(): SeriesEpisodeDao
    abstract fun epgDao(): EpgProgramDao
    abstract fun watchProgressDao(): WatchProgressDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stream_items ADD COLUMN preferredEngine TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novastream_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

package io.github.toyota32k.secureCamera.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MetaData::class, ChapterData::class, KeyValueEntry::class], version = 2, exportSchema = false)
abstract class Database : RoomDatabase() {
    abstract fun metaDataTable():MetaDataTable
    abstract fun chapterDataTable(): ChapterDataTable
    abstract fun kvTable(): KeyValueTable
}



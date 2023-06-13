package io.github.toyota32k.secureCamera.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MetaData::class, ChapterData::class, KeyValueEntry::class], version = 1, exportSchema = false)
abstract class Database : RoomDatabase() {
    abstract fun metaDataTable():MetaDataTable
    abstract fun chapterDataTable(): ChapterDataTable
    abstract fun kvTable(): KeyValueTable
}
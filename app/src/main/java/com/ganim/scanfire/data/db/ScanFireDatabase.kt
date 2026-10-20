package com.ganim.scanfire.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, DocumentEntity::class, PageEntity::class, CodeScanEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ScanFireDatabase : RoomDatabase() {
    abstract fun folders(): FolderDao
    abstract fun documents(): DocumentDao
    abstract fun pages(): PageDao
    abstract fun codeScans(): CodeScanDao

    companion object {
        const val NAME = "scanfire.db"

        fun build(context: Context): ScanFireDatabase =
            Room.databaseBuilder(context, ScanFireDatabase::class.java, NAME).build()
    }
}

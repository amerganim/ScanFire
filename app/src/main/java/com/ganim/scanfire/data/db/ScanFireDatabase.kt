package com.ganim.scanfire.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, DocumentEntity::class, PageEntity::class, CodeScanEntity::class],
    version = 2,
    exportSchema = true,
    // v2: documents.deletedAt (recycle bin).
    autoMigrations = [AutoMigration(from = 1, to = 2)],
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

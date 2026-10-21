package com.ganim.scanfire.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, DocumentEntity::class, PageEntity::class, CodeScanEntity::class, ExpenseEntity::class],
    version = 3,
    exportSchema = true,
    // v2: documents.deletedAt (recycle bin). v3: vault, reminders, expenses.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class ScanFireDatabase : RoomDatabase() {
    abstract fun folders(): FolderDao
    abstract fun documents(): DocumentDao
    abstract fun pages(): PageDao
    abstract fun codeScans(): CodeScanDao
    abstract fun expenses(): ExpenseDao

    companion object {
        const val NAME = "scanfire.db"

        fun build(context: Context): ScanFireDatabase =
            Room.databaseBuilder(context, ScanFireDatabase::class.java, NAME).build()
    }
}

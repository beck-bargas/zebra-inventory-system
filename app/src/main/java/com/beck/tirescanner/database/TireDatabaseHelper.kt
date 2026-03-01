package com.beck.tirescanner.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// TireEntry now includes syncId so devices can identify the same tire
data class TireEntry(
    val id: Int = 0,
    val syncId: String = java.util.UUID.randomUUID().toString(), // unique ID shared across devices
    val barcode: String,
    val brand: String,
    val size: String,
    val quantity: Int,
    val vendor: String? = null,
    val imageUrl: String? = null
)

class TireDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, "tires.db", null, 2  // bumped to version 2 for sync_id migration
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE tires (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sync_id TEXT UNIQUE NOT NULL,
                barcode TEXT,
                brand TEXT,
                size TEXT,
                quantity INTEGER DEFAULT 1,
                vendor TEXT,
                image_url TEXT
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Add sync_id column to existing installs
            db.execSQL("ALTER TABLE tires ADD COLUMN sync_id TEXT")
            // Backfill existing rows with a UUID
            db.execSQL("UPDATE tires SET sync_id = lower(hex(randomblob(16))) WHERE sync_id IS NULL")
        }
    }
}
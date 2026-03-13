package com.beck.tirescanner.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class TireEntry(
    val id: Int = 0,
    val syncId: String = java.util.UUID.randomUUID().toString(), // unique ID shared across devices
    val barcode: String,
    val brand: String,
    val size: String,
    val quantity: Int,
    val imageUrl: String? = null,
    val sku: String = generateSku()
) {

    companion object {
        fun generateSku(): String = (100000..999999).random().toString()
    }
}

class TireDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, "tires.db", null, 4
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tires (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sync_id TEXT UNIQUE NOT NULL,
                barcode TEXT,
                brand TEXT,
                size TEXT,
                quantity INTEGER DEFAULT 1,
                image_url TEXT,
                sku TEXT
            )
        """
        )
        db.execSQL(
            """
            CREATE TABLE barcode_cache(
                barcode TEXT PRIMARY KEY,
                brand TEXT,
                size TEXT,
                image_url TEXT
            )
        """
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE tires ADD COLUMN sync_id TEXT")
            db.execSQL("UPDATE tires SET sync_id = lower(hex(randomblob(16))) WHERE sync_id IS NULL")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE tires ADD COLUMN sku TEXT")
        }
        if (oldVersion < 4) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS barcode_cache (
                barcode TEXT PRIMARY KEY,
                brand TEXT,
                size TEXT,
                image_url TEXT
                )
            """)
            db.execSQL("""
                INSERT OR IGNORE INTO barcode_cache (barcode, brand, size, image_url)
                SELECT barcode, brand, size, image_url FROM tires
            """)
        }
    }
}
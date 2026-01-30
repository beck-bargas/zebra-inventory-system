package com.beck.tirescanner.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TireDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "TireInventory.db"
        private const val DATABASE_VERSION = 1

        // Table name
        const val TABLE_TIRES = "tires"

        // Column names
        const val COLUMN_ID = "id"
        const val COLUMN_BARCODE = "barcode"
        const val COLUMN_BRAND = "brand"
        const val COLUMN_SIZE = "size"
        const val COLUMN_QUANTITY = "quantity"
        const val COLUMN_VENDOR = "vendor"
        const val COLUMN_DATE_ADDED = "date_added"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_TIRES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BARCODE TEXT NOT NULL,
                $COLUMN_BRAND TEXT NOT NULL,
                $COLUMN_SIZE TEXT NOT NULL,
                $COLUMN_QUANTITY INTEGER NOT NULL,
                $COLUMN_VENDOR TEXT,
                $COLUMN_DATE_ADDED INTEGER NOT NULL
            )
        """.trimIndent()

        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TIRES")
        onCreate(db)
    }
}
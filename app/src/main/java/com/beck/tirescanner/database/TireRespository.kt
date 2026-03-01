package com.beck.tirescanner.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.beck.tirescanner.models.Product

class TireRepository(context: Context) {
    private val dbHelper = TireDatabaseHelper(context)

    // Insert a NEW tire into the database
    fun insertTire(product: Product, barcode: String, quantity: Int, vendor: String?): Long {
        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(TireDatabaseHelper.COLUMN_BARCODE, barcode)
            put(TireDatabaseHelper.COLUMN_BRAND, product.brand)
            put(TireDatabaseHelper.COLUMN_SIZE, product.size)
            put(TireDatabaseHelper.COLUMN_QUANTITY, quantity)
            put(TireDatabaseHelper.COLUMN_VENDOR, vendor)
            put(TireDatabaseHelper.COLUMN_DATE_ADDED, System.currentTimeMillis())
        }

        val id = db.insert(TireDatabaseHelper.TABLE_TIRES, null, values)
        db.close()
        return id
    }

    // Check if a tire with this barcode, brand, and size already exists
    fun getTireByDetails(barcode: String, brand: String?, size: String?): TireEntry? {
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            TireDatabaseHelper.TABLE_TIRES,
            null,
            "${TireDatabaseHelper.COLUMN_BARCODE} = ? AND ${TireDatabaseHelper.COLUMN_BRAND} = ? AND ${TireDatabaseHelper.COLUMN_SIZE} = ?",
            arrayOf(barcode, brand ?: "", size ?: ""),
            null,
            null,
            null
        )

        var tire: TireEntry? = null
        cursor.use {
            if (it.moveToFirst()) {
                tire = cursorToTireEntry(it)
            }
        }

        db.close()
        return tire
    }

    // Find tire by barcode (first match)
    fun getTireByBarcode(barcode: String): TireEntry? {
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            TireDatabaseHelper.TABLE_TIRES,
            null,
            "${TireDatabaseHelper.COLUMN_BARCODE} = ?",
            arrayOf(barcode),
            null,
            null,
            null
        )

        var tire: TireEntry? = null
        cursor.use {
            if (it.moveToFirst()) {
                tire = cursorToTireEntry(it)
            }
        }

        db.close()
        return tire
    }

    // Increase quantity (when restocking existing tire)
    fun increaseQuantity(id: Long, amountToAdd: Int): Boolean {
        val db = dbHelper.writableDatabase

        // Get current quantity
        val cursor = db.query(
            TireDatabaseHelper.TABLE_TIRES,
            arrayOf(TireDatabaseHelper.COLUMN_QUANTITY),
            "${TireDatabaseHelper.COLUMN_ID} = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )

        var currentQuantity = 0
        cursor.use {
            if (it.moveToFirst()) {
                currentQuantity = it.getInt(0)
            }
        }

        val newQuantity = currentQuantity + amountToAdd

        val values = ContentValues().apply {
            put(TireDatabaseHelper.COLUMN_QUANTITY, newQuantity)
        }

        val rowsAffected = db.update(
            TireDatabaseHelper.TABLE_TIRES,
            values,
            "${TireDatabaseHelper.COLUMN_ID} = ?",
            arrayOf(id.toString())
        )

        db.close()
        return rowsAffected > 0
    }

    // Decrease quantity (when selling tire)
    fun decreaseQuantity(id: Long, amountToRemove: Int): Boolean {
        val db = dbHelper.readableDatabase

        // Get current quantity
        val cursor = db.query(
            TireDatabaseHelper.TABLE_TIRES,
            arrayOf(TireDatabaseHelper.COLUMN_QUANTITY),
            "${TireDatabaseHelper.COLUMN_ID} = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )

        var currentQuantity = 0
        cursor.use {
            if (it.moveToFirst()) {
                currentQuantity = it.getInt(0)
            }
        }
        cursor.close()

        // Calculate new quantity
        val newQuantity = currentQuantity - amountToRemove

        if (newQuantity < 0) {
            db.close()
            return false // Can't have negative quantity
        }

        // Update or delete
        val writableDb = dbHelper.writableDatabase
        if (newQuantity == 0) {
            // Delete the entry if quantity reaches 0
            writableDb.delete(
                TireDatabaseHelper.TABLE_TIRES,
                "${TireDatabaseHelper.COLUMN_ID} = ?",
                arrayOf(id.toString())
            )
        } else {
            // Update with new quantity
            val values = ContentValues().apply {
                put(TireDatabaseHelper.COLUMN_QUANTITY, newQuantity)
            }
            writableDb.update(
                TireDatabaseHelper.TABLE_TIRES,
                values,
                "${TireDatabaseHelper.COLUMN_ID} = ?",
                arrayOf(id.toString())
            )
        }

        writableDb.close()
        db.close()
        return true
    }

    // Get all tires
    fun getAllTires(): List<TireEntry> {
        val tires = mutableListOf<TireEntry>()
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            TireDatabaseHelper.TABLE_TIRES,
            null,
            null,
            null,
            null,
            null,
            "${TireDatabaseHelper.COLUMN_DATE_ADDED} DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                tires.add(cursorToTireEntry(it))
            }
        }

        db.close()
        return tires
    }

    // Search tires by brand or size
    fun searchTires(query: String): List<TireEntry> {
        val tires = mutableListOf<TireEntry>()
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            TireDatabaseHelper.TABLE_TIRES,
            null,
            "${TireDatabaseHelper.COLUMN_BRAND} LIKE ? OR ${TireDatabaseHelper.COLUMN_SIZE} LIKE ?",
            arrayOf("%$query%", "%$query%"),
            null,
            null,
            "${TireDatabaseHelper.COLUMN_DATE_ADDED} DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                tires.add(cursorToTireEntry(it))
            }
        }

        db.close()
        return tires
    }

    // Get total quantity across all tires
    fun getTotalTireCount(): Int {
        val db = dbHelper.readableDatabase
        var total = 0

        val cursor = db.rawQuery(
            "SELECT SUM(${TireDatabaseHelper.COLUMN_QUANTITY}) FROM ${TireDatabaseHelper.TABLE_TIRES}",
            null
        )

        cursor.use {
            if (it.moveToFirst()) {
                total = it.getInt(0)
            }
        }

        db.close()
        return total
    }

    // Helper function to convert cursor to TireEntry
    private fun cursorToTireEntry(cursor: Cursor): TireEntry {
        return TireEntry(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(TireDatabaseHelper.COLUMN_ID)),
            barcode = cursor.getString(cursor.getColumnIndexOrThrow(TireDatabaseHelper.COLUMN_BARCODE)),
            brand = cursor.getString(cursor.getColumnIndexOrThrow(TireDatabaseHelper.COLUMN_BRAND)),
            size = cursor.getString(cursor.getColumnIndexOrThrow(TireDatabaseHelper.COLUMN_SIZE)),
            quantity = cursor.getInt(cursor.getColumnIndexOrThrow(TireDatabaseHelper.COLUMN_QUANTITY)),
            vendor = cursor.getString(cursor.getColumnIndexOrThrow(TireDatabaseHelper.COLUMN_VENDOR)),
            dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(TireDatabaseHelper.COLUMN_DATE_ADDED))
        )
    }
}

// Data class for tire entries
data class TireEntry(
    val id: Long,
    val barcode: String,
    val brand: String,
    val size: String,
    val quantity: Int,
    val vendor: String?,
    val dateAdded: Long
)
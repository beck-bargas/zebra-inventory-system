package com.beck.tirescanner.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.beck.tirescanner.models.Product

class TireRepository(context: Context) {
    private val dbHelper = TireDatabaseHelper(context)

    private fun cursorToTireEntry(cursor: android.database.Cursor): TireEntry {
        val skuIndex = cursor.getColumnIndex("sku")
        val sku = if (skuIndex >= 0 && !cursor.isNull(skuIndex)) cursor.getString(skuIndex)
        else TireEntry.generateSku()

        val nameIndex = cursor.getColumnIndex("name")
        val brand = cursor.getString(cursor.getColumnIndexOrThrow("brand"))
        val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex)
        else brand

        return TireEntry(
            id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
            syncId = cursor.getString(cursor.getColumnIndexOrThrow("sync_id")),
            barcode = cursor.getString(cursor.getColumnIndexOrThrow("barcode")),
            brand = brand,
            size = cursor.getString(cursor.getColumnIndexOrThrow("size")),
            quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
            imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("image_url")),
            sku = sku,
            name = name
        )
    }

    fun saveToCache(barcode: String, brand: String, size: String, imageUrl: String?, title: String?) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("barcode", barcode)
            put("brand", brand)
            put("size", size)
            put("image_url", imageUrl ?: "")
            put("title", title ?: "")
        }
        db.insertWithOnConflict("barcode_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getCachedTire(barcode: String): TireEntry? {
        val db = dbHelper.readableDatabase
        val cursor = db.query("barcode_cache", null, "barcode = ?", arrayOf(barcode), null, null, null)
        val result = if (cursor.moveToFirst()) {
            val titleIndex = cursor.getColumnIndex("title")
            val title = if (titleIndex >= 0 && !cursor.isNull(titleIndex)) cursor.getString(titleIndex) else null
            TireEntry(
                barcode = cursor.getString(cursor.getColumnIndexOrThrow("barcode")),
                brand = cursor.getString(cursor.getColumnIndexOrThrow("brand")),
                size = cursor.getString(cursor.getColumnIndexOrThrow("size")),
                imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("image_url")),
                quantity = 0,
                title = title
            )
        } else null
        cursor.close()
        return result
    }

    fun getAllTires(): List<TireEntry> {
        val db = dbHelper.readableDatabase
        val tires = mutableListOf<TireEntry>()
        val cursor = db.query("tires", null, null, null, null, null, "brand ASC")
        while (cursor.moveToNext()) tires.add(cursorToTireEntry(cursor))
        cursor.close()
        return tires
    }

    fun getTireByBarcode(barcode: String): TireEntry? {
        val db = dbHelper.readableDatabase
        val cursor = db.query("tires", null, "barcode = ?", arrayOf(barcode), null, null, null)
        val tire = if (cursor.moveToFirst()) cursorToTireEntry(cursor) else null
        cursor.close()
        return tire
    }

    fun getTireByDetails(barcode: String, brand: String?, size: String?): TireEntry? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "tires", null,
            "barcode = ? AND brand = ? AND size = ?",
            arrayOf(barcode, brand ?: "", size ?: ""),
            null, null, null
        )
        val tire = if (cursor.moveToFirst()) cursorToTireEntry(cursor) else null
        cursor.close()
        return tire
    }

    fun getTotalTireCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT SUM(quantity) FROM tires", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count
    }

    fun searchTires(query: String): List<TireEntry> {
        val db = dbHelper.readableDatabase
        val tires = mutableListOf<TireEntry>()
        val cursor = db.query(
            "tires", null,
            "brand LIKE ? OR size LIKE ? OR name LIKE ?",
            arrayOf("%$query%", "%$query%", "%$query%"),
            null, null, "brand ASC"
        )
        while (cursor.moveToNext()) tires.add(cursorToTireEntry(cursor))
        cursor.close()
        return tires
    }

    fun insertTire(product: Product, barcode: String, quantity: Int): Long {
        val db = dbHelper.writableDatabase
        val existingSku = getTireByBarcode(barcode)?.sku
            ?: product.mpn?.takeIf { it.isNotEmpty() }?.let { mpn ->
                (mpn.take(5).padEnd(5, '0') + "0").uppercase()
            }
            ?: TireEntry.generateSku()
        val name = TireEntry.extractNameFromTitle(product.title, product.mpn)
            ?: product.brand
            ?: ""
        val values = ContentValues().apply {
            put("sync_id", java.util.UUID.randomUUID().toString())
            put("barcode", barcode)
            put("brand", product.brand ?: "")
            put("size", product.size ?: "")
            put("quantity", quantity)
            put("image_url", product.images?.firstOrNull() ?: "")
            put("sku", existingSku)
            put("name", name)
        }
        Log.d("TireName", "title='${product.title}' mpn='${product.mpn}' name='$name'")
        return db.insert("tires", null, values)
    }

    fun backfillSkus() {
        val db = dbHelper.writableDatabase
        val cursor = db.query("tires", arrayOf("id"), "sku IS NULL", null, null, null, null)
        while (cursor.moveToNext()) {
            val id = cursor.getInt(0)
            val values = ContentValues().apply { put("sku", TireEntry.generateSku()) }
            db.update("tires", values, "id = ?", arrayOf(id.toString()))
        }
        cursor.close()
    }

    fun deleteTire(id: Int) {
        val db = dbHelper.writableDatabase
        db.delete("tires", "id = ?", arrayOf(id.toString()))
    }

    fun updateQuantity(id: Int, quantity: Int) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply { put("quantity", quantity) }
        db.update("tires", values, "id = ?", arrayOf(id.toString()))
    }

    fun increaseQuantity(id: Int, amount: Int): Boolean {
        val db = dbHelper.writableDatabase
        db.execSQL("UPDATE tires SET quantity = quantity + ? WHERE id = ?", arrayOf(amount, id))
        return true
    }

    fun decreaseQuantity(id: Int, amount: Int): Boolean {
        val db = dbHelper.writableDatabase
        val cursor = db.query("tires", arrayOf("quantity"), "id = ?", arrayOf(id.toString()), null, null, null)
        if (!cursor.moveToFirst()) { cursor.close(); return false }
        val currentQty = cursor.getInt(0)
        cursor.close()
        return if (currentQty - amount <= 0) {
            db.delete("tires", "id = ?", arrayOf(id.toString())) > 0
        } else {
            db.execSQL("UPDATE tires SET quantity = quantity - ? WHERE id = ?", arrayOf(amount, id))
            true
        }
    }

    fun upsertTires(remoteTires: List<TireEntry>) {
        val db = dbHelper.writableDatabase
        for (remote in remoteTires) {
            val cursor = db.query("tires", null, "sync_id = ?", arrayOf(remote.syncId), null, null, null)
            if (cursor.moveToFirst()) {
                val values = ContentValues().apply {
                    put("quantity", remote.quantity)
                    put("name", remote.name)
                    put("sku", remote.sku)
                    put("size", remote.size)
                    put("brand", remote.brand)
                }
                db.update("tires", values, "sync_id = ?", arrayOf(remote.syncId))
            } else {
                val values = ContentValues().apply {
                    put("sync_id", remote.syncId)
                    put("barcode", remote.barcode)
                    put("brand", remote.brand)
                    put("size", remote.size)
                    put("quantity", remote.quantity)
                    put("image_url", remote.imageUrl ?: "")
                    put("sku", remote.sku)
                    put("name", remote.name)
                }
                db.insert("tires", null, values)
            }
            cursor.close()
            saveToCache(remote.barcode, remote.brand, remote.size, remote.imageUrl, remote.name)
        }
    }
    fun deleteNotInList(syncIds: List<String>) {
        Log.d("TireRepo", "deleteNotInList called with ${syncIds.size} ids")
        val db = dbHelper.writableDatabase
        if (syncIds.isEmpty()) {
            val deleted = db.delete("tires", null, null)
            Log.d("TireRepo", "Deleted all: $deleted rows")
            return
        }
        val placeholders = syncIds.joinToString(",") { "?" }
        val deleted = db.delete("tires", "sync_id NOT IN ($placeholders)", syncIds.toTypedArray())
        Log.d("TireRepo", "Deleted $deleted rows not in list")
    }
}
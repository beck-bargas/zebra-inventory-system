package com.beck.tirescanner.database

import android.content.ContentValues
import android.content.Context
import com.beck.tirescanner.models.Product

class TireRepository(context: Context) {
    private val dbHelper = TireDatabaseHelper(context)

    fun getAllTires(): List<TireEntry> {
        val db = dbHelper.readableDatabase
        val tires = mutableListOf<TireEntry>()
        val cursor = db.query("tires", null, null, null, null, null, "brand ASC")
        while (cursor.moveToNext()) {
            tires.add(
                TireEntry(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    syncId = cursor.getString(cursor.getColumnIndexOrThrow("sync_id")),
                    barcode = cursor.getString(cursor.getColumnIndexOrThrow("barcode")),
                    brand = cursor.getString(cursor.getColumnIndexOrThrow("brand")),
                    size = cursor.getString(cursor.getColumnIndexOrThrow("size")),
                    quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                    vendor = cursor.getString(cursor.getColumnIndexOrThrow("vendor")),
                    imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("image_url"))
                )
            )
        }
        cursor.close()
        return tires
    }

    fun getTireByBarcode(barcode: String): TireEntry? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "tires", null,
            "barcode = ?", arrayOf(barcode),
            null, null, null
        )
        val tire = if (cursor.moveToFirst()) {
            TireEntry(
                id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                syncId = cursor.getString(cursor.getColumnIndexOrThrow("sync_id")),
                barcode = cursor.getString(cursor.getColumnIndexOrThrow("barcode")),
                brand = cursor.getString(cursor.getColumnIndexOrThrow("brand")),
                size = cursor.getString(cursor.getColumnIndexOrThrow("size")),
                quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                vendor = cursor.getString(cursor.getColumnIndexOrThrow("vendor")),
                imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("image_url"))
            )
        } else null
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
        val tire = if (cursor.moveToFirst()) {
            TireEntry(
                id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                syncId = cursor.getString(cursor.getColumnIndexOrThrow("sync_id")),
                barcode = cursor.getString(cursor.getColumnIndexOrThrow("barcode")),
                brand = cursor.getString(cursor.getColumnIndexOrThrow("brand")),
                size = cursor.getString(cursor.getColumnIndexOrThrow("size")),
                quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                vendor = cursor.getString(cursor.getColumnIndexOrThrow("vendor")),
                imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("image_url"))
            )
        } else null
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
            "brand LIKE ? OR size LIKE ? OR vendor LIKE ?",
            arrayOf("%$query%", "%$query%", "%$query%"),
            null, null, "brand ASC"
        )
        while (cursor.moveToNext()) {
            tires.add(
                TireEntry(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    syncId = cursor.getString(cursor.getColumnIndexOrThrow("sync_id")),
                    barcode = cursor.getString(cursor.getColumnIndexOrThrow("barcode")),
                    brand = cursor.getString(cursor.getColumnIndexOrThrow("brand")),
                    size = cursor.getString(cursor.getColumnIndexOrThrow("size")),
                    quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                    vendor = cursor.getString(cursor.getColumnIndexOrThrow("vendor")),
                    imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("image_url"))
                )
            )
        }
        cursor.close()
        return tires
    }

    fun insertTire(product: Product, barcode: String, quantity: Int, vendor: String?): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("sync_id", java.util.UUID.randomUUID().toString())
            put("barcode", barcode)
            put("brand", product.brand ?: "")
            put("size", product.size ?: "")
            put("quantity", quantity)
            put("vendor", vendor ?: "")
            put("image_url", product.images?.firstOrNull() ?: "")
        }
        return db.insert("tires", null, values)
    }

    fun increaseQuantity(id: Int, amount: Int): Boolean {
        val db = dbHelper.writableDatabase
        return db.execSQL("UPDATE tires SET quantity = quantity + ? WHERE id = ?",
            arrayOf(amount, id)).let { true }
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

    // Sync: upsert tires from another device, adding quantities together for conflicts
    fun upsertTires(remoteTires: List<TireEntry>) {
        val db = dbHelper.writableDatabase
        for (remote in remoteTires) {
            val cursor = db.query(
                "tires", null,
                "sync_id = ?", arrayOf(remote.syncId),
                null, null, null
            )
            if (cursor.moveToFirst()) {
                // Tire exists locally - add quantities together
                val localQty = cursor.getInt(cursor.getColumnIndexOrThrow("quantity"))
                val remoteQty = remote.quantity
                if (remoteQty > localQty) {
                    // Remote has more - update to remote quantity
                    // (avoids double-counting if we already have their additions)
                    val values = ContentValues().apply {
                        put("quantity", remoteQty)
                    }
                    db.update("tires", values, "sync_id = ?", arrayOf(remote.syncId))
                }
            } else {
                // New tire from other device - insert it
                val values = ContentValues().apply {
                    put("sync_id", remote.syncId)
                    put("barcode", remote.barcode)
                    put("brand", remote.brand)
                    put("size", remote.size)
                    put("quantity", remote.quantity)
                    put("vendor", remote.vendor ?: "")
                    put("image_url", remote.imageUrl ?: "")
                }
                db.insert("tires", null, values)
            }
            cursor.close()
        }
    }
}
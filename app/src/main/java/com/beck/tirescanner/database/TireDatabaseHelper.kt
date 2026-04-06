package com.beck.tirescanner.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class TireEntry(
    val id: Int = 0,
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val barcode: String,
    val brand: String,
    val size: String,
    val quantity: Int,
    val imageUrl: String? = null,
    val sku: String = generateSku(),
    val name: String = brand,
    val title: String? = null
) {
    companion object {
        fun generateSku(): String = (100000..999999).random().toString()

        fun extractNameFromTitle(title: String?, mpn: String?): String? {
            if (title.isNullOrEmpty()) return null
            var cleaned = title

            if (!mpn.isNullOrEmpty()) {
                cleaned = cleaned.replace(Regex("\\b${Regex.escape(mpn)}\\b", RegexOption.IGNORE_CASE), "")
            }

            val sizeRegex = Regex(
                """(?:P|LT|ST|C)?\s*\d{3}\s*/\s*\d{2}\s*R\s*\d{2}(?:\.\d)?(?:\s*\d{2,3}(?:/\d{2,3})?\s*[A-Z]{1,2})?(?:\s*[C-G])?|(?<!/)\d{1,3}R\d{2}(?:\.\d)?(?:\s*\d{2,3}(?:/\d{2,3})?\s*[A-Z]{1,2})?(?:\s*[C-G])?|\d{2,3}[xX]\d{2,3}(?:\.\d+)?R\d{2}|\d{2,3}[xX]\d{1,2}(?:\.\d+)?-\d{2}""",
                RegexOption.IGNORE_CASE
            )
            cleaned = cleaned.replace(sizeRegex, "")
            cleaned = cleaned?.replace(Regex(",.*$"), "")

            val junkWords = listOf(
                "(?<!Cooper )\\bTire\\b", "\\bTires\\b", "\\bBSW\\b", "\\bWSW\\b",
                "\\bOWL\\b", "\\bRWL\\b", "\\bXL\\b", "\\bSL\\b",
                "\\bHigh[- ]Performance\\b", "\\bAll[- ]Terrain\\b", "\\bCommercial\\b", "\\bLight Truck\\b",
                "\\b[C-H]\\b", "\\bLR[C-H]\\b", "\\bBlack Wall\\b", "\\bBW\\b",
                "\\bAll[- ]Season\\b", "\\bfor\\b.*$", "\\(\\d{5,}\\)", "\\b\\d{5,}\\b",
                "\\(.*?(?:PLY|PR|G).*?\\)", "[A-Z]?/\\d+\\s*(?:PLY|PR)\\b", "\\b\\d{1,2}\\s*(?:PLY|Ply|ply)\\b", "\\b\\d{2,3}/\\d{2,3}\\s*[A-Z]{0,2}\\b",
                "(?<![A-Za-z]-?)\\b\\d{2,3}\\s*[A-Z]{1,2}\\b", "(?<![A-Za-z]-?)\\b\\d{2,3}\\s+[A-Z]\\b", "\\bDirectional\\b", "\\bRear\\b",
                "\\bFront\\b",
            )
            for (junk in junkWords) {
                cleaned = cleaned?.replace(Regex(junk, RegexOption.IGNORE_CASE), "")
            }

            cleaned = cleaned?.replace(Regex("\\s+"), " ")?.trim()?.trimEnd(',', '-', '/', ' ')

            return cleaned?.ifEmpty { null }
        }
    }
}

class TireDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, "tires.db", null, 6
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
                image_url TEXT,
                sku TEXT,
                name TEXT
            )
        """)
        db.execSQL("""
            CREATE TABLE barcode_cache (
                barcode TEXT PRIMARY KEY,
                brand TEXT,
                size TEXT,
                image_url TEXT,
                title TEXT
            )
        """)
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
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE tires ADD COLUMN name TEXT")
            db.execSQL("UPDATE tires SET name = brand WHERE name IS NULL")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE barcode_cache ADD COLUMN title TEXT")
        }
    }
}
package com.beck.tirescanner.models

import com.google.gson.annotations.SerializedName

data class BarcodeLookupResponse(
    val products: List<BarcodeLookupProduct> = emptyList()
) {
    fun toProducts(): List<Product> = products.map { it.toProduct() }
}

data class BarcodeLookupProduct(
    val title: String? = null,
    val brand: String? = null,
    val manufacturer: String? = null,
    val size: String? = null,
    val images: List<String>? = null,
    @SerializedName("barcode_number")
    val barcode: String? = null,
    val mpn: String? = null
) {
    fun toProduct(): Product {
        val resolvedBrand = brand?.takeIf { it.isNotEmpty() } ?: manufacturer
        val baseSizeRegex = Regex("""(?:P|LT|ST|C)?\s*\d{3}\s*/\s*\d{2}\s*R\s*\d{2}""", RegexOption.IGNORE_CASE)
        val sizeRegex = Regex("""(?:P|LT|ST|C)?\s*\d{3}\s*/\s*\d{2}\s*R\s*\d{2}(?:\s*\d{2,3}(?:/\d{2,3})?\s*[A-Z]{1,2})?(?:\s*[C-F])?|\d{2}R\d{2}(?:\.\d)?""", RegexOption.IGNORE_CASE)
        val cleanedSize = size?.replace("[", "")?.replace("]", "")?.trim()

        val deduplicatedTitle = deduplicateSizes(title, baseSizeRegex)
        val titleMatch = if (!deduplicatedTitle.isNullOrEmpty()) sizeRegex.findAll(deduplicatedTitle).maxByOrNull { it.value.length } else null
        val sizeMatch = if (!cleanedSize.isNullOrEmpty()) sizeRegex.findAll(cleanedSize).maxByOrNull { it.value.length } else null

        val titleValue = titleMatch?.value?.let { normalizeSize(it) }
        val sizeValue = sizeMatch?.value?.let { normalizeSize(it) }

        val resolvedSize = when {
            titleValue != null && sizeValue != null && titleValue.length >= sizeValue.length -> titleValue
            sizeValue != null -> sizeValue
            titleValue != null -> titleValue
            else -> null
        }

        var finalSize = if (resolvedSize != null && !resolvedSize.startsWith("LT", ignoreCase = true) &&
            title?.contains("light truck", ignoreCase = true) == true) {
            "LT$resolvedSize"
        } else resolvedSize

        val loadRange = detectLoadRange(title)
        if (loadRange != null && finalSize != null && !finalSize.contains(Regex("[C-F]$"))) {
            finalSize = "$finalSize $loadRange"
        }

        return Product(title = title, brand = resolvedBrand, size = finalSize, images = images, barcode = barcode, mpn = mpn)
    }


    private fun deduplicateSizes(title: String?, baseRegex: Regex): String? {
        if (title.isNullOrEmpty()) return title
        val matches = baseRegex.findAll(title).toList()
        if (matches.size <= 1) return title
        val prefixed = matches.firstOrNull { it.value.first().isLetter() }
        val toRemove = if (prefixed != null) matches.filter { it != prefixed } else matches.drop(1)
        var result = title
        for (m in toRemove.sortedByDescending { it.range.first }) {
            result = result?.removeRange(m.range) ?: result
        }
        return result?.replace(Regex("\\s+"), " ")?.trim()
    }

    private fun normalizeSize(raw: String): String {
        return raw
            .replace(Regex("""\s*/\s*"""), "/")
            .replace(Regex("""([RDB])\s*(\d)"""), "$1$2")
            .trim()
    }

    private fun detectLoadRange(title: String?): String? {
        if (title.isNullOrEmpty()) return null
        val plyToLoadRange = mapOf(2 to "A", 4 to "B", 6 to "C", 8 to "D", 10 to "E", 12 to "F")
        val plyMatch = Regex("""\b(\d{1,2})PR\b""", RegexOption.IGNORE_CASE).find(title)
        if (plyMatch != null) {
            val ply = plyMatch.groupValues[1].toIntOrNull()
            return plyToLoadRange[ply]
        }
        val loadRangeMatch = Regex("""\bLoad\s+Range\s+([A-F])\b""", RegexOption.IGNORE_CASE).find(title)
        if (loadRangeMatch != null) return loadRangeMatch.groupValues[1].uppercase()
        val standaloneMatch = Regex("""\b([C-F])\b""", RegexOption.IGNORE_CASE).find(title)
        if (standaloneMatch != null) return standaloneMatch.groupValues[1].uppercase()
        return null
    }
}

data class UpcItemdbResponse(
    val code: String? = null,
    val total: Int = 0,
    val items: List<UpcItem> = emptyList()
) {
    fun toProducts(): List<Product> = items.map { it.toProduct() }
}

data class UpcItem(
    val ean: String? = null,
    val upc: String? = null,
    val title: String? = null,
    val brand: String? = null,
    val size: String? = null,
    val dimension: String? = null,
    val images: List<String>? = null,
    val offers: List<Offer>? = null
) {
    fun toProduct(): Product {
        val baseSizeRegex = Regex("""(?:P|LT|ST|C)?\s*\d{3}\s*/\s*\d{2}\s*R\s*\d{2}""", RegexOption.IGNORE_CASE)
        val sizeRegex = Regex("""(?:P|LT|ST|C)?\s*\d{3}\s*/\s*\d{2}\s*R\s*\d{2}(?:\s*\d{2,3}(?:/\d{2,3})?\s*[A-Z]{1,2})?(?:\s*[C-F])?|\d{2}R\d{2}(?:\.\d)?""", RegexOption.IGNORE_CASE)
        val cleanedSize = size?.replace("[", "")?.replace("]", "")?.trim()

        val deduplicatedTitle = deduplicateSizes(title, baseSizeRegex)
        val titleMatch = if (!deduplicatedTitle.isNullOrEmpty()) sizeRegex.findAll(deduplicatedTitle).maxByOrNull { it.value.length } else null
        val sizeMatch = if (!cleanedSize.isNullOrEmpty()) sizeRegex.findAll(cleanedSize).maxByOrNull { it.value.length } else null

        val titleValue = titleMatch?.value?.let { normalizeSize(it) }
        val sizeValue = sizeMatch?.value?.let { normalizeSize(it) }

        val resolvedSize = when {
            titleValue != null && sizeValue != null && titleValue.length >= sizeValue.length -> titleValue
            sizeValue != null -> sizeValue
            titleValue != null -> titleValue
            !dimension.isNullOrEmpty() -> dimension
            else -> null
        }

        var finalSize = if (resolvedSize != null && !resolvedSize.startsWith("LT", ignoreCase = true) &&
            title?.contains("light truck", ignoreCase = true) == true) {
            "LT$resolvedSize"
        } else resolvedSize

        val loadRange = detectLoadRange(title)
        if (loadRange != null && finalSize != null && !finalSize.contains(Regex("[C-F]$"))) {
            finalSize = "$finalSize $loadRange"
        }

        return Product(title = title, brand = brand, size = finalSize, images = images, barcode = upc ?: ean)
    }


    private fun deduplicateSizes(title: String?, baseRegex: Regex): String? {
        if (title.isNullOrEmpty()) return title
        val matches = baseRegex.findAll(title).toList()
        if (matches.size <= 1) return title
        val prefixed = matches.firstOrNull { it.value.first().isLetter() }
        val toRemove = if (prefixed != null) matches.filter { it != prefixed } else matches.drop(1)
        var result = title
        for (m in toRemove.sortedByDescending { it.range.first }) {
            result = result?.removeRange(m.range) ?: result
        }
        return result?.replace(Regex("\\s+"), " ")?.trim()
    }

    private fun normalizeSize(raw: String): String {
        return raw
            .replace(Regex("""\s*/\s*"""), "/")
            .replace(Regex("""([RDB])\s*(\d)"""), "$1$2")
            .trim()
    }

    private fun detectLoadRange(title: String?): String? {
        if (title.isNullOrEmpty()) return null
        val plyToLoadRange = mapOf(2 to "A", 4 to "B", 6 to "C", 8 to "D", 10 to "E", 12 to "F")
        val plyMatch = Regex("""\b(\d{1,2})PR\b""", RegexOption.IGNORE_CASE).find(title)
        if (plyMatch != null) {
            val ply = plyMatch.groupValues[1].toIntOrNull()
            return plyToLoadRange[ply]
        }
        val loadRangeMatch = Regex("""\bLoad\s+Range\s+([A-F])\b""", RegexOption.IGNORE_CASE).find(title)
        if (loadRangeMatch != null) return loadRangeMatch.groupValues[1].uppercase()
        val standaloneMatch = Regex("""\b([C-F])\b""", RegexOption.IGNORE_CASE).find(title)
        if (standaloneMatch != null) return standaloneMatch.groupValues[1].uppercase()
        return null
    }
}

data class Offer(
    val merchant: String? = null,
    val domain: String? = null,
    val title: String? = null,
    val currency: String? = null,
    val price: Double? = null,
    val shipping: String? = null,
    val condition: String? = null,
    val availability: String? = null,
    val link: String? = null,
    @SerializedName("updated_t")
    val updatedT: Long? = null
)

data class Product(
    val title: String? = null,
    val size: String? = null,
    val brand: String? = null,
    val images: List<String>? = null,
    val barcode: String? = null,
    val mpn: String? = null
) {
    fun hasBrandAndSize(): Boolean = !brand.isNullOrEmpty() && !size.isNullOrEmpty()

    fun getMissingFields(): String {
        val missing = mutableListOf<String>()
        if (brand.isNullOrEmpty()) missing.add("brand")
        if (size.isNullOrEmpty()) missing.add("size")
        return missing.joinToString(" and ")
    }
}
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
        val sizeRegex = Regex("""(?:P|LT|ST|C)?\s*\d{3}\s*/\s*\d{2}\s*R\s*\d{2}|\d{2}R\d{2}(?:\.\d)?""", RegexOption.IGNORE_CASE)
        val cleanedSize = size?.replace("[", "")?.replace("]", "")?.trim()
        val resolvedSize = when {
            !cleanedSize.isNullOrEmpty() && sizeRegex.containsMatchIn(cleanedSize) ->
                sizeRegex.find(cleanedSize)!!.value.replace(Regex("\\s"), "").trim()
            !title.isNullOrEmpty() && sizeRegex.containsMatchIn(title) ->
                sizeRegex.find(title)!!.value.replace(Regex("\\s"), "").trim()
            else -> null
        }
        val finalSize = if (resolvedSize != null && !resolvedSize.startsWith("LT", ignoreCase = true) &&
            title?.contains("light truck", ignoreCase = true) == true) {
            "LT$resolvedSize"
        } else resolvedSize
        return Product(title = title, brand = resolvedBrand, size = finalSize, images = images, barcode = barcode, mpn = mpn)
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
        val sizeRegex = Regex("""(?:P|LT|ST|C)?\s*\d{3}\s*/\s*\d{2}\s*R\s*\d{2}|\d{2}R\d{2}(?:\.\d)?""", RegexOption.IGNORE_CASE)
        val cleanedSize = size?.replace("[", "")?.replace("]", "")?.trim()
        val resolvedSize = when {
            !cleanedSize.isNullOrEmpty() && sizeRegex.containsMatchIn(cleanedSize) ->
                sizeRegex.find(cleanedSize)!!.value.replace(Regex("\\s"), "").trim()
            !title.isNullOrEmpty() && sizeRegex.containsMatchIn(title) ->
                sizeRegex.find(title)!!.value.replace(Regex("\\s"), "").trim()
            !dimension.isNullOrEmpty() -> dimension
            else -> null
        }
        val finalSize = if (resolvedSize != null && !resolvedSize.startsWith("LT", ignoreCase = true) &&
            title?.contains("light truck", ignoreCase = true) == true) {
            "LT$resolvedSize"
        } else resolvedSize
        return Product(title = title, brand = brand, size = finalSize, images = images, barcode = upc ?: ean)
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
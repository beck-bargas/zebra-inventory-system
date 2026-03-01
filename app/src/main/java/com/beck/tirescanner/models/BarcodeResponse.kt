package com.beck.tirescanner.models

import com.google.gson.annotations.SerializedName

// ---- Barcode Lookup (paid) ----
data class BarcodeLookupResponse(
    val products: List<BarcodeLookupProduct> = emptyList()
) {
    fun toProducts(): List<Product> = products.map { it.toProduct() }
}

data class BarcodeLookupProduct(
    val title: String? = null,
    val brand: String? = null,
    val size: String? = null,
    val images: List<String>? = null,
    @SerializedName("barcode_number")
    val barcode: String? = null
) {
    fun toProduct(): Product {
        val sizeRegex = Regex("""(?:P|LT|ST|C)?\s*\d{3}/\d{2}R\d{2}""", RegexOption.IGNORE_CASE)
        val resolvedSize = when {
            !size.isNullOrEmpty() -> size
            !title.isNullOrEmpty() && sizeRegex.containsMatchIn(title) ->
                sizeRegex.find(title)!!.value.trim()
            else -> null
        }
        return Product(title = title, brand = brand, size = resolvedSize, images = images, barcode = barcode)
    }
}

// ---- UPCitemdb (free fallback) ----
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
        val sizeRegex = Regex("""(?:P|LT|ST|C)?\s*\d{3}/\d{2}R\d{2}""", RegexOption.IGNORE_CASE)
        val resolvedSize = when {
            !size.isNullOrEmpty() -> size
            !title.isNullOrEmpty() && sizeRegex.containsMatchIn(title) ->
                sizeRegex.find(title)!!.value.trim()
            !dimension.isNullOrEmpty() -> dimension
            else -> null
        }
        return Product(title = title, brand = brand, size = resolvedSize, images = images, barcode = upc ?: ean)
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

// ---- Shared internal model ----
data class Product(
    val title: String? = null,
    val size: String? = null,
    val brand: String? = null,
    val images: List<String>? = null,
    val barcode: String? = null
) {
    fun hasBrandAndSize(): Boolean = !brand.isNullOrEmpty() && !size.isNullOrEmpty()

    fun getMissingFields(): String {
        val missing = mutableListOf<String>()
        if (brand.isNullOrEmpty()) missing.add("brand")
        if (size.isNullOrEmpty()) missing.add("size")
        return missing.joinToString(" and ")
    }
}
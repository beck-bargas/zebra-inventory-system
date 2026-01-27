package com.beck.tirescanner.models

data class BarcodeResponse(
    val products: List<Product>
)

data class Product(
    val title: String? = null,
    val size: String? = null,
    val brand: String? = null,
    val images: List<String>? = null
) {
    fun hasBrandAndSize(): Boolean {
        return !brand.isNullOrEmpty() && !size.isNullOrEmpty()
    }
}
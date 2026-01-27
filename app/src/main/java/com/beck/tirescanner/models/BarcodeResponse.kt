package com.beck.tirescanner.models

import com.google.gson.annotations.SerializedName

data class BarcodeResponse(
    val products: List<Product>
)

data class Product(
    val title: String? = null,
    val size: String? = null,
    val brand: String? = null,
    val images: List<String>? = null,
    @SerializedName("barcode_number")
    val barcode: String? = null
) {
    fun hasBrandAndSize(): Boolean {
        return !brand.isNullOrEmpty() && !size.isNullOrEmpty()
    }
}
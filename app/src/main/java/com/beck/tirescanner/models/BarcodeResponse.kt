package com.beck.tirescanner.models

data class BarcodeResponse(
    val products: List<Product>
)

data class Product(
    val title: String,
    val size: String,
    val brand: String,
)

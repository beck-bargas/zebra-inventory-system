package com.beck.tirescanner.network

import com.beck.tirescanner.models.BarcodeResponse
import retrofit2.http.*

interface TireApiService {
    @GET("v3/products")
    suspend fun getProductInfo(
        @Query("barcode") barcode: String,
        @Query("key") apiKey: String
    ): BarcodeResponse
}
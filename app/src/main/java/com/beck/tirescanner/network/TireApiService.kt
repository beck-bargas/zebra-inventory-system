package com.beck.tirescanner.network

import com.beck.tirescanner.models.BarcodeLookupResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TireApiService {
    @GET("v3/products")
    suspend fun getProductInfo(
        @Query("barcode") barcode: String,
        @Query("key") apiKey: String
    ): BarcodeLookupResponse
}
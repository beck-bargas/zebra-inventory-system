package com.beck.tirescanner.network

import com.beck.tirescanner.models.UpcItemdbResponse
import retrofit2.http.*

interface UpcApiService {
    @GET("prod/trial/lookup")
    suspend fun getProductInfo(
        @Query("upc") barcode: String,
        @Header("Accept") accept: String = "application/json"
    ): UpcItemdbResponse
}
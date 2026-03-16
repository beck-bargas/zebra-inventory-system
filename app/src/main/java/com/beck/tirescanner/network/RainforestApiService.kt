package com.beck.tirescanner.network

import com.beck.tirescanner.models.RainforestResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface RainforestApiService {
    @GET("request")
    suspend fun getProductByGtin(
        @Query("api_key") apiKey: String,
        @Query("type") type: String,
        @Query("gtin") gtin: String,
        @Query("amazon_domain") amazonDomain: String
    ): RainforestResponse
}
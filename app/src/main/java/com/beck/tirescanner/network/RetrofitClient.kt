package com.beck.tirescanner.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.beck.tirescanner.BuildConfig

object RetrofitClient {
    private const val BASE_URL = "https://api.barcodelookup.com/"
    const val API_KEY = BuildConfig.BARCODE_API_KEY

    val apiService: TireApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TireApiService::class.java)
    }
}
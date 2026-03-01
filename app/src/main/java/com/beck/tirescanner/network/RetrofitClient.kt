package com.beck.tirescanner.network

import com.beck.tirescanner.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    const val API_KEY = BuildConfig.BARCODE_API_KEY

    val apiService: TireApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.barcodelookup.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TireApiService::class.java)
    }

    val upcApiService: UpcApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.upcitemdb.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UpcApiService::class.java)
    }
}
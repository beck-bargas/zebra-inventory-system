package com.beck.tirescanner.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    var API_KEY: String = ""

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
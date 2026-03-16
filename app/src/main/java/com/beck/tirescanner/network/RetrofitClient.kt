package com.beck.tirescanner.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    var RAINFOREST_API_KEY: String = ""

    val upcApiService: UpcApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.upcitemdb.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UpcApiService::class.java)
    }

    val rainforestApiService: RainforestApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.rainforestapi.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RainforestApiService::class.java)
    }
}
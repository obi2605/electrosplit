package com.example.electrosplitapp

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface PaymentHistoryService {
    @GET("/getPaymentHistory/{phone}")
    fun getPaymentHistory(@Path("phone") phone: String): Call<List<PaymentHistoryEntry>>
}

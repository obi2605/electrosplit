package com.example.electrosplitapp

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface BillService {
    @POST("fetchBill")
    fun fetchBill(@Body request: BillRequest): Call<BillResponse>

    // Remove fetchBillDetails if you're not using it
}
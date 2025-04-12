package com.example.electrosplitapp

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface BillService {
    @POST("fetchBill")
    fun fetchBill(@Body request: BillRequest): Call<BillResponse>

    @POST("fetchBillDetails")
    fun fetchBillDetails(@Body request: BillRequest): Call<BillDetailsResponse>
}

// Keep only the new BillDetailsResponse here (since it doesn't exist in Models.kt)
data class BillDetailsResponse(
    val success: Boolean,
    val totalUnits: Float,
    val totalAmount: Float,
    val dueDate: String,
    val billingPeriod: String
)
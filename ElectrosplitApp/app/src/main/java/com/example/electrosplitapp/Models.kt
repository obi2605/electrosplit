package com.example.electrosplitapp

data class BillRequest(
    val consumerNumber: String,
    val operator: String
)

data class BillResponse(
    val success: Boolean,  // Added to match API
    val message: String,   // Added to match API
    val totalUnits: Int,
    val totalAmount: Double
)
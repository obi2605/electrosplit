package com.example.electrosplitapp

data class BillRequest(
    val consumerNumber: String,
    val operator: String
)

data class BillResponse(
    val totalUnits: Int,
    val totalAmount: Double
)
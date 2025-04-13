package com.example.electrosplitapp

data class BillRequest(
    val consumerNumber: String,
    val operator: String
)

data class BillResponse(
    val totalUnits: Int,
    val totalAmount: Double
)

data class UserRequest(
    val phoneNumber: String,
    val password: String,
    val name: String? = null,
    val consumerNumber: String,
    val operator: String
)

data class AuthResponse(
    val success: Boolean,
    val message: String? = null,
    val userId: Int? = null,
    val name: String? = null,
    val consumerNumber: String? = null,
    val operator: String? = null
)
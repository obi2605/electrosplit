package com.electrosplit

import io.ktor.server.application.*  // Correct import for Application
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.Serializable

@Serializable
data class BillRequest(val consumerNumber: String, val operator: String)

@Serializable
data class BillResponse(val totalUnits: Int, val totalAmount: Double)

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}
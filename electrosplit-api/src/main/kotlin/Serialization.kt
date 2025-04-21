package com.electrosplit

import io.ktor.server.application.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.Serializable

@Serializable
data class BillRequest(val consumerNumber: String, val operator: String)

@Serializable
data class BillResponse(val totalUnits: Int, val totalAmount: Double)

@Serializable
data class UserRequest(
    val phoneNumber: String,
    val password: String,
    val name: String? = null
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val message: String? = null,
    val userId: Int? = null,
    val name: String? = null
)

// Add these to Serialization.kt
@Serializable
data class GroupRequest(
    val groupName: String,
    val creatorPhone: String,
    val consumerNumber: String,
    val operator: String,
    val groupQr: String
)

@Serializable
data class GroupResponse(
    val groupId: Int,
    val groupName: String,
    val groupCode: String,
    val groupQr: String,
    val creatorName: String,
    val creatorPhone: String,
    val billDetails: BillResponse
)

@Serializable
data class JoinGroupRequest(
    val groupCode: String,
    val memberPhone: String
)

@Serializable
data class MemberInfo(
    val name: String,
    val phone: String,
    val reading: Float? = null,
    val amountToPay: Float,
    val paymentStatus: String,
    val offsetValue: Float? = null,
    val offsetOrigin: String = "",
    val previousOffsetValue: Float? = null
)



@Serializable
data class GroupDetailsResponse(
    val groupId: Int,
    val groupName: String,
    val groupCode: String,
    val groupQr: String,
    val creatorName: String,
    val creatorPhone: String,
    val billDetails: BillResponse,
    val consumerNumber: String,
    val operator: String,
    val members: List<MemberInfo>,
    val pieChartData: Map<String, Float>,
    val billId: Int? = null // ✅ NEW FIELD
)


@Serializable
data class UpdateGroupRequest(
    val groupId: Int,
    val newName: String? = null,
    val newQr: String? = null
)

@Serializable
data class LeaveGroupRequest(
    val groupId: Int,
    val memberPhone: String
)

@Serializable
data class DeleteGroupRequest(
    val groupId: Int,
    val creatorPhone: String
)

@Serializable
data class SubmitReadingRequest(
    val groupId: Int,
    val phone: String,
    val reading: String,
    val offset: String? = null // ✅ Add this
)



@Serializable
data class UpdateGroupBillRequest(
    val groupId: Int,
    val consumerNumber: String,
    val operator: String
)

@Serializable
data class MarkPaidRequest(
    val groupId: Int,
    val memberPhone: String,
    val splitAmount: Double,
    val consumerNumber: String
)

@Serializable
data class PaymentHistoryEntry(
    val amount: Double,
    val consumerNumber: String,
    val billGenerationDate: String,
    val datetimePaid: String,
    val groupName: String,
    val operator: String,
    val unitsPaidFor: Float,
    val monthLabel: String? = null,
    val monthKey: String? = null
)




fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}
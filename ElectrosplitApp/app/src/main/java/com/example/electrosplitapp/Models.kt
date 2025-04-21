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
    val name: String? = null
)

data class AuthResponse(
    val success: Boolean,
    val message: String? = null,
    val userId: Int? = null,
    val name: String? = null,

)

// Add to Models.kt
data class GroupRequest(
    val groupName: String,
    val creatorPhone: String,
    val consumerNumber: String,
    val operator: String,
    val groupQr: String
)

data class GroupResponse(
    val groupId: Int,
    val groupName: String,
    val groupCode: String,
    val groupQr: String,
    val creatorName: String,
    val creatorPhone: String,
    val billDetails: BillResponse
)

data class JoinGroupRequest(
    val groupCode: String,
    val memberPhone: String
)

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
    val offsetValue: Float?, // ✅ new field
    val offsetOrigin: String?,
    val billId: Int? = null // ✅ NEW FIELD

)



data class UpdateGroupRequest(
    val groupId: Int,
    val newName: String?= null,
    val newQr: String?= null
)

data class LeaveGroupRequest(
    val groupId: Int,
    val memberPhone: String
)

data class DeleteGroupRequest(
    val groupId: Int,
    val creatorPhone: String
)


data class SubmitReadingRequest(
    val groupId: Int,
    val phone: String,
    val reading: String,
    val offset: String? = null // ✅ Add this
)



data class MarkPaidRequest(
    val memberPhone: String,
    val splitAmount: Double,
    val groupId: Int,
    val consumerNumber: String
)

data class PaymentHistoryEntry(
    val amount: Double,
    val consumerNumber: String,
    val billGenerationDate: String,
    val datetimePaid: String,
    val groupName: String,
    val operator: String
)


typealias ResetPaymentRequest = MarkPaidRequest







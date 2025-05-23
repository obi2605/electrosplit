package com.example.electrosplitapp.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.electrosplitapp.*
import com.example.electrosplitapp.data.AuthManager
import com.example.electrosplitapp.utils.QRGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class GroupViewModel(
    private val billService: BillService,
    val authManager: AuthManager
) : ViewModel() {
    private val _groupRestored = MutableStateFlow(false)
    val groupRestored: StateFlow<Boolean> = _groupRestored.asStateFlow()

    private val _groupDetails = MutableStateFlow<GroupDetailsResponse?>(null)
    val groupDetails: StateFlow<GroupDetailsResponse?> = _groupDetails.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)

    val phoneNumber = authManager.phoneNumber
    val currentGroupId = authManager.currentGroupId
    val isGroupCreator = authManager.isGroupCreator

    init {
        restoreGroupIfExists()
    }

    fun restoreGroupIfExists() {
        viewModelScope.launch {
            val groupIdStr = authManager.currentGroupId.first()
            val groupId = groupIdStr?.toIntOrNull()
            Log.d("GroupViewModel", "Restoring group with ID: $groupIdStr")
            if (groupId != null) {
                fetchGroupDetails(groupId)
            }
            _groupRestored.value = true
            Log.d("GroupViewModel", "Group restore complete")
        }
    }

    fun createGroup(
        groupName: String,
        consumerNumber: String,
        operator: String,
        onSuccess: (GroupResponse) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val phone = phoneNumber.first()

                if (phone == null) {
                    _errorMessage.value = "Missing required user data"
                    return@launch
                }

                // Step 1: Send request without QR
                val tempRequest = GroupRequest(
                    groupName = groupName,
                    creatorPhone = phone,
                    consumerNumber = consumerNumber,
                    operator = operator,
                    groupQr = ""
                )

                val response = withContext(Dispatchers.IO) {
                    billService.createGroup(tempRequest).execute()
                }

                if (response.isSuccessful) {
                    response.body()?.let { groupResponse ->
                        val groupCode = groupResponse.groupCode
                        val generatedQr = QRGenerator.generateBase64QRCode(groupCode)

                        // Save locally
                        authManager.saveGroupDetails(
                            groupResponse.groupId,
                            groupResponse.groupName,
                            groupCode,
                            generatedQr,
                            true
                        )

                        // Step 2: Send QR back to backend
                        withContext(Dispatchers.IO) {
                            billService.updateGroup(
                                UpdateGroupRequest(groupResponse.groupId, groupResponse.groupName, generatedQr)
                            ).execute()
                        }

                        onSuccess(groupResponse)
                    } ?: run {
                        _errorMessage.value = "Empty response from server"
                    }
                } else {
                    _errorMessage.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.message}"
                Log.e("GroupViewModel", "Create group failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchGroupDetails(groupId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val response = withContext(Dispatchers.IO) {
                    billService.getGroupDetails(groupId).execute()
                }

                if (response.isSuccessful) {
                    val group = response.body()
                    _groupDetails.value = group

                    val groupCode = group?.groupCode
                    val groupQr = group?.groupQr

                    if (!groupCode.isNullOrBlank() && groupQr.isNullOrBlank()) {
                        val regeneratedQr = QRGenerator.generateBase64QRCode(groupCode)

                        withContext(Dispatchers.IO) {
                            billService.updateGroup(
                                UpdateGroupRequest(group.groupId, group.groupName, regeneratedQr)
                            ).execute()
                        }

                        authManager.saveGroupDetails(
                            group.groupId,
                            group.groupName,
                            group.groupCode,
                            regeneratedQr,
                            isGroupCreator.first()
                        )
                    }
                } else {
                    _errorMessage.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.message}"
                Log.e("GroupViewModel", "Fetch failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun joinGroup(groupCode: String, onSuccess: (GroupDetailsResponse) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val phone = phoneNumber.first()
                if (phone == null) {
                    _errorMessage.value = "Phone number not available"
                    return@launch
                }

                val request = JoinGroupRequest(groupCode, phone)
                val response = withContext(Dispatchers.IO) {
                    billService.joinGroup(request).execute()
                }

                if (response.isSuccessful) {
                    response.body()?.let { groupDetails ->
                        authManager.saveGroupDetails(
                            groupDetails.groupId,
                            groupDetails.groupName,
                            groupDetails.groupCode,
                            groupDetails.groupQr,
                            false
                        )
                        _groupDetails.value = groupDetails
                        onSuccess(groupDetails)
                    } ?: run {
                        _errorMessage.value = "Empty response from server"
                    }
                } else {
                    _errorMessage.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.message}"
                Log.e("GroupViewModel", "Join group failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun leaveGroup(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val groupId = currentGroupId.first()?.toIntOrNull()
                val phone = phoneNumber.first()

                if (groupId == null || phone == null) {
                    _errorMessage.value = "Missing required data"
                    return@launch
                }

                val request = LeaveGroupRequest(groupId, phone)
                val response = withContext(Dispatchers.IO) {
                    billService.leaveGroup(request).execute()
                }

                if (response.isSuccessful) {
                    authManager.clearGroupDetails()
                    _groupDetails.value = null
                    onSuccess()
                } else {
                    _errorMessage.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.message}"
                Log.e("GroupViewModel", "Leave group failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteGroup(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val groupId = currentGroupId.first()?.toIntOrNull()
                val phone = phoneNumber.first()

                if (groupId == null || phone == null) {
                    _errorMessage.value = "Missing required data"
                    return@launch
                }

                val request = DeleteGroupRequest(groupId, phone)
                val response = withContext(Dispatchers.IO) {
                    billService.deleteGroup(request).execute()
                }

                if (response.isSuccessful) {
                    authManager.clearGroupDetails()
                    _groupDetails.value = null
                    onSuccess()
                } else {
                    _errorMessage.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.message}"
                Log.e("GroupViewModel", "Delete group failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun submitReading(reading: String, offset: String? = null, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val groupId = currentGroupId.first()?.toIntOrNull()
                val phone = phoneNumber.first()

                if (groupId == null || phone == null) {
                    _errorMessage.value = "Missing required data"
                    return@launch
                }

                val request = SubmitReadingRequest(
                    groupId = groupId,
                    phone = phone,
                    reading = reading,
                    offset = offset // ✅ include offset
                )

                val response = withContext(Dispatchers.IO) {
                    billService.submitReading(request).execute()
                }

                if (response.isSuccessful) {
                    fetchGroupDetails(groupId)
                    onSuccess()
                } else {
                    _errorMessage.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }



    fun updateGroupName(newName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val groupId = currentGroupId.first()?.toIntOrNull()
                val phone = phoneNumber.first()

                if (groupId == null || phone == null) {
                    _errorMessage.value = "Missing required data"
                    return@launch
                }

                val request = UpdateGroupRequest(groupId, newName)
                val response = withContext(Dispatchers.IO) {
                    billService.updateGroup(request).execute()
                }

                if (response.isSuccessful) {
                    authManager.saveGroupDetails(
                        groupId,
                        newName,
                        "", // Preserve existing code
                        "", // Preserve existing QR
                        isGroupCreator.first()
                    )
                    fetchGroupDetails(groupId)
                    onSuccess()
                } else {
                    _errorMessage.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.message}"
                Log.e("GroupViewModel", "Update group failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateGroupBill(newConsumer: String, newOperator: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val groupId = currentGroupId.first()?.toIntOrNull()
                if (groupId == null) {
                    _errorMessage.value = "Group ID missing"
                    return@launch
                }

                val request = BillRequest(newConsumer, newOperator)
                val response = withContext(Dispatchers.IO) {
                    billService.updateGroupBill(groupId, request).execute()
                }

                if (response.isSuccessful) {
                    val group = response.body()
                    _groupDetails.value = null
                    _groupDetails.value = group

                    group?.let {
                        // ✅ Save updated group state to authManager
                        authManager.saveGroupDetails(
                            it.groupId,
                            it.groupName,
                            it.groupCode,
                            it.groupQr,
                            isGroupCreator.first()
                        )
                    }

                    onSuccess()
                } else {
                    _errorMessage.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markAsPaid(groupId: Int, amountPaid: Float) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val phone = phoneNumber.first()
                val group = groupDetails.value

                if (phone == null || group == null) {
                    _errorMessage.value = "Missing required info"
                    return@launch
                }

                val request = MarkPaidRequest(
                    memberPhone = phone,
                    splitAmount = amountPaid.toDouble(),
                    groupId = groupId,
                    consumerNumber = group.consumerNumber
                )

                val response = withContext(Dispatchers.IO) {
                    billService.markAsPaid(request).execute()
                }

                if (response.isSuccessful) {
                    fetchGroupDetails(groupId)
                } else {
                    _errorMessage.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetPaymentStatus(groupId: Int, amountPaid: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val phone = phoneNumber.first()
                val consumer = groupDetails.value?.consumerNumber

                if (phone == null || consumer.isNullOrBlank()) {
                    _errorMessage.value = "Missing required data"
                    return@launch
                }

                val request = MarkPaidRequest(
                    groupId = groupId,
                    memberPhone = phone,
                    splitAmount = amountPaid,
                    consumerNumber = consumer
                )

                val response = withContext(Dispatchers.IO) {
                    billService.resetPaymentStatus(request).execute()
                }

                if (response.isSuccessful) {
                    fetchGroupDetails(groupId)
                } else {
                    _errorMessage.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getCurrentUserOffset(): Pair<Float?, String?> {
        val phone = phoneNumber.first() ?: return null to null
        val member = groupDetails.value?.members?.find { it.phone == phone }
        return member?.offsetValue to member?.offsetOrigin
    }

    fun triggerBillUpdate(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val group = groupDetails.value ?: return@launch
            val consumer = group.consumerNumber
            val operator = group.operator
            if (consumer.isNotBlank() && operator.isNotBlank()) {
                updateGroupBill(consumer, operator) {}  // 🔄 This hits /updateGroupBill
            }
        }
    }

}

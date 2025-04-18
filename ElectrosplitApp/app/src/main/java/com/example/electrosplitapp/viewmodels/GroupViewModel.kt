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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val phoneNumber = authManager.phoneNumber
    val userName = authManager.userName
    val consumerNumber = authManager.consumerNumber
    val operatorName = authManager.operatorName
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

    fun createGroup(groupName: String, onSuccess: (GroupResponse) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val phone = phoneNumber.first()
                val consumer = consumerNumber.first()
                val operator = operatorName.first()


                if (phone == null || consumer == null || operator == null) {
                    _errorMessage.value = "Missing required user data"
                    return@launch
                }

                // Temporarily create dummy request to get backend-generated groupCode
                val tempRequest = GroupRequest(
                    groupName = groupName,
                    creatorPhone = phone,
                    consumerNumber = consumer,
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

                        // Save locally with proper QR
                        authManager.saveGroupDetails(
                            groupResponse.groupId,
                            groupResponse.groupName,
                            groupCode,
                            generatedQr,
                            true
                        )

                        // Push QR update to backend
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

    fun submitReading(reading: String, onSuccess: () -> Unit) {
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

                val request = SubmitReadingRequest(groupId, phone, reading)
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
                Log.e("GroupViewModel", "Submit reading failed", e)
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

}

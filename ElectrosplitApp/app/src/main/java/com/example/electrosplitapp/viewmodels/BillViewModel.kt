package com.example.electrosplitapp.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.electrosplitapp.BillService
import com.example.electrosplitapp.data.AuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.electrosplitapp.BillDetailsResponse
import com.example.electrosplitapp.BillRequest
import retrofit2.awaitResponse

class BillViewModel(
    private val billService: BillService,
    private val authManager: AuthManager
) : ViewModel() {
    // Existing flows for auth data
    val consumerNumber = authManager.consumerNumber
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val operatorName = authManager.operatorName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val accountName = authManager.accountName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    // New StateFlow implementation for billDetails
    private val _billDetails = MutableStateFlow<BillDetailsResponse?>(null)
    val billDetails: StateFlow<BillDetailsResponse?> = _billDetails.asStateFlow()

    // UI state variables
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        refreshBillData()
    }

    fun refreshBillData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val consumerNumber = consumerNumber.value
                val operator = operatorName.value
                if (consumerNumber != null && operator != null) {
                    val response = billService.fetchBillDetails(
                        BillRequest(consumerNumber, operator)
                    ).awaitResponse()

                    if (response.isSuccessful) {
                        _billDetails.value = response.body()
                    } else {
                        _errorMessage.value = "Failed to fetch bill details"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun logout() {
        authManager.logout()
    }
}
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
                    val response = billService.fetchBill(
                        BillRequest(consumerNumber, operator)
                    ).awaitResponse()

                    if (response.isSuccessful) {
                        val bill = response.body()
                        if (bill != null && bill.success) {
                            _billDetails.value = BillDetailsResponse(
                                success = true,
                                totalUnits = bill.totalUnits.toFloat(),
                                totalAmount = bill.totalAmount.toFloat(),
                                dueDate = "N/A", // You can add these to your BillResponse
                                billingPeriod = "N/A"
                            )
                        } else {
                            _errorMessage.value = "Failed to fetch bill details"
                        }
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
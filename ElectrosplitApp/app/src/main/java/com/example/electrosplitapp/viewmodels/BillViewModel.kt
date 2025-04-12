package com.example.electrosplitapp.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.electrosplitapp.BillRequest
import com.example.electrosplitapp.BillResponse
import com.example.electrosplitapp.BillService
import com.example.electrosplitapp.data.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

class BillViewModel(
    private val billService: BillService,
    private val authManager: AuthManager
) : ViewModel() {

    private val _billResponse = MutableStateFlow<BillResponse?>(null)
    val billResponse: StateFlow<BillResponse?> = _billResponse.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Auth data flows
    val consumerNumber = authManager.consumerNumber
    val operatorName = authManager.operatorName
    val accountName = authManager.accountName

    init {
        Log.d("BillViewModel", "ViewModel initialized")
        fetchBill()
    }

    fun fetchBill() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val consumer = consumerNumber.first()
                val operator = operatorName.first()

                if (consumer == null || operator == null) {
                    _errorMessage.value = "Consumer/Operator not set in auth"
                    return@launch
                }

                val request = BillRequest(consumer, operator)
                val response = withContext(Dispatchers.IO) {
                    billService.fetchBill(request).execute()
                }

                if (response.isSuccessful) {
                    response.body()?.let { bill ->
                        _billResponse.value = bill
                    } ?: run {
                        _errorMessage.value = "Server returned empty response"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    _errorMessage.value = "Server error: ${response.code()} - $errorBody"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.localizedMessage}"
                Log.e("BillViewModel", "Fetch failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }    suspend fun logout() {
        authManager.logout()
    }
}
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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


    // ✅ Call this manually with group-sourced values
    fun fetchBill(consumerNumber: String, operatorName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                if (consumerNumber.isBlank() || operatorName.isBlank()) {
                    _errorMessage.value = "Invalid bill information"
                    return@launch
                }

                val request = BillRequest(consumerNumber, operatorName)
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
    }

    suspend fun logout() {
        authManager.logout()
    }
}

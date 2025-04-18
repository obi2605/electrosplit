package com.example.electrosplitapp.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.electrosplitapp.PaymentHistoryEntry
import com.example.electrosplitapp.PaymentHistoryService
import com.example.electrosplitapp.data.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryViewModel(
    private val paymentHistoryService: PaymentHistoryService,
    private val authManager: AuthManager
) : ViewModel() {

    private val _historyData = MutableStateFlow<List<PaymentHistoryEntry>>(emptyList())
    val historyData: StateFlow<List<PaymentHistoryEntry>> = _historyData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun fetchPaymentHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val phone = authManager.phoneNumber.first()
                if (phone == null) {
                    _errorMessage.value = "Phone number not found"
                    return@launch
                }

                val response = withContext(Dispatchers.IO) {
                    paymentHistoryService.getPaymentHistory(phone).execute()
                }

                if (response.isSuccessful) {
                    _historyData.value = response.body() ?: emptyList()
                } else {
                    _errorMessage.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: ${e.message}"
                Log.e("HistoryViewModel", "Fetch failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}

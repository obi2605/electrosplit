package com.example.electrosplitapp.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.electrosplitapp.PaymentHistoryEntry
import com.example.electrosplitapp.PaymentHistoryService
import com.example.electrosplitapp.data.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

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

    private val _selectedMonth = MutableStateFlow<String?>(null)
    val selectedMonth: StateFlow<String?> = _selectedMonth.asStateFlow()

    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    // Emits entries grouped by month (e.g., "April 2025")
    val groupedHistoryData: StateFlow<Map<String, List<PaymentHistoryEntry>>> =
        combine(_historyData, _selectedMonth, _selectedGroup) { list, monthFilter, groupFilter ->
            list
                .filter { entry ->
                    (monthFilter == null || getMonthYear(entry.datetimePaid) == monthFilter) &&
                            (groupFilter == null || entry.groupName == groupFilter)
                }
                .groupBy { getMonthYear(it.datetimePaid) }
                .toSortedMap(compareByDescending { monthYear -> parseMonthYear(monthYear) })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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
                    _historyData.value = response.body()
                        ?.sortedByDescending { it.datetimePaid }
                        ?: emptyList()
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

    fun setMonthFilter(month: String?) {
        _selectedMonth.value = month
    }

    fun setGroupFilter(group: String?) {
        _selectedGroup.value = group
    }

    fun clearFilters() {
        _selectedMonth.value = null
        _selectedGroup.value = null
    }

    private fun getMonthYear(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            val outputFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            date?.let { outputFormat.format(it) } ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun parseMonthYear(monthYear: String): Date? {
        return try {
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).parse(monthYear)
        } catch (e: Exception) {
            null
        }
    }
}

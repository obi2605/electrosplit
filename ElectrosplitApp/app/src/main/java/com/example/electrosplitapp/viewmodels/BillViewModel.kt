package com.example.electrosplitapp.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.electrosplitapp.BillService
import com.example.electrosplitapp.data.AuthManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BillViewModel(
    private val billService: BillService,
    private val authManager: AuthManager
) : ViewModel() {
    val consumerNumber = authManager.consumerNumber
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val operatorName = authManager.operatorName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val accountName = authManager.accountName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun refreshBillData() {
        viewModelScope.launch {
            // Implement bill refresh logic
        }
    }
}
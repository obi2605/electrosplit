package com.example.electrosplitapp.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.electrosplitapp.PaymentHistoryEntry
import com.example.electrosplitapp.viewmodels.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(historyViewModel: HistoryViewModel) {
    val history by historyViewModel.historyData.collectAsState()
    val loading by historyViewModel.isLoading.collectAsState()
    val errorMessage by historyViewModel.errorMessage.collectAsState()

    LaunchedEffect(Unit) {
        historyViewModel.fetchPaymentHistory()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Payment History") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                errorMessage != null -> {
                    Text("Error: $errorMessage", color = MaterialTheme.colorScheme.error)
                }
                history.isEmpty() -> {
                    Text("No payment history available.")
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(history) { entry ->
                            PaymentHistoryCard(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentHistoryCard(entry: PaymentHistoryEntry) {
    val formattedBillDate = formatDateNoSuffix(entry.billGenerationDate)
    val formattedPaidDate = formatDateNoSuffix(entry.datetimePaid)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Group: ${entry.groupName}", style = MaterialTheme.typography.titleMedium)
            Text("Consumer No: ${entry.consumerNumber}")
            Text("Operator: ${entry.operator}")
            Text("Amount Paid: ₹${"%.2f".format(entry.amount)}")
            Text("Bill Date: $formattedBillDate")
            Text("Paid On: $formattedPaidDate")
        }
    }
}


fun formatDateNoSuffix(timestamp: String): String {
    return try {
        // Adjust parser to match actual format: "yyyy-MM-dd HH:mm:ss.SSSSSS"
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.getDefault())
        val date = parser.parse(timestamp)

        if (date != null) {
            val formatter = SimpleDateFormat("d MMMM yyyy, h:mm a", Locale.getDefault())
            formatter.format(date)
        } else {
            timestamp
        }
    } catch (e: Exception) {
        timestamp
    }
}




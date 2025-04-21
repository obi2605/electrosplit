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
    val groupedHistory by historyViewModel.groupedHistoryData.collectAsState()
    val allHistory by historyViewModel.historyData.collectAsState()
    val loading by historyViewModel.isLoading.collectAsState()
    val errorMessage by historyViewModel.errorMessage.collectAsState()

    val selectedMonth by historyViewModel.selectedMonth.collectAsState()
    val selectedGroup by historyViewModel.selectedGroup.collectAsState()

    val monthOptions = remember(allHistory) {
        allHistory.mapNotNull { getMonthYear(it.datetimePaid) }.distinct().sortedDescending()
    }
    val groupOptions = remember(allHistory) {
        allHistory.map { it.groupName }.distinct().sorted()
    }

    var expandedMonth by remember { mutableStateOf(false) }
    var expandedGroup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        historyViewModel.fetchPaymentHistory()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Payment History") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterDropdown(
                    label = "Month",
                    options = monthOptions,
                    selected = selectedMonth,
                    expanded = expandedMonth,
                    onExpandChange = { expandedMonth = it },
                    onSelect = {
                        historyViewModel.setMonthFilter(it)
                        expandedMonth = false
                    }
                )

                FilterDropdown(
                    label = "Group",
                    options = groupOptions,
                    selected = selectedGroup,
                    expanded = expandedGroup,
                    onExpandChange = { expandedGroup = it },
                    onSelect = {
                        historyViewModel.setGroupFilter(it)
                        expandedGroup = false
                    }
                )

                if (selectedMonth != null || selectedGroup != null) {
                    TextButton(onClick = { historyViewModel.clearFilters() }) {
                        Text("Clear Filters")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Text("Error: $errorMessage", color = MaterialTheme.colorScheme.error)
                }
                groupedHistory.isEmpty() -> {
                    Text("No payment history available.")
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        groupedHistory.forEach { (monthHeading, entries) ->
                            item {
                                Text(
                                    text = monthHeading,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            items(entries) { entry ->
                                PaymentHistoryCard(entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onSelect: (String?) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandChange
    ) {
        OutlinedTextField(
            value = selected ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandChange(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option) }
                )
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
            Text("Units Paid For: ${"%.2f".format(entry.unitsPaidFor)} kWh")
            Text("Bill Date: $formattedBillDate")
            Text("Paid On: $formattedPaidDate")
        }
    }
}

fun formatDateNoSuffix(timestamp: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.getDefault())
        val date = parser.parse(timestamp)
        date?.let {
            val formatter = SimpleDateFormat("d MMMM yyyy, h:mm a", Locale.getDefault())
            formatter.format(it)
        } ?: timestamp
    } catch (e: Exception) {
        timestamp
    }
}

fun getMonthYear(timestamp: String): String? {
    return try {
        val input = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.getDefault())
        val output = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val date = input.parse(timestamp)
        date?.let { output.format(it) }
    } catch (e: Exception) {
        null
    }
}

package com.example.electrosplitapp.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.electrosplitapp.BillResponse
import com.example.electrosplitapp.CameraScreen
import com.example.electrosplitapp.VisionService
import com.example.electrosplitapp.utils.BillCalculator
import com.example.electrosplitapp.viewmodels.BillViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    visionService: VisionService,
    onLogout: () -> Unit,
    viewModel: BillViewModel = viewModel()
) {
    val context = LocalContext.current
    var showCamera by remember { mutableStateOf(false) }
    var manualReading by remember { mutableStateOf("") }
    var showManualDialog by remember { mutableStateOf(false) }
    var showBreakdownDialog by remember { mutableStateOf(false) }
    var calculatedBill by remember { mutableStateOf<BillCalculator.SplitResult?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                processImageFromUri(context, visionService, it) { reading ->
                    manualReading = reading
                    showManualDialog = true
                }
            }
        }
    )

    // State collection
    val billResponse by viewModel.billResponse.collectAsState(initial = null)
    val isLoading by viewModel.isLoading.collectAsState(initial = false)
    val errorMessage by viewModel.errorMessage.collectAsState(initial = null)
    val accountName by viewModel.accountName.collectAsState(initial = null)
    val consumerNumber by viewModel.consumerNumber.collectAsState(initial = null)
    val operator by viewModel.operatorName.collectAsState(initial = null)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Electrosplit") },
                actions = {
                    IconButton(onClick = {
                        viewModel.viewModelScope.launch {
                            viewModel.logout()
                            onLogout()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            AccountInfoCard(
                isLoading = isLoading,
                errorMessage = errorMessage,
                billResponse = billResponse,
                accountName = accountName,
                consumerNumber = consumerNumber,
                operator = operator
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Submit Meter Reading",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            ReadingOptions(
                onScanPressed = { showCamera = true },
                onGalleryPressed = { galleryLauncher.launch("image/*") },
                onManualPressed = { showManualDialog = true }
            )

            calculatedBill?.let { splitResult ->
                Spacer(modifier = Modifier.height(24.dp))
                CalculationResultCard(
                    amount = splitResult.individualBills.first().amountToPay,
                    onViewBreakdown = { showBreakdownDialog = true }
                )
            }
        }
    }

    if (showCamera) {
        CameraScreen(
            visionService = visionService,
            onTextRecognized = { reading ->
                showCamera = false
                manualReading = reading
                showManualDialog = true
            },
            onClose = { showCamera = false }
        )
    }

    if (showManualDialog) {
        ManualReadingDialog(
            initialValue = manualReading,
            totalAmount = billResponse?.totalAmount?.toFloat() ?: 0f,
            totalUnits = billResponse?.totalUnits?.toFloat() ?: 0f,
            groupSize = 1,
            onDismiss = { showManualDialog = false },
            onSubmit = { reading, result ->
                manualReading = reading
                calculatedBill = result
                showManualDialog = false
            }
        )
    }

    if (showBreakdownDialog && calculatedBill != null) {
        BreakdownDialog(
            breakdown = calculatedBill!!.getFormattedBreakdown(),
            onDismiss = { showBreakdownDialog = false }
        )
    }
}

@Composable
private fun AccountInfoCard(
    isLoading: Boolean,
    errorMessage: String?,
    billResponse: BillResponse?,
    accountName: String?,
    consumerNumber: String?,
    operator: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = accountName ?: "My Account",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Consumer Number: ${consumerNumber ?: "Not available"}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Operator: ${operator ?: "Not available"}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading bill data...")
                    }
                }

                errorMessage != null -> {
                    Text(
                        text = "Error: ${errorMessage.takeIf { it.isNotBlank() } ?: "Unknown error"}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                billResponse != null -> {
                    Column {
                        Text(
                            text = "Total Units: ${billResponse.totalUnits} kWh",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Total Amount: ₹${"%.2f".format(billResponse.totalAmount)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                else -> {
                    Text(
                        "No bill data available",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingOptions(
    onScanPressed: () -> Unit,
    onGalleryPressed: () -> Unit,
    onManualPressed: () -> Unit
) {
    Column {
        FilledTonalButton(
            onClick = onScanPressed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = "Scan Meter")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan Meter")
        }

        Spacer(modifier = Modifier.height(8.dp))

        FilledTonalButton(
            onClick = onGalleryPressed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = "From Gallery")
            Spacer(modifier = Modifier.width(8.dp))
            Text("From Gallery")
        }

        Spacer(modifier = Modifier.height(8.dp))

        FilledTonalButton(
            onClick = onManualPressed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Keyboard, contentDescription = "Manual Entry")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enter Manually")
        }
    }
}

@Composable
private fun CalculationResultCard(amount: Float, onViewBreakdown: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Your Share",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "₹${"%.2f".format(amount)}",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onViewBreakdown,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Show Calculation Details")
            }
        }
    }
}

@Composable
private fun ManualReadingDialog(
    initialValue: String,
    totalAmount: Float,
    totalUnits: Float,
    groupSize: Int,
    onDismiss: () -> Unit,
    onSubmit: (String, BillCalculator.SplitResult) -> Unit
) {
    var reading by remember { mutableStateOf(initialValue) }
    var showBreakdown by remember { mutableStateOf(false) }
    val cleanReading = remember(reading) {
        reading.replace("[^0-9.]".toRegex(), "").takeIf { it.isNotEmpty() } ?: "0"
    }

    val splitResult = remember(cleanReading, totalAmount, totalUnits, groupSize) {
        if (cleanReading.isNotEmpty() && cleanReading != "0") {
            val userReading = BillCalculator.parseReading(cleanReading)
            BillCalculator.calculateSplit(
                totalBillAmount = totalAmount,
                totalUnits = totalUnits,
                individualReadings = listOf(userReading),
                groupSize = groupSize
            )
        } else {
            // Return zero result if no valid reading
            BillCalculator.SplitResult(
                individualBills = listOf(BillCalculator.MemberBill(0f, 0f)),
                commonAmount = 0f,
                commonSharePerMember = 0f,
                totalBillAmount = totalAmount,
                totalUnits = totalUnits
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Meter Reading") },
        text = {
            Column {
                OutlinedTextField(
                    value = reading,
                    onValueChange = { reading = it },
                    label = { Text("Current Reading (kWh)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    trailingIcon = { Text("kWh") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (cleanReading.isNotEmpty() && cleanReading != "0") {
                    Text(
                        text = "Reading: ${cleanReading.toFloat()} kWh",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val yourShare = splitResult.individualBills.first().amountToPay
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Your Share: ₹${"%.2f".format(yourShare)}",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    if (cleanReading.isNotEmpty() && cleanReading != "0") {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { showBreakdown = true },
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text("How?")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (cleanReading.isNotEmpty() && cleanReading != "0") {
                        onSubmit(cleanReading, splitResult)
                    }
                },
                enabled = cleanReading.isNotEmpty() && cleanReading != "0"
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showBreakdown) {
        AlertDialog(
            onDismissRequest = { showBreakdown = false },
            title = { Text("Calculation Breakdown") },
            text = {
                Text(splitResult.getFormattedBreakdown())
            },
            confirmButton = {
                Button(onClick = { showBreakdown = false }) {
                    Text("Got It")
                }
            }
        )
    }
}

@Composable
private fun BreakdownDialog(breakdown: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calculation Breakdown") },
        text = {
            Text(breakdown)
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

private fun processImageFromUri(
    context: Context,
    visionService: VisionService,
    uri: Uri,
    onResult: (String) -> Unit
) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)!!
        val jpegBytes = inputStream.readBytes()
        inputStream.close()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reading = visionService.detectDigitsFromJpegBytes(jpegBytes)
                withContext(Dispatchers.Main) {
                    onResult(reading)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult("Error: ${e.message}")
                }
            }
        }
    } catch (e: Exception) {
        onResult("Error: ${e.message}")
    }
}
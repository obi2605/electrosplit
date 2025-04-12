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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.electrosplitapp.BillDetailsResponse
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

    // Maintain all original gallery launcher code
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

    // Maintain all original state collection
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val billDetails by viewModel.billDetails.collectAsState()
    val accountName by viewModel.accountName.collectAsState(initial = null)

    // Maintain original Scaffold structure
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Electrosplit") },
                actions = {
                    IconButton(onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
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
            // Original AccountInfoCard with all features
            AccountInfoCard(
                isLoading = isLoading,
                errorMessage = errorMessage,
                billDetails = billDetails,
                accountName = accountName
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Submit Meter Reading",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Original ReadingOptions with all buttons
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

    // Original CameraScreen implementation
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

    // Enhanced ManualReadingDialog
    if (showManualDialog) {
        ManualReadingDialog(
            initialValue = manualReading,
            billDetails = billDetails,
            groupSize = 1, // Default to 1 (no group)
            onDismiss = { showManualDialog = false },
            onSubmit = { reading, result ->
                manualReading = reading
                calculatedBill = result
                showManualDialog = false
            }
        )
    }

    // New BreakdownDialog
    if (showBreakdownDialog && calculatedBill != null) {
        BreakdownDialog(
            breakdown = calculatedBill!!.getFormattedBreakdown(),
            onDismiss = { showBreakdownDialog = false }
        )
    }
}

// Original AccountInfoCard implementation
@Composable
private fun AccountInfoCard(
    isLoading: Boolean,
    errorMessage: String?,
    billDetails: BillDetailsResponse?,
    accountName: String?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = accountName ?: "My Account",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !errorMessage.isNullOrEmpty() -> {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                billDetails != null -> {
                    Column {
                        Text(
                            text = "Total Units: ${billDetails.totalUnits} kWh",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Total Amount: ₹${"%.2f".format(billDetails.totalAmount)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Billing Period: ${billDetails.billingPeriod}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Due Date: ${billDetails.dueDate}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

// Original ReadingOptions implementation
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

// Enhanced CalculationResultCard
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

// Enhanced ManualReadingDialog
@Composable
private fun ManualReadingDialog(
    initialValue: String,
    billDetails: BillDetailsResponse?,
    groupSize: Int,
    onDismiss: () -> Unit,
    onSubmit: (String, BillCalculator.SplitResult) -> Unit
) {
    var reading by remember { mutableStateOf(initialValue) }
    val cleanReading = remember(reading) {
        reading.replace("[^0-9.]".toRegex(), "").takeIf { it.isNotEmpty() } ?: "0"
    }

    val splitResult = remember(cleanReading, billDetails, groupSize) {
        if (cleanReading.isNotEmpty() && billDetails != null) {
            val userReading = BillCalculator.parseReading(cleanReading)
            BillCalculator.calculateSplit(
                totalBillAmount = billDetails.totalAmount,
                totalUnits = billDetails.totalUnits,
                individualReadings = listOf(userReading),
                groupSize = groupSize
            )
        } else null
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
                    suffix = { Text("kWh") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (cleanReading.isNotEmpty()) {
                    Text(
                        text = "Reading: ${cleanReading.toFloat()} kWh",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                splitResult?.let { result ->
                    val yourShare = result.individualBills.first().amountToPay
                    Text(
                        text = "Your Share: ₹${"%.2f".format(yourShare)}",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    if (groupSize > 1) {
                        Text(
                            text = "Includes ${"%.2f".format(result.commonSharePerMember)} kWh common usage",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (splitResult != null) {
                        onSubmit(cleanReading, splitResult)
                    }
                },
                enabled = splitResult != null
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
}

// New BreakdownDialog
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

// Original processImageFromUri implementation
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
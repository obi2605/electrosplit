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
    var calculatedBill by remember { mutableStateOf<Float?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                processImageFromUri(context, visionService, it) { reading: String ->
                    manualReading = reading
                    showManualDialog = true
                }
            }
        }
    )

    // Collect StateFlow values
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val billDetails by viewModel.billDetails.collectAsState()

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
            AccountInfoCard(
                isLoading = isLoading,
                errorMessage = errorMessage,
                billDetails = billDetails,
                accountName = viewModel.accountName.collectAsState().value
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

            calculatedBill?.let { amount ->
                Spacer(modifier = Modifier.height(24.dp))
                CalculationResultCard(amount = amount)
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
            onDismiss = { showManualDialog = false },
            onSubmit = { reading ->
                calculatedBill = calculateBillShare(reading)
                showManualDialog = false
            }
        )
    }
}

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
private fun CalculationResultCard(amount: Float) {
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
        }
    }
}

@Composable
private fun ManualReadingDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reading by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Meter Reading") },
        text = {
            Column {
                OutlinedTextField(
                    value = reading,
                    onValueChange = {
                        reading = it.filter { c -> c.isDigit() || c == '.' }
                    },
                    label = { Text("Current Reading (kWh)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    suffix = { Text("kWh") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (reading.isNotEmpty()) {
                    Text(
                        text = "Reading: ${reading}kWh",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(reading) },
                enabled = reading.isNotBlank()
            ) {
                Text("Calculate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun calculateBillShare(reading: String): Float {
    return try {
        val units = reading.toFloat()
        BillCalculator.calculateBill(units)
    } catch (e: NumberFormatException) {
        0f
    }
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
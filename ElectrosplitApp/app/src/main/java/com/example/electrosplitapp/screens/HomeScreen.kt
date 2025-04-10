package com.example.electrosplitapp.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.electrosplitapp.VisionService
import com.example.electrosplitapp.viewmodels.BillViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// CORRECT Material 3 Icon imports
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Keyboard
import com.example.electrosplitapp.CameraScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    visionService: VisionService,
    onLogout: () -> Unit,
    viewModel: BillViewModel = viewModel()
) {
    val context = LocalContext.current
    var showCamera by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var manualReading by remember { mutableStateOf("") }
    var showManualDialog by remember { mutableStateOf(false) }
    var calculatedBill by remember { mutableStateOf<Float?>(null) }

    // Gallery Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                processImageFromUri(context, visionService, it) { reading ->
                    manualReading = reading
                    showManualDialog = true
                }
            }
            showGallery = false
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Electrosplit") },
                actions = {
                    IconButton(onClick = onLogout) {
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
            // Account Info Card
            AccountInfoCard(viewModel)

            Spacer(modifier = Modifier.height(24.dp))

            // Meter Reading Section
            Text(
                text = "Submit Meter Reading",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reading Options
            ReadingOptions(
                onScanPressed = { showCamera = true },
                onGalleryPressed = { galleryLauncher.launch("image/*") },
                onManualPressed = { showManualDialog = true }
            )

            // Calculation Result
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
                calculatedBill = calculateBillShare(reading.toFloat())
                showManualDialog = false
            }
        )
    }
}

@Composable
private fun AccountInfoCard(viewModel: BillViewModel) {
    val accountName by viewModel.accountName.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = accountName ?: "My Account",
                style = MaterialTheme.typography.titleLarge
            )
            // Add more account info here
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
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
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
            OutlinedTextField(
                value = reading,
                onValueChange = { reading = it },
                label = { Text("Current Reading") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )
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

private fun calculateBillShare(reading: Float): Float {
    // Implement your actual calculation logic here
    return reading * 0.75f // Example calculation
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
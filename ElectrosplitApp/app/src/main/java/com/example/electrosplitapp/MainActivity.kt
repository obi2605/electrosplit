package com.example.electrosplitapp

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.electrosplitapp.ui.theme.ElectrosplitAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    private lateinit var visionService: VisionService
    private lateinit var billService: BillService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        visionService = VisionService(applicationContext)
        billService = Retrofit.Builder()
            .baseUrl("http://192.168.1.2:8080/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BillService::class.java)

        setContent {
            ElectrosplitAppTheme {
                ElectrosplitApp(visionService, billService)
            }
        }
    }

    override fun onDestroy() {
        visionService.shutdown()
        super.onDestroy()
    }
}

@Composable
fun ElectrosplitApp(visionService: VisionService, billService: BillService) {
    var consumerNumber by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var showCamera by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                isScanning = true
                result = "Processing image..."
                processImageFromUri(context, visionService, it) { reading ->
                    isScanning = false
                    result = "Meter Reading: $reading"
                    Log.d("GalleryFlow", "Recognized meter: $reading")
                }
            }
        }
    )

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = consumerNumber,
            onValueChange = { consumerNumber = it },
            label = { Text("Consumer Number") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = Color.Black)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = operator,
            onValueChange = { operator = it },
            label = { Text("Operator") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = Color.Black)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val request = BillRequest(consumerNumber, operator)
                billService.fetchBill(request).enqueue(object : Callback<BillResponse> {
                    override fun onResponse(call: Call<BillResponse>, response: Response<BillResponse>) {
                        result = if (response.isSuccessful) {
                            val bill = response.body()
                            "Total Units: ${bill?.totalUnits}, Amount: ₹${bill?.totalAmount}"
                        } else {
                            "Bill not found"
                        }
                    }
                    override fun onFailure(call: Call<BillResponse>, t: Throwable) {
                        result = "Error: ${t.message}"
                    }
                })
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Fetch Bill")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                showCamera = true
                isScanning = true
                result = ""
                Log.d("CameraFlow", "Starting meter scanning")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan Meter")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { galleryLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select from Gallery")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = result,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (isScanning) {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Processing...")
            }
        }
    }

    if (showCamera) {
        CameraScreen(
            visionService = visionService,
            onTextRecognized = { text ->
                showCamera = false
                isScanning = false
                result = "Meter Reading: $text"
                Log.d("CameraFlow", "Recognized meter: $text")
            },
            onClose = {
                showCamera = false
                isScanning = false
                result = "Scan cancelled"
                Log.d("CameraFlow", "Scan cancelled by user")
            }
        )
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
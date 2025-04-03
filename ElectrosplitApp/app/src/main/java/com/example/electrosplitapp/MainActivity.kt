package com.example.electrosplitapp

import android.content.Context
import android.graphics.BitmapFactory
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.InputStream
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    private val billService by lazy {
        Retrofit.Builder()
            .baseUrl("http://192.168.1.2:8080/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BillService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ElectrosplitAppTheme {
                ElectrosplitApp(billService)
            }
        }
    }
}

@Composable
fun ElectrosplitApp(billService: BillService) {
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
                processImageFromUri(context, it) { reading ->
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
            modifier =  Modifier.fillMaxWidth()
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
            Spacer(modifier =  Modifier.height(16.dp))
            Column(
                modifier =  Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier =  Modifier.height(8.dp))
                Text("Processing...")
            }
        }
    }

    if (showCamera) {
        CameraScreen(
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

private fun processImageFromUri(context: Context, uri: Uri, onResult: (String) -> Unit) {
    try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (bitmap !=  null) {
            val processedBitmap = ImagePreprocessor.preprocessBitmap(bitmap)
            val image = InputImage.fromBitmap(processedBitmap, 0)

            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { visionText ->
                    extractMeterReading(visionText)?.let { reading ->
                        onResult(reading)
                    } ?: onResult("No valid reading found")
                }
                .addOnFailureListener { e ->
                    onResult("Error: ${e.message}")
                    Log.e("GalleryFlow", "Image processing  failed", e)
                }
        } else {
            onResult("Failed to load image")
            Log.e("GalleryFlow", "Bitmap decoding  failed")
        }
    } catch (e: Exception) {
        onResult("Error: ${e.message}")
        Log.e("GalleryFlow", "Image processing exception", e)
    }
}

private fun extractMeterReading(visionText: Text): String? {
    // Get all potential number sequences
    val numberSequences = visionText.textBlocks
        .flatMap { block -> block.lines }
        .map { line -> line.text.replace(Regex("[^0-9]"), "") }
        .filter { it  .length in 4  ..8  }

    Log.d(  "  OCR_CANDIDATES  ",  "  Potential readings: $numberSequences  "  )

    // Simple scoring - prefer longer sequences
    return numberSequences.maxByOrNull { it  .  length }  ?.  let {
        when  {
            it  .  contains(  '.'  )  -> it  .  take(  8  )  // Preserve decimals
            it  .  length  >=  5  ->  "  ${it  .  dropLast  (  1  )  }  .  ${  it  .  takeLast  (  1  )  }  "  // Add decimal
            else  ->  it
        }  .  also  { result  ->
            Log  .  d  (  "  OCR_RESULT  ",  "  Selected reading: $  result  "  )
        }
    }
}
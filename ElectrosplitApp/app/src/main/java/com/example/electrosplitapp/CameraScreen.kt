package com.example.electrosplitapp

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import kotlin.math.abs

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScreen(
    onTextRecognized: (String) -> Unit,
    onClose: () -> Unit
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // States
    var hasCameraPermission by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Preparing camera...") }
    var isScanning by remember { mutableStateOf(false) }
    var lastDetectionTime by remember { mutableLongStateOf(0L) }
    var stableReading by remember { mutableStateOf<String?>(null) }
    var readingConfidence by remember { mutableIntStateOf(0) }

    // Permission launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) statusMessage = "Camera permission required"
        }
    )

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val executor = Executors.newSingleThreadExecutor()

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()

                            // Preview
                            val preview = Preview.Builder()
                                .build()
                                .also { it.surfaceProvider = previewView.surfaceProvider }

                            // Analyzer
                            val imageAnalyzer = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analyzer ->
                                    analyzer.setAnalyzer(executor, createAnalyzer { visionText ->
                                        val now = System.currentTimeMillis()
                                        if (now - lastDetectionTime > 500) { // 500ms between scans
                                            lastDetectionTime = now
                                            val reading = extractMeterReading(visionText)
                                            if (reading != null) {
                                                if (stableReading == reading) {
                                                    readingConfidence++
                                                    if (readingConfidence >= 3) { // Require 3 consistent readings
                                                        onTextRecognized(reading)
                                                        stableReading = null
                                                        readingConfidence = 0
                                                    }
                                                } else {
                                                    stableReading = reading
                                                    readingConfidence = 1
                                                }
                                            }
                                        }
                                    })
                                }

                            // Bind use cases
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalyzer
                            )

                            isScanning = true
                            statusMessage = "Hold steady for 2 seconds"

                        } catch (e: Exception) {
                            statusMessage = "Camera error"
                            Log.e("CameraScreen", "Setup failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Visual guide overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width * 0.8f
            val height = size.height * 0.25f
            drawRect(
                color = Color.Yellow.copy(alpha = 0.3f),
                topLeft = Offset((size.width - width)/2, (size.height - height)/2),
                size = Size(width, height),
                style = Stroke(width = 5f)
            )
        }

        // Status message
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusMessage,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
            if (isScanning) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Confidence: $readingConfidence/3",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Cancel button
        Button(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red.copy(alpha = 0.8f),
                contentColor = Color.White
            )
        ) {
            Text("Cancel Scan")
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun createAnalyzer(
    onTextDetected: (Text) -> Unit
): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { imageProxy ->
    try {
        val mediaImage = imageProxy.image ?: return@Analyzer
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                Log.d("OCR_DEBUG", "Raw text detected: ${visionText.text}")
                onTextDetected(visionText)
            }
            .addOnFailureListener { e ->
                Log.e("OCR_ERROR", "Recognition failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } catch (e: Exception) {
        Log.e("ANALYZER_ERROR", "Image analysis failed", e)
        imageProxy.close()
    }
}

private fun extractMeterReading(visionText: Text): String? {
    // Get all potential number sequences
    val numberSequences = visionText.textBlocks
        .flatMap { block -> block.lines }
        .map { line -> line.text.replace(Regex("[^0-9.]"), "") }
        .filter { it.length in 4..8 }

    Log.d("OCR_CANDIDATES", "Potential readings: $numberSequences")

    // Simple scoring - prefer longer sequences
    return numberSequences.maxByOrNull { it.length }?.let {
        when {
            it.contains('.') -> it.take(8) // Preserve decimals
            it.length >= 5 -> "${it.dropLast(1)}.${it.takeLast(1)}" // Add decimal
            else -> it
        }.also { result ->
            Log.d("OCR_RESULT", "Selected reading: $result")
        }
    }
}
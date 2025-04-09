package com.example.electrosplitapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

class VisionService(private val context: Context) {
    private val tAG = "VisionService"

    private val visionClient by lazy {
        try {
            // 1. Verify file access
            val inputStream = context.resources.openRawResource(R.raw.service_account)
            Log.d(tAG, "Service account file exists. Size: ${inputStream.available()} bytes")

            // 2. Load credentials (with proper error handling)
            val credentials = try {
                GoogleCredentials.fromStream(inputStream).also {
                    Log.d(tAG, "Credentials loaded successfully")
                    // Debug: Print first 50 chars of private key as a sanity check
                    val keyPreview = it.toString().take(50)
                    Log.d(tAG, "Credentials preview: $keyPreview...")
                }
            } finally {
                inputStream.close()
            }

            // 3. Initialize client
            ImageAnnotatorClient.create(
                ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider { credentials }
                    .build()
            ).also {
                Log.d(tAG, "Vision client initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(tAG, "Initialization failed", e.apply {
                // Log detailed error context
                Log.e(tAG, "Error type: ${e.javaClass.simpleName}")
                Log.e(tAG, "Error message: ${e.message}")
                if (e.cause != null) {
                    Log.e(tAG, "Root cause: ${e.cause?.message}")
                }
            })
            null
        }
    }
    suspend fun detectDigits(imageProxy: ImageProxy): String {
        return withContext(Dispatchers.IO) {
            try {
                val jpegBytes = convertToJpegBytes(imageProxy)
                detectDigitsFromJpegBytes(jpegBytes)
            } catch (e: Exception) {
                Log.e(tAG, "Detection error", e)
                "Error: ${e.message?.take(50)}"
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun convertToJpegBytes(imageProxy: ImageProxy): ByteArray {
        val image = imageProxy.image ?: throw Exception("No image data")
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val bitmap = Bitmap.createBitmap(
            image.width,
            image.height,
            Bitmap.Config.ARGB_8888
        ).apply {
            copyPixelsFromBuffer(image.planes[0].buffer)
        }

        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        return ByteArrayOutputStream().use { output ->
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            output.toByteArray()
        }.also {
            image.close()
            if (rotatedBitmap != bitmap) {
                rotatedBitmap.recycle()
            }
            bitmap.recycle()
        }
    }

    suspend fun detectDigitsFromJpegBytes(jpegBytes: ByteArray): String {
        return withContext(Dispatchers.IO) {
            try {
                val client = visionClient ?: return@withContext "Vision client not initialized"

                // Build the image request
                val imgBytes = ByteString.copyFrom(jpegBytes)
                val img = Image.newBuilder().setContent(imgBytes).build()

                // We only need text detection
                val feature = Feature.newBuilder()
                    .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
                    .build()

                val request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(img)
                    .build()

                // Perform the request
                val response = client.batchAnnotateImages(listOf(request))
                    .responsesList.first()

                if (response.hasError()) {
                    return@withContext "API Error: ${response.error.message}"
                }

                // Extract and process the text
                val fullText = response.fullTextAnnotation?.text ?: ""
                Log.d(tAG, "Full recognized text:\n$fullText")

                // Parse the meter reading from the text
                parseMeterReading(fullText)
            } catch (e: Exception) {
                Log.e(tAG, "Vision API error", e)
                "Error: ${e.message?.take(50)}"
            }
        }
    }

    private fun parseMeterReading(fullText: String): String {
        // Split text into lines and clean each line
        val candidates = fullText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                // Remove all non-digit characters except decimal point
                val cleanLine = line.replace("[^0-9.]".toRegex(), "")

                // Check if this looks like a meter reading
                if (cleanLine.length in 4..8 &&
                    cleanLine.matches("^[0-9.]*$".toRegex()) &&
                    cleanLine.count { it == '.' } <= 1) {
                    cleanLine to line
                } else {
                    null
                }
            }

        Log.d(tAG, "Potential meter reading candidates: $candidates")

        // Return the first candidate that looks like a valid reading
        return candidates.firstOrNull()?.first ?: findBestNumberCandidate(fullText)
    }

    private fun findBestNumberCandidate(fullText: String): String {
        return fullText.lines()
            .mapNotNull { line ->
                // Find all sequences of digits in the line
                Regex("\\d+").findAll(line)
                    .maxByOrNull { it.value.length }
                    ?.value
                    ?.let { digits -> digits to line }
            }
            .maxByOrNull { (digits, _) -> digits.length }
            ?.first ?: "No readable meter found"
    }

    fun shutdown() {
        try {
            visionClient?.close()
        } catch (e: Exception) {
            Log.e(tAG, "Error closing Vision client", e)
        }
    }
}
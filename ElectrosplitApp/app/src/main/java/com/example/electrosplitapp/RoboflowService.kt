package com.example.electrosplitapp

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Environment
import android.util.Base64
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

object RoboflowService {
    private const val API_KEY = "420L33QgmZR2CScgiOwi"
    private const val MODEL_ENDPOINT = "metersocr7" // Changed from "white-numbers0 model"
    private const val MODEL_VERSION = "1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun detectDigits(imageProxy: ImageProxy): String {
        return withContext(Dispatchers.IO) {
            try {
                val jpegBytes = convertToJpegBytes(imageProxy)
                saveDebugImage(jpegBytes)
                detectDigitsFromJpegBytes(jpegBytes)
            } catch (e: Exception) {
                Log.e("ROBOFLOW", "Detection error", e)
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
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            output.toByteArray()
        }.also {
            image.close()
            if (rotatedBitmap != bitmap) {
                rotatedBitmap.recycle()
            }
            bitmap.recycle()
        }
    }

    private fun saveDebugImage(bytes: ByteArray) {
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "electrosplit_debug")
            dir.mkdirs()

            val file = File(dir, "debug_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            Log.d("DEBUG", "Saved image to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("DEBUG", "Failed to save image", e)
        }
    }

    suspend fun detectDigitsFromJpegBytes(jpegBytes: ByteArray): String {
        return withContext(Dispatchers.IO) {
            try {
                val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                val requestBody = base64Image.toRequestBody("application/x-www-form-urlencoded".toMediaType())

                val request = Request.Builder()
                    .url("https://detect.roboflow.com/$MODEL_ENDPOINT/$MODEL_VERSION?api_key=$API_KEY")
                    .post(requestBody)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build()

                Log.d("ROBOFLOW", "Sending request with ${jpegBytes.size} bytes")
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                Log.d("ROBOFLOW", "Received response: ${response.code} - $responseBody")

                if (!response.isSuccessful) {
                    return@withContext "API Error ${response.code}: $responseBody"
                }

                parseAndFormatResponse(responseBody)
            } catch (e: Exception) {
                Log.e("ROBOFLOW", "Network error", e)
                "Error: ${e.message?.take(50)}"
            }
        }
    }

    private fun parseAndFormatResponse(json: String): String {
        return try {
            val root = JSONObject(json)
            when {
                root.has("error") -> "API Error: ${root.getString("error")}"
                !root.has("predictions") -> "No digits detected"
                else -> {
                    val predictions = root.getJSONArray("predictions")
                    if (predictions.length() == 0) {
                        "No readable digits"
                    } else {
                        // 1. Filter and sort with confidence threshold
                        val validDigits = predictions
                            .let { 0.until(it.length()).map { i -> it.getJSONObject(i) } }
                            .filter {
                                it.getString("class").startsWith("D") &&
                                        it.getDouble("confidence") >= 0.75
                            }
                            .sortedBy { it.getDouble("x") } // Sort by x-position AFTER filtering

                        // 2. Extract digits in correct left-to-right order
                        val digits = validDigits.joinToString("") {
                            it.getString("class").removePrefix("D")
                        }

                        // 3. Apply strict digit formatting rules
                        when {
                            digits.isEmpty() -> "0.0 kWh"
                            digits.length == 5 -> "${digits}.0 kWh" // 12345 → 12345.0
                            digits.length >= 6 -> {
                                "${digits.dropLast(1)}.${digits.takeLast(1)} kWh" // 123456 → 12345.6
                            }
                            else -> {
                                // For <5 digits, pad with leading zeros before decimal
                                val padded = digits.padStart(5, '0')
                                "${padded.dropLast(1)}.${padded.takeLast(1)} kWh" // 578 → 0057.8
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ROBOFLOW", "Parse error", e)
            "Parse Error"
        }
    }}
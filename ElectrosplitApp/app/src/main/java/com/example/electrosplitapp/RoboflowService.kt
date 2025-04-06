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
    private const val MODEL_VERSION = "1"

    // Using basic OkHttp client without logging interceptor
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
                // Method that matches your working curl command exactly
                val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                val requestBody = base64Image.toRequestBody("application/x-www-form-urlencoded".toMediaType())

                val request = Request.Builder()
                    .url("https://detect.roboflow.com/white-numbers/$MODEL_VERSION?api_key=$API_KEY")
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
                !root.has("predictions") -> {
                    Log.d("ROBOFLOW", "No predictions in response: $json")
                    "No digits detected"
                }
                else -> {
                    val predictions = root.getJSONArray("predictions")
                    if (predictions.length() == 0) {
                        "No readable digits"
                    } else {
                        predictions
                            .let { 0.until(it.length()).map { i -> it.getJSONObject(i) } }
                            .sortedBy { it.getDouble("x") }
                            .joinToString("") { it.getString("class") }
                            .let { digits ->
                                when {
                                    digits.isEmpty() -> "0.0"
                                    digits.length == 1 -> "0.$digits"
                                    else -> "${digits.dropLast(1)}.${digits.takeLast(1)}"
                                } + " kWh"
                            }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ROBOFLOW", "Parse error: $json", e)
            "Parse Error"
        }
    }
}
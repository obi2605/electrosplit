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
    private const val MODEL_ENDPOINT = "7seg-fzxkn"
    private const val MODEL_VERSION = "2"

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
            rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
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
            if (root.has("error")) return "API Error: ${root.getString("error")}"
            if (!root.has("predictions")) return "No digits detected"

            val predictions = root.getJSONArray("predictions")
            if (predictions.length() == 0) return "No readable digits"

            val all = (0 until predictions.length()).map { predictions.getJSONObject(it) }

            val digitsOnly = all.filter {
                it.getString("class").startsWith("D") && it.getDouble("confidence") >= 0.5
            }.sortedBy { it.getDouble("x") }

            val totdec = all.find {
                it.getString("class") == "TOTDEC" && it.getDouble("confidence") >= 0.5
            }

            if (digitsOnly.isEmpty()) return "0.0 kWh"

            val digitValues = digitsOnly.map { it.getString("class").removePrefix("D") }
            val digitX = digitsOnly.map { it.getDouble("x") }

            // Only use TOTDEC if it appears before the last digit
            val shouldUseTotdec = totdec != null && totdec.getDouble("x") < digitX.last()

            return if (shouldUseTotdec) {
                val totX = totdec!!.getDouble("x")

                val decimalIndex = digitX.indexOfFirst { it > totX }.let {
                    if (it == -1) digitValues.size else it
                }

                val beforeDecimal = digitValues.take(decimalIndex).joinToString("")
                val afterDecimal = digitValues.drop(decimalIndex).joinToString("")

                val formatted = when {
                    beforeDecimal.isEmpty() && afterDecimal.isEmpty() -> "0.0"
                    beforeDecimal.isEmpty() -> "0.$afterDecimal"
                    afterDecimal.isEmpty() -> "$beforeDecimal.0"
                    else -> "$beforeDecimal.$afterDecimal"
                }

                "$formatted kWh"
            } else {
                val digits = digitValues.joinToString("")
                val digitCount = digits.length

                when {
                    digitCount >= 2 -> "${digits.dropLast(1)}.${digits.takeLast(1)} kWh"
                    digitCount == 1 -> "0.${digits} kWh"
                    else -> "0.0 kWh"
                }
            }

        } catch (e: Exception) {
            Log.e("ROBOFLOW", "Parse error", e)
            "Parse Error"
        }
    }
}

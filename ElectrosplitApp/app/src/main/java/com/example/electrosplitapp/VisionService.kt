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

class VisionService(private val context: Context) {
    private val tag = "VisionService"
    private var visionClient: ImageAnnotatorClient? = null
    private var isShutdown = false

    init {
        initializeClient()
    }

    private fun initializeClient() {
        if (visionClient != null) return

        try {
            val inputStream = context.resources.openRawResource(R.raw.service_account)
            val credentials = GoogleCredentials.fromStream(inputStream)
            inputStream.close()

            visionClient = ImageAnnotatorClient.create(
                ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider { credentials }
                    .build()
            )
            isShutdown = false
            Log.d(tag, "Vision client initialized successfully")
        } catch (e: Exception) {
            Log.e(tag, "Initialization failed", e)
            visionClient = null
        }
    }

    suspend fun detectDigits(imageProxy: ImageProxy): String {
        if (isShutdown) {
            initializeClient()
        }
        return withContext(Dispatchers.IO) {
            try {
                val jpegBytes = convertToJpegBytes(imageProxy)
                detectDigitsFromJpegBytes(jpegBytes)
            } catch (e: Exception) {
                Log.e(tag, "Detection error", e)
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
        if (isShutdown) {
            initializeClient()
        }

        return withContext(Dispatchers.IO) {
            try {
                val client = visionClient ?: return@withContext "Vision client not initialized"

                val imgBytes = ByteString.copyFrom(jpegBytes)
                val img = Image.newBuilder().setContent(imgBytes).build()

                val feature = Feature.newBuilder()
                    .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
                    .build()

                val request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(img)
                    .build()

                val response = client.batchAnnotateImages(listOf(request))
                    .responsesList.first()

                if (response.hasError()) {
                    return@withContext "API Error: ${response.error.message}"
                }

                val fullText = response.fullTextAnnotation?.text ?: ""
                Log.d(tag, "Full recognized text:\n$fullText")

                parseMeterReading(fullText)
            } catch (e: Exception) {
                Log.e(tag, "Vision API error", e)
                "Error: ${e.message?.take(50)}"
            }
        }
    }

    private fun parseMeterReading(fullText: String): String {
        val candidates = fullText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val cleanLine = line.replace("[^0-9.]".toRegex(), "")

                if (cleanLine.length in 4..8 &&
                    cleanLine.matches("^[0-9.]*$".toRegex()) &&
                    cleanLine.count { it == '.' } <= 1) {
                    cleanLine to line
                } else {
                    null
                }
            }

        Log.d(tag, "Potential meter reading candidates: $candidates")

        return candidates.firstOrNull()?.first ?: findBestNumberCandidate(fullText)
    }

    private fun findBestNumberCandidate(fullText: String): String {
        return fullText.lines()
            .mapNotNull { line ->
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
            visionClient = null
            isShutdown = true
            Log.d(tag, "Vision client shutdown successfully")
        } catch (e: Exception) {
            Log.e(tag, "Error closing Vision client", e)
        }
    }
}
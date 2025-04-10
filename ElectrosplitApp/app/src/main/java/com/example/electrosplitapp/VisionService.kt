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
import kotlin.math.abs

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

                parseMeterReading(response)
            } catch (e: Exception) {
                Log.e(tag, "Vision API error", e)
                "Error: ${e.message?.take(50)}"
            }
        }
    }

    private fun parseMeterReading(response: AnnotateImageResponse): String {
        val fullText = response.fullTextAnnotation ?:
        return "No text detected"

        // Map to store digit sequences and their combined bounding boxes
        val digitSequences = mutableListOf<DigitSequence>()

        // 1. Extract all possible digit sequences with their bounding boxes
        for (page in fullText.pagesList) {
            for (block in page.blocksList) {
                for (paragraph in block.paragraphsList) {
                    val sequences = findContiguousDigitSequences(paragraph)
                    digitSequences.addAll(sequences)
                }
            }
        }

        Log.d(tag, "All digit sequences: ${digitSequences.joinToString()}")

        // 2. Find the largest valid sequence
        val largestValidSequence = digitSequences
            .filter { isValidMeterReading(it.digits) }
            .maxByOrNull { it.boundingArea }

        // 3. If found, check for adjacent unmerged digits
        val finalReading = largestValidSequence?.let { sequence ->
            val extendedDigits = tryExtendSequence(sequence, digitSequences)
            if (extendedDigits != sequence.digits) {
                Log.d(tag, "Extended sequence from ${sequence.digits} to $extendedDigits")
            }
            extendedDigits
        }

        // 4. Fallback to text-based search if no valid sequence found
        return finalReading ?: findBestNumberCandidate(fullText.text)
    }

    /**
     * Finds contiguous digit sequences within a paragraph and calculates their combined bounding boxes
     */
    private fun findContiguousDigitSequences(paragraph: Paragraph): List<DigitSequence> {
        val sequences = mutableListOf<DigitSequence>()
        var currentDigits = StringBuilder()
        var currentSymbols = mutableListOf<Symbol>()

        for (word in paragraph.wordsList) {
            for (symbol in word.symbolsList) {
                if (symbol.text.matches(Regex("[0-9.]"))) {
                    currentDigits.append(symbol.text)
                    currentSymbols.add(symbol)
                } else {
                    if (currentDigits.isNotEmpty()) {
                        sequences.add(createDigitSequence(currentDigits.toString(), currentSymbols))
                        currentDigits.clear()
                        currentSymbols.clear()
                    }
                }
            }
        }

        // Add the last sequence if any
        if (currentDigits.isNotEmpty()) {
            sequences.add(createDigitSequence(currentDigits.toString(), currentSymbols))
        }

        return sequences
    }

    private fun createDigitSequence(digits: String, symbols: List<Symbol>): DigitSequence {
        return DigitSequence(
            digits = digits,
            boundingArea = calculateCombinedBoundingBoxArea(symbols),
            symbols = symbols
        )
    }

    /**
     * Attempts to extend a digit sequence by merging with adjacent digit sequences
     * that are close enough and have no separating characters
     */
    private fun tryExtendSequence(
        baseSequence: DigitSequence,
        allSequences: List<DigitSequence>
    ): String {
        val baseSymbols = baseSequence.symbols
        if (baseSymbols.isEmpty()) return baseSequence.digits

        // Find sequences that are adjacent to our base sequence
        val adjacentSequences = allSequences.filter { candidate ->
            candidate != baseSequence &&
                    isAdjacentWithoutGaps(baseSymbols, candidate.symbols)
        }

        // Merge all adjacent digit sequences
        return (listOf(baseSequence) + adjacentSequences)
            .sortedBy { it.symbols.firstOrNull()?.boundingBox?.verticesList?.firstOrNull()?.x ?: 0 }
            .joinToString("") { it.digits }
    }

    /**
     * Checks if two symbol groups are adjacent without non-digit characters between them
     */
    private fun isAdjacentWithoutGaps(
        symbols1: List<Symbol>,
        symbols2: List<Symbol>
    ): Boolean {
        if (symbols1.isEmpty() || symbols2.isEmpty()) return false

        val lastSymbol1 = symbols1.last()
        val firstSymbol2 = symbols2.first()

        // Check if the symbols are approximately on the same line
        val y1 = lastSymbol1.boundingBox.verticesList.averageY()
        val y2 = firstSymbol2.boundingBox.verticesList.averageY()
        if (abs(y1 - y2) > 10) return false  // Not on same line

        // Check horizontal proximity (no large gap)
        val x1 = lastSymbol1.boundingBox.verticesList.maxX()
        val x2 = firstSymbol2.boundingBox.verticesList.minX()
        return (x2 - x1) < 50  // Max 50px gap allowed
    }

    private fun calculateCombinedBoundingBoxArea(symbols: List<Symbol>): Int {
        if (symbols.isEmpty()) return 0

        val allVertices = symbols.flatMap { it.boundingBox.verticesList }
        val xs = allVertices.map { it.x }
        val ys = allVertices.map { it.y }
        val width = xs.maxOrNull()!! - xs.minOrNull()!!
        val height = ys.maxOrNull()!! - ys.minOrNull()!!

        return width * height
    }

    private fun isValidMeterReading(digits: String): Boolean {
        return digits.length >= 4 &&
                digits.matches("^[0-9.]*$".toRegex()) &&
                digits.count { it == '.' } <= 1
    }

    private fun findBestNumberCandidate(fullText: String): String {
        return fullText.lines()
            .mapNotNull { line ->
                Regex("\\d+").findAll(line)
                    .maxByOrNull { it.value.length }
                    ?.value
            }
            .maxByOrNull { it.length }
            ?: "No readable meter found"
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

    // Helper data class
    private data class DigitSequence(
        val digits: String,
        val boundingArea: Int,
        val symbols: List<Symbol>
    )

    // Extension functions for VertexList
    private fun List<Vertex>.averageY() = map { it.y }.average().toInt()
    private fun List<Vertex>.minX() = minOf { it.x }
    private fun List<Vertex>.maxX() = maxOf { it.x }
}
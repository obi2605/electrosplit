/*package com.example.electrosplitapp

import android.graphics.*

object ImagePreprocessor {
    fun preprocessForMeterReading(bitmap: Bitmap): Bitmap {
        // 1. Crop to center area where main display is (60% width, 40% height)
        val cropRect = Rect(
            (bitmap.width * 0.2f).toInt(),
            (bitmap.height * 0.3f).toInt(),
            (bitmap.width * 0.8f).toInt(),
            (bitmap.height * 0.7f).toInt()
        )
        var processed = Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        )

        // 2. Convert to grayscale with white boost
        processed = toGrayscale(processed, boostWhites = true)

        // 3. Apply aggressive threshold to isolate white numbers
        processed = highContrastThreshold(processed, threshold = 200)

        // 4. Clean up small noise
        processed = removeSmallNoise(processed, minPixelSize = 10)

        return processed
    }

    fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        var processed = autoRotateBitmap(bitmap)
        processed = preprocessForMeterReading(processed)
        return processed
    }

    // --- Optimized Helper Methods ---
    private fun toGrayscale(bmp: Bitmap, boostWhites: Boolean = true): Bitmap {
        val grayBitmap = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            var gray = (r * 0.299 + g * 0.587 + b * 0.114).toInt()

            // Boost white pixels if needed
            if (boostWhites && gray > 160) {
                gray = 255
            }
            pixels[i] = Color.rgb(gray, gray, gray)
        }

        grayBitmap.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return grayBitmap
    }

    private fun highContrastThreshold(bmp: Bitmap, threshold: Int = 200): Bitmap {
        val thresholdBitmap = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)

        for (i in pixels.indices) {
            val gray = Color.red(pixels[i]) // Using red channel since it's grayscale
            pixels[i] = if (gray > threshold) Color.WHITE else Color.BLACK
        }

        thresholdBitmap.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return thresholdBitmap
    }

    private fun removeSmallNoise(bmp: Bitmap, minPixelSize: Int): Bitmap {
        // Simple noise removal by ignoring small white regions
        val output = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        // Draw background
        canvas.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), paint)

        // Only copy large enough white areas
        paint.color = Color.WHITE
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)

        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                val pixel = pixels[y * bmp.width + x]
                if (Color.red(pixel) > 128) { // White pixel
                    // Check if part of a large enough region
                    if (isLargeEnough(bmp, x, y, minPixelSize)) {
                        canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                    }
                }
            }
        }

        return output
    }

    private fun isLargeEnough(bmp: Bitmap, x: Int, y: Int, minSize: Int): Boolean {
        // Simple check for continuous white area
        var count = 0
        for (dy in -1..1) {
            for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until bmp.width && ny in 0 until bmp.height) {
                    if (Color.red(bmp.getPixel(nx, ny)) > 128) {
                        count++
                        if (count >= minSize) return true
                    }
                }
            }
        }
        return false
    }

    private fun autoRotateBitmap(bitmap: Bitmap): Bitmap {
        // Implement EXIF rotation handling if needed
        return bitmap
    }
}*/
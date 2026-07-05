package com.xiyunmn.cwmhook.feature.statusbar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View

internal class StatusBarRenderedColorSampler(
    private val sampleBitmapWidth: Int,
    private val sampleBitmapHeight: Int,
) {
    fun sample(appRoot: View, sampleY: Int): Int? {
        val sourceHeight = minOf(sampleBitmapHeight, appRoot.height - sampleY)
        if (sourceHeight <= 0) {
            return null
        }
        val bitmapWidth = minOf(sampleBitmapWidth, appRoot.width)
        val bitmap = Bitmap.createBitmap(bitmapWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.scale(bitmapWidth.toFloat() / appRoot.width.toFloat(), 1f)
            canvas.translate(0f, -sampleY.toFloat())
            appRoot.draw(canvas)
            dominantOpaqueColor(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun dominantOpaqueColor(bitmap: Bitmap): Int? {
        val buckets = HashMap<Int, ColorBucket>()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) < 220) {
                    continue
                }
                val key = ((Color.red(color) shr 3) shl 10) or
                    ((Color.green(color) shr 3) shl 5) or
                    (Color.blue(color) shr 3)
                val bucket = buckets.getOrPut(key) { ColorBucket() }
                bucket.count += 1
                bucket.r += Color.red(color)
                bucket.g += Color.green(color)
                bucket.b += Color.blue(color)
            }
        }
        val best = buckets.values.maxByOrNull { it.count } ?: return null
        return Color.rgb(
            (best.r / best.count).toInt(),
            (best.g / best.count).toInt(),
            (best.b / best.count).toInt(),
        )
    }
}

package com.ganim.scanfire.imaging

import android.graphics.Bitmap
import android.graphics.RectF

/** Turns an original capture plus the page's edit settings into the image that is shown and exported. */
object PageRenderer {
    fun render(original: Bitmap, rotation: Int, crop: RectF, filter: String): Bitmap {
        val rotated = Bitmaps.rotate(original, rotation)
        val cropped = crop(rotated, crop)
        return Filters.apply(cropped, filter)
    }

    fun crop(bitmap: Bitmap, crop: RectF): Bitmap {
        val full = crop.left <= 0f && crop.top <= 0f && crop.right >= 1f && crop.bottom >= 1f
        if (full) return bitmap
        val l = (crop.left.coerceIn(0f, 1f) * bitmap.width).toInt()
        val t = (crop.top.coerceIn(0f, 1f) * bitmap.height).toInt()
        val r = (crop.right.coerceIn(0f, 1f) * bitmap.width).toInt()
        val b = (crop.bottom.coerceIn(0f, 1f) * bitmap.height).toInt()
        if (r - l < 8 || b - t < 8) return bitmap
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }
}

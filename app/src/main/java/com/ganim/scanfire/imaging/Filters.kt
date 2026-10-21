package com.ganim.scanfire.imaging

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.ganim.scanfire.BuildConfig

enum class PageFilter(val key: String, val label: String) {
    Original("original", "Original"),
    /** Whitens paper, removes shadows and colour casts, sharpens text. */
    Magic("magic", "Clear scan"),
    /** Brighter and crisper, colours kept as they are. */
    Enhance("enhance", "Bright"),
    Gray("gray", "Grey"),
    BlackWhite("bw", "B&W"),
    ;

    companion object {
        fun of(key: String): PageFilter = entries.firstOrNull { it.key == key } ?: Original
    }
}

object Filters {
    const val ORIGINAL = "original"

    fun apply(bitmap: Bitmap, filter: String): Bitmap {
        val f = PageFilter.of(filter)
        if (f == PageFilter.Original) return bitmap
        val started = SystemClock.elapsedRealtime()
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val out = when (f) {
            PageFilter.Original -> px
            PageFilter.Enhance -> FilterKernels.enhance(px, w, h)
            PageFilter.Magic -> FilterKernels.magic(px, w, h)
            PageFilter.Gray -> FilterKernels.grayscale(px)
            PageFilter.BlackWhite -> FilterKernels.blackWhite(px, w, h)
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888).also {
            if (BuildConfig.DEBUG) Log.d("ScanFire", "filter ${f.key} ${w}x$h took ${SystemClock.elapsedRealtime() - started} ms")
        }
    }
}

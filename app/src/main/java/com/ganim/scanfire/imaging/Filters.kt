package com.ganim.scanfire.imaging

import android.graphics.Bitmap

/** Page filters. Phase 2 ships only the identity; phase 3 adds the real ones. */
object Filters {
    const val ORIGINAL = "original"

    fun apply(bitmap: Bitmap, filter: String): Bitmap = bitmap
}

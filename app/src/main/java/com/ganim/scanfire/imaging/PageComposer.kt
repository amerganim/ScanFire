package com.ganim.scanfire.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Builds composite pages: both sides of an ID card on one sheet, or a book spread split in two. */
object PageComposer {
    /** A4 at 150 dpi, white, with each card at 70% of the page width (roughly real size for ID-1 cards). */
    fun idCardSheet(sides: List<Bitmap>): Bitmap {
        val w = 1240
        val h = 1754
        val sheet = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val cardW = w * 0.7f
        val heights = sides.map { cardW * it.height / it.width }
        val gap = 80f
        val total = heights.sum() + gap * (sides.size - 1)
        // Shrink everything uniformly if tall (portrait-orientation) cards don't fit.
        val fit = minOf(1f, (h - 160f) / total)
        var y = (h - total * fit) / 2
        sides.forEachIndexed { i, side ->
            val cw = cardW * fit
            val ch = heights[i] * fit
            val x = (w - cw) / 2
            canvas.drawBitmap(side, null, RectF(x, y, x + cw, y + ch), paint)
            y += ch + gap * fit
        }
        return sheet
    }

    /** Splits a two-page spread down the middle (landscape) or across (portrait) into reading order. */
    fun splitSpread(spread: Bitmap): List<Bitmap> =
        if (spread.width >= spread.height) {
            val half = spread.width / 2
            listOf(Bitmap.createBitmap(spread, 0, 0, half, spread.height), Bitmap.createBitmap(spread, half, 0, spread.width - half, spread.height))
        } else {
            val half = spread.height / 2
            listOf(Bitmap.createBitmap(spread, 0, 0, spread.width, half), Bitmap.createBitmap(spread, 0, half, spread.width, spread.height - half))
        }
}

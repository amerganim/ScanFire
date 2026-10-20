package com.ganim.scanfire.imaging

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-Kotlin filter maths over ARGB pixel arrays, kept free of android.graphics so it can be
 * unit-tested on the JVM. [Filters] wraps these for Bitmaps.
 */
object FilterKernels {

    fun luma(p: Int): Int {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun argb(r: Int, g: Int, b: Int) =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Luminance values at the given lower and upper percentiles (0..1). */
    fun percentiles(px: IntArray, low: Double, high: Double): Pair<Int, Int> {
        val hist = IntArray(256)
        // Sampling every few pixels is plenty for a histogram and keeps large pages fast.
        val step = max(1, px.size / 400_000)
        var n = 0
        var i = 0
        while (i < px.size) { hist[luma(px[i])]++; n++; i += step }
        fun at(fraction: Double): Int {
            val target = (n * fraction).toLong()
            var acc = 0L
            for (v in 0..255) { acc += hist[v]; if (acc > target) return v }
            return 255
        }
        return at(low) to at(high)
    }

    /** Auto-levels: stretches the 1st-99th percentile range to full contrast. */
    fun enhance(px: IntArray): IntArray {
        val (lo, hi) = percentiles(px, 0.01, 0.99)
        if (hi - lo < 16) return px.copyOf()
        val lut = IntArray(256) { v -> ((v - lo) * 255.0 / (hi - lo)).roundToInt().coerceIn(0, 255) }
        return IntArray(px.size) { i ->
            val p = px[i]
            argb(lut[(p shr 16) and 0xFF], lut[(p shr 8) and 0xFF], lut[p and 0xFF])
        }
    }

    fun grayscale(px: IntArray): IntArray {
        val (lo, hi) = percentiles(px, 0.01, 0.99)
        val range = max(16, hi - lo)
        return IntArray(px.size) { i ->
            val v = ((luma(px[i]) - lo) * 255 / range).coerceIn(0, 255)
            argb(v, v, v)
        }
    }

    /**
     * Estimates the paper colour across the page: block means, then a max over neighbouring blocks
     * so dark text inside a block doesn't drag the estimate down, then bilinear upsampling.
     * Returns one luminance value per pixel.
     */
    fun backgroundLuma(px: IntArray, w: Int, h: Int, blocks: Int = 24): IntArray {
        val bs = max(4, max(w, h) / blocks)
        val bw = (w + bs - 1) / bs
        val bh = (h + bs - 1) / bs
        val mean = IntArray(bw * bh)
        for (by in 0 until bh) for (bx in 0 until bw) {
            var sum = 0L; var count = 0
            val y1 = min(h, (by + 1) * bs); val x1 = min(w, (bx + 1) * bs)
            var y = by * bs
            while (y < y1) { var x = bx * bs; while (x < x1) { sum += luma(px[y * w + x]); count++; x += 2 }; y += 2 }
            mean[by * bw + bx] = if (count == 0) 255 else (sum / count).toInt()
        }
        val dilated = IntArray(mean.size) { idx ->
            val bx = idx % bw; val by = idx / bw
            var m = 0
            for (dy in -1..1) for (dx in -1..1) {
                val nx = bx + dx; val ny = by + dy
                if (nx in 0 until bw && ny in 0 until bh) m = max(m, mean[ny * bw + nx])
            }
            m
        }
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val fy = ((y + 0.5) / bs - 0.5).coerceIn(0.0, (bh - 1).toDouble())
            val y0 = fy.toInt(); val y1 = min(bh - 1, y0 + 1); val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5) / bs - 0.5).coerceIn(0.0, (bw - 1).toDouble())
                val x0 = fx.toInt(); val x1 = min(bw - 1, x0 + 1); val tx = fx - x0
                val top = dilated[y0 * bw + x0] * (1 - tx) + dilated[y0 * bw + x1] * tx
                val bottom = dilated[y1 * bw + x0] * (1 - tx) + dilated[y1 * bw + x1] * tx
                out[y * w + x] = (top * (1 - ty) + bottom * ty).roundToInt()
            }
        }
        return out
    }

    /**
     * "Magic colour": divides out the uneven background (shadows, yellowed paper) so the page goes
     * white, keeps ink colours and boosts their saturation a little.
     */
    fun magic(px: IntArray, w: Int, h: Int): IntArray {
        val bg = backgroundLuma(px, w, h)
        return IntArray(px.size) { i ->
            val p = px[i]
            val gain = 255.0 / max(48, bg[i])
            var r = ((p shr 16) and 0xFF) * gain
            var g = ((p shr 8) and 0xFF) * gain
            var b = (p and 0xFF) * gain
            val l = (r * 0.299 + g * 0.587 + b * 0.114)
            // Saturation boost around the pixel's own luminance.
            r = l + (r - l) * 1.35; g = l + (g - l) * 1.35; b = l + (b - l) * 1.35
            // Gentle S-curve: near-white goes white, ink gets darker.
            fun curve(v: Double): Int {
                val n = (v / 255.0).coerceIn(0.0, 1.0)
                val c = if (n > 0.88) 1.0 else n * n * (3 - 2 * n) * 0.7 + n * 0.3
                return (c * 255).roundToInt()
            }
            argb(curve(r), curve(g), curve(b))
        }
    }

    /**
     * Black and white via Bradley adaptive thresholding: a pixel is ink when it is noticeably darker
     * than the mean of its neighbourhood, which copes with shadows a global threshold can't.
     */
    fun blackWhite(px: IntArray, w: Int, h: Int, sensitivity: Double = 0.12): IntArray {
        val lum = IntArray(px.size) { luma(px[it]) }
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += lum[y * w + x]
                integral[(y + 1) * (w + 1) + (x + 1)] = integral[y * (w + 1) + (x + 1)] + row
            }
        }
        val half = max(4, max(w, h) / 32)
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        return IntArray(px.size) { i ->
            val x = i % w; val y = i / w
            val x0 = max(0, x - half); val x1 = min(w - 1, x + half)
            val y0 = max(0, y - half); val y1 = min(h - 1, y + half)
            val count = (x1 - x0 + 1) * (y1 - y0 + 1)
            val sum = integral[(y1 + 1) * (w + 1) + (x1 + 1)] - integral[y0 * (w + 1) + (x1 + 1)] -
                integral[(y1 + 1) * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
            if (lum[i] * count < sum * (1 - sensitivity)) black else white
        }
    }
}

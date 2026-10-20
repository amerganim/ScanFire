package com.ganim.scanfire.imaging

import java.util.stream.IntStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-Kotlin filter maths over ARGB pixel arrays, kept free of android.graphics so it can be
 * unit-tested on the JVM. [Filters] wraps these for Bitmaps.
 */
object FilterKernels {

    /**
     * Runs [work] over rows [0, rows) split into bands, one per core. Bands only write their own
     * rows, so no locking is needed.
     */
    internal fun inBands(rows: Int, work: (Int, Int) -> Unit) {
        val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        if (cores == 1 || rows < 256) { work(0, rows); return }
        val band = (rows + cores - 1) / cores
        IntStream.range(0, cores).parallel().forEach { b ->
            val start = b * band
            val end = min(rows, start + band)
            if (start < end) work(start, end)
        }
    }

    fun luma(p: Int): Int {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun argb(r: Int, g: Int, b: Int) =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Values of one channel ([shift] 16 red, 8 green, 0 blue, -1 luminance) at two percentiles (0..1). */
    fun percentiles(px: IntArray, low: Double, high: Double, shift: Int = -1): Pair<Int, Int> {
        val hist = IntArray(256)
        // Sampling every few pixels is plenty for a histogram and keeps large pages fast.
        val step = max(1, px.size / 400_000)
        var n = 0
        var i = 0
        while (i < px.size) {
            hist[if (shift < 0) luma(px[i]) else (px[i] shr shift) and 0xFF]++
            n++; i += step
        }
        fun at(fraction: Double): Int {
            val target = (n * fraction).toLong()
            var acc = 0L
            for (v in 0..255) { acc += hist[v]; if (acc > target) return v }
            return 255
        }
        return at(low) to at(high)
    }

    /**
     * Auto-levels per channel: each of R, G and B is stretched from its 1st to 99.5th percentile,
     * which also neutralises a colour cast (yellow lamp light, blue shade). Then text is sharpened.
     */
    fun enhance(px: IntArray, w: Int, h: Int): IntArray {
        val luts = intArrayOf(16, 8, 0).map { shift ->
            val (lo, hi) = percentiles(px, 0.01, 0.995, shift)
            if (hi - lo < 16) IntArray(256) { it } else IntArray(256) { v -> ((v - lo) * 255.0 / (hi - lo)).roundToInt().coerceIn(0, 255) }
        }
        val out = IntArray(px.size) { i ->
            val p = px[i]
            argb(luts[0][(p shr 16) and 0xFF], luts[1][(p shr 8) and 0xFF], luts[2][p and 0xFF])
        }
        return sharpen(out, w, h, amount = 0.6)
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
     * The paper's colour across the page, per channel, on a coarse grid. Each cell takes the 90th
     * percentile of its pixels (paper is the bright majority, so ink barely moves it), cells are
     * dilated by their neighbours (cells packed with text still find paper), then smoothed so the
     * estimate has no visible block edges.
     */
    class PaperGrid internal constructor(val cols: Int, val rows: Int, val cell: Int, internal val channels: List<IntArray>) {
        /** Grid coordinate of pixel [p] along one axis: lower cell index and blend weight toward the next. */
        internal fun axis(p: Int, count: Int): Pair<Int, Float> {
            val f = ((p + 0.5f) / cell - 0.5f).coerceIn(0f, (count - 1).toFloat())
            return f.toInt() to (f - f.toInt())
        }

        /** Paper colour for every pixel of row [y], channel by channel, bilinearly interpolated. */
        fun row(y: Int, w: Int, xs: IntArray, txs: FloatArray, out: Array<FloatArray>) {
            val (y0, ty) = axis(y, rows)
            val y1 = min(rows - 1, y0 + 1)
            for (ch in 0..2) {
                val src = channels[ch]
                val o = out[ch]
                for (x in 0 until w) {
                    val x0 = xs[x]; val x1 = min(cols - 1, x0 + 1); val tx = txs[x]
                    val top = src[y0 * cols + x0] * (1 - tx) + src[y0 * cols + x1] * tx
                    val bottom = src[y1 * cols + x0] * (1 - tx) + src[y1 * cols + x1] * tx
                    o[x] = top * (1 - ty) + bottom * ty
                }
            }
        }
    }

    fun paperGrid(px: IntArray, w: Int, h: Int, cellsAcross: Int = 40): PaperGrid {
        val cell = max(8, max(w, h) / cellsAcross)
        val cols = (w + cell - 1) / cell
        val rows = (h + cell - 1) / cell
        val raw = Array(3) { IntArray(cols * rows) }
        val hist = Array(3) { IntArray(256) }
        for (cy in 0 until rows) for (cx in 0 until cols) {
            hist.forEach { it.fill(0) }
            var n = 0
            val y1 = min(h, (cy + 1) * cell); val x1 = min(w, (cx + 1) * cell)
            var y = cy * cell
            while (y < y1) {
                var x = cx * cell
                while (x < x1) {
                    val p = px[y * w + x]
                    hist[0][(p shr 16) and 0xFF]++; hist[1][(p shr 8) and 0xFF]++; hist[2][p and 0xFF]++
                    n++; x += 2
                }
                y += 2
            }
            val target = (n * 0.9).toInt()
            for (ch in 0..2) {
                var acc = 0; var v = 0
                while (v < 255) { acc += hist[ch][v]; if (acc > target) break; v++ }
                raw[ch][cy * cols + cx] = if (n == 0) 255 else v
            }
        }
        fun neighbourhood(src: IntArray, reduce: (IntArray, Int) -> Int): IntArray {
            val window = IntArray(9)
            return IntArray(src.size) { idx ->
                val cx = idx % cols; val cy = idx / cols
                var k = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = (cx + dx).coerceIn(0, cols - 1); val ny = (cy + dy).coerceIn(0, rows - 1)
                    window[k++] = src[ny * cols + nx]
                }
                reduce(window, k)
            }
        }
        val smoothed = raw.map { ch ->
            val dilated = neighbourhood(ch) { win, k -> (0 until k).maxOf { win[it] } }
            neighbourhood(dilated) { win, k -> (0 until k).sumOf { win[it] } / k }
        }
        return PaperGrid(cols, rows, cell, smoothed)
    }

    /**
     * "Magic colour": divides each channel by the estimated paper colour, so shadows, gradients and
     * yellowed or tinted paper all go white, keeps ink colours (with a saturation boost), pushes
     * near-white to pure white, and sharpens text.
     */
    fun magic(px: IntArray, w: Int, h: Int): IntArray {
        val grid = paperGrid(px, w, h)
        val xs = IntArray(w); val txs = FloatArray(w)
        for (x in 0 until w) grid.axis(x, grid.cols).let { (c, t) -> xs[x] = c; txs[x] = t }
        val lut = IntArray(256 * 4) { tone(it / 4.0) } // tone() sampled at quarter steps of 0..255
        val out = IntArray(px.size)
        inBands(h) { y0, y1 ->
            val bg = Array(3) { FloatArray(w) }
            for (y in y0 until y1) {
                grid.row(y, w, xs, txs, bg)
                val row = y * w
                for (x in 0 until w) {
                    val p = px[row + x]
                    // Floor the divisor so very dark regions (photos, a dark table edge) aren't blown out.
                    var r = ((p shr 16) and 0xFF) * 255f / max(64f, bg[0][x])
                    var g = ((p shr 8) and 0xFF) * 255f / max(64f, bg[1][x])
                    var b = (p and 0xFF) * 255f / max(64f, bg[2][x])
                    val l = r * 0.299f + g * 0.587f + b * 0.114f
                    r = l + (r - l) * 1.3f; g = l + (g - l) * 1.3f; b = l + (b - l) * 1.3f
                    out[row + x] = argb(toneOf(lut, r), toneOf(lut, g), toneOf(lut, b))
                }
            }
        }
        // A higher threshold than Enhance: after whitening, small wobbles are creases and grain, not text.
        return sharpen(out, w, h, amount = 0.8, threshold = 6)
    }

    /** Normalised brightness at and above which a pixel counts as paper and becomes pure white. */
    private const val WHITE_POINT = 0.85

    private fun toneOf(lut: IntArray, v: Float): Int = lut[(v * 4).toInt().coerceIn(0, lut.size - 1)]

    /** Paper (at or above [WHITE_POINT]) becomes pure white; below that a gentle S-curve deepens ink without crushing it. */
    private fun tone(v: Double): Int {
        val n = (v / 255.0).coerceIn(0.0, 1.0)
        if (n >= WHITE_POINT) return 255
        val t = n / WHITE_POINT
        val s = t * t * (3 - 2 * t)
        return ((s * 0.65 + t * 0.35) * 255).roundToInt()
    }

    /**
     * Unsharp mask on luminance only (no colour fringes): every channel moves by
     * amount x (luma - blurred luma). Differences under [threshold] are left alone so paper grain
     * and JPEG noise don't get amplified. Radius scales with the page so it suits any resolution.
     */
    fun sharpen(px: IntArray, w: Int, h: Int, amount: Double, threshold: Int = 3): IntArray {
        if (w < 8 || h < 8) return px
        val radius = max(1, max(w, h) / 1400)
        val lum = IntArray(px.size)
        inBands(h) { y0, y1 -> for (i in y0 * w until y1 * w) lum[i] = luma(px[i]) }
        // Horizontal running sums, edge-replicated.
        val horizontal = IntArray(px.size)
        inBands(h) { y0, y1 ->
            for (y in y0 until y1) {
                val row = y * w
                var sum = 0
                for (k in -radius..radius) sum += lum[row + k.coerceIn(0, w - 1)]
                for (x in 0 until w) {
                    horizontal[row + x] = sum
                    sum += lum[row + min(w - 1, x + radius + 1)] - lum[row + max(0, x - radius)]
                }
            }
        }
        val area = (2 * radius + 1) * (2 * radius + 1)
        val out = px.copyOf()
        // Vertical running sums kept per column while walking rows, so memory is read in order.
        inBands(h) { y0, y1 ->
            val column = IntArray(w)
            for (k in -radius..radius) {
                val row = (y0 + k).coerceIn(0, h - 1) * w
                for (x in 0 until w) column[x] += horizontal[row + x]
            }
            for (y in y0 until y1) {
                val row = y * w
                for (x in 0 until w) {
                    val i = row + x
                    val diff = lum[i] - column[x] / area
                    if (abs(diff) >= threshold) {
                        val d = (diff * amount).roundToInt()
                        val p = px[i]
                        out[i] = argb(((p shr 16) and 0xFF) + d, ((p shr 8) and 0xFF) + d, (p and 0xFF) + d)
                    }
                }
                val add = min(h - 1, y + radius + 1) * w
                val drop = max(0, y - radius) * w
                for (x in 0 until w) column[x] += horizontal[add + x] - horizontal[drop + x]
            }
        }
        return out
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

package com.ganim.scanfire.imaging

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Corners of a page in an image, normalised to 0..1, in order top-left, top-right, bottom-right,
 * bottom-left. Stored as x0,y0,x1,y1,x2,y2,x3,y3.
 */
data class Quad(val points: FloatArray) {
    init { require(points.size == 8) }

    fun x(i: Int) = points[i * 2]
    fun y(i: Int) = points[i * 2 + 1]

    fun moved(i: Int, x: Float, y: Float) = Quad(points.copyOf().also { it[i * 2] = x.coerceIn(0f, 1f); it[i * 2 + 1] = y.coerceIn(0f, 1f) })

    val isFull: Boolean get() = this == FULL

    /** Area as a fraction of the image (shoelace formula). */
    fun area(): Float {
        var s = 0f
        for (i in 0 until 4) { val j = (i + 1) % 4; s += x(i) * y(j) - x(j) * y(i) }
        return abs(s) / 2f
    }

    /** True when the four corners form a convex shape in clockwise order (what a page looks like). */
    fun isConvex(): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val a = i; val b = (i + 1) % 4; val c = (i + 2) % 4
            val cross = (x(b) - x(a)) * (y(c) - y(b)) - (y(b) - y(a)) * (x(c) - x(b))
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s == 0) return false
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    override fun equals(other: Any?) = other is Quad && points.contentEquals(other.points)
    override fun hashCode() = points.contentHashCode()

    companion object {
        val FULL = Quad(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))
    }
}

/**
 * Finds the sheet of paper in a photo, without ML: the paper is whatever differs most from the
 * colour around the photo's edges (the table, a bedspread...). Pure Kotlin so it is unit-tested.
 *
 * 1. Work on a ~400 px copy, lightly blurred.
 * 2. Background colour = mean of a thin band along the photo's border.
 * 3. Each pixel's colour distance from that background, split by Otsu's threshold.
 * 4. Close small gaps (text, shadows on the paper), keep the biggest blob near the centre.
 * 5. Corners = extreme points along the two diagonals.
 * Returns null when nothing page-like is found, so the caller can fall back to the full photo.
 */
object PageDetector {
    private const val WORK_EDGE = 400

    fun detect(px: IntArray, w: Int, h: Int): Quad? {
        if (w < 16 || h < 16) return null
        val scale = min(1f, WORK_EDGE.toFloat() / max(w, h))
        val sw = max(8, (w * scale).toInt())
        val sh = max(8, (h * scale).toInt())
        val small = downsample(px, w, h, sw, sh)
        val blurred = boxBlur(boxBlur(small, sw, sh), sw, sh)

        // Background colour from the border band.
        val band = max(2, min(sw, sh) / 25)
        var br = 0L; var bg = 0L; var bb = 0L; var n = 0
        for (y in 0 until sh) for (x in 0 until sw) {
            if (x < band || y < band || x >= sw - band || y >= sh - band) {
                val p = blurred[y * sw + x]
                br += (p shr 16) and 0xFF; bg += (p shr 8) and 0xFF; bb += p and 0xFF; n++
            }
        }
        val mr = br / n.toFloat(); val mg = bg / n.toFloat(); val mb = bb / n.toFloat()
        val borderLuma = 0.299f * mr + 0.587f * mg + 0.114f * mb

        // Colour distance from the background, plus a bonus for being brighter (paper is usually lighter).
        val dist = IntArray(sw * sh) { i ->
            val p = blurred[i]
            val r = ((p shr 16) and 0xFF) - mr; val g = ((p shr 8) and 0xFF) - mg; val b = (p and 0xFF) - mb
            val l = 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
            min(255f, sqrt(r * r + g * g + b * b) * 0.7f + max(0f, l - borderLuma) * 0.6f).toInt()
        }
        val threshold = otsu(dist)
        if (threshold < 12) return null // the photo is almost uniform: no clear page edge
        var mask = BooleanArray(sw * sh) { dist[it] > threshold }
        val r = max(1, min(sw, sh) / 60)
        mask = erode(dilate(mask, sw, sh, r), sw, sh, r)

        val blob = largestBlob(mask, sw, sh) ?: return null
        if (blob.size < sw * sh * 0.12) return null

        // Extreme points along the diagonals give the four corners, even for a tilted page.
        var tl = blob[0]; var tr = blob[0]; var brc = blob[0]; var bl = blob[0]
        fun sx(i: Int) = i % sw
        fun sy(i: Int) = i / sw
        for (i in blob) {
            val s = sx(i) + sy(i); val d = sx(i) - sy(i)
            if (s < sx(tl) + sy(tl)) tl = i
            if (s > sx(brc) + sy(brc)) brc = i
            if (d > sx(tr) - sy(tr)) tr = i
            if (d < sx(bl) - sy(bl)) bl = i
        }
        fun nx(i: Int) = (sx(i) + 0.5f) / sw
        fun ny(i: Int) = (sy(i) + 0.5f) / sh
        val quad = Quad(floatArrayOf(nx(tl), ny(tl), nx(tr), ny(tr), nx(brc), ny(brc), nx(bl), ny(bl)))
        // Reject shapes that can't be a page, or that are just the whole photo again.
        if (!quad.isConvex() || quad.area() < 0.12f || quad.area() > 0.97f) return null
        return quad
    }

    private fun downsample(px: IntArray, w: Int, h: Int, sw: Int, sh: Int): IntArray = IntArray(sw * sh) { i ->
        // Average a small block per output pixel, so fine texture doesn't alias into edges.
        val ox = i % sw; val oy = i / sw
        val x0 = ox * w / sw; val x1 = max(x0 + 1, (ox + 1) * w / sw)
        val y0 = oy * h / sh; val y1 = max(y0 + 1, (oy + 1) * h / sh)
        var r = 0; var g = 0; var b = 0; var c = 0
        val stepX = max(1, (x1 - x0) / 3); val stepY = max(1, (y1 - y0) / 3)
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val p = px[y * w + x]
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; c++
                x += stepX
            }
            y += stepY
        }
        (0xFF shl 24) or ((r / c) shl 16) or ((g / c) shl 8) or (b / c)
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        var r = 0; var g = 0; var b = 0; var c = 0
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx; val ny = y + dy
            if (nx in 0 until w && ny in 0 until h) {
                val p = px[ny * w + nx]
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; c++
            }
        }
        (0xFF shl 24) or ((r / c) shl 16) or ((g / c) shl 8) or (b / c)
    }

    /** Otsu's method: the threshold that best separates the histogram into two classes. */
    fun otsu(values: IntArray): Int {
        val hist = IntArray(256)
        values.forEach { hist[it.coerceIn(0, 255)]++ }
        val total = values.size.toDouble()
        val sumAll = (0..255).sumOf { it.toDouble() * hist[it] }
        var sumB = 0.0; var wB = 0.0; var best = 0.0; var threshold = 0
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB; val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; threshold = t }
        }
        return threshold
    }

    private fun dilate(m: BooleanArray, w: Int, h: Int, r: Int) = morph(m, w, h, r, true)
    private fun erode(m: BooleanArray, w: Int, h: Int, r: Int) = morph(m, w, h, r, false)

    /** Square-window dilation (any set pixel nearby) or erosion (all pixels nearby set). */
    private fun morph(m: BooleanArray, w: Int, h: Int, r: Int, dilate: Boolean): BooleanArray {
        // Separable: rows then columns.
        val rows = BooleanArray(m.size)
        for (y in 0 until h) for (x in 0 until w) {
            var v = !dilate
            for (k in -r..r) {
                val nx = (x + k).coerceIn(0, w - 1)
                if (dilate && m[y * w + nx]) { v = true; break }
                if (!dilate && !m[y * w + nx]) { v = false; break }
            }
            rows[y * w + x] = v
        }
        val out = BooleanArray(m.size)
        for (y in 0 until h) for (x in 0 until w) {
            var v = !dilate
            for (k in -r..r) {
                val ny = (y + k).coerceIn(0, h - 1)
                if (dilate && rows[ny * w + x]) { v = true; break }
                if (!dilate && !rows[ny * w + x]) { v = false; break }
            }
            out[y * w + x] = v
        }
        return out
    }

    /** Pixel indices of the largest 4-connected blob, preferring one that covers the centre. */
    private fun largestBlob(mask: BooleanArray, w: Int, h: Int): IntArray? {
        val label = IntArray(mask.size)
        var best: IntArray? = null
        var bestScore = 0.0
        val centre = (h / 2) * w + w / 2
        val queue = IntArray(mask.size)
        var next = 1
        for (start in mask.indices) {
            if (!mask[start] || label[start] != 0) continue
            var head = 0; var tail = 0
            queue[tail++] = start; label[start] = next
            while (head < tail) {
                val i = queue[head++]
                val x = i % w; val y = i / w
                if (x > 0 && mask[i - 1] && label[i - 1] == 0) { label[i - 1] = next; queue[tail++] = i - 1 }
                if (x < w - 1 && mask[i + 1] && label[i + 1] == 0) { label[i + 1] = next; queue[tail++] = i + 1 }
                if (y > 0 && mask[i - w] && label[i - w] == 0) { label[i - w] = next; queue[tail++] = i - w }
                if (y < h - 1 && mask[i + w] && label[i + w] == 0) { label[i + w] = next; queue[tail++] = i + w }
            }
            val score = tail * (if (label[centre] == next) 1.5 else 1.0)
            if (score > bestScore) { bestScore = score; best = queue.copyOf(tail) }
            next++
        }
        return best
    }
}

/** Perspective correction: maps the quad in the source onto an upright rectangle. */
object Perspective {
    /**
     * Output size for a quad given in source pixels: the longer of each pair of opposite sides,
     * capped at [maxEdge] on the long side.
     */
    fun outputSize(q: FloatArray, maxEdge: Int): Pair<Int, Int> {
        fun len(a: Int, b: Int) = sqrt((q[a * 2] - q[b * 2]).let { it * it } + (q[a * 2 + 1] - q[b * 2 + 1]).let { it * it })
        var w = max(len(0, 1), len(3, 2))
        var h = max(len(0, 3), len(1, 2))
        val s = min(1f, maxEdge / max(w, h))
        w *= s; h *= s
        return max(1, w.toInt()) to max(1, h.toInt())
    }

    /**
     * Warps [px] (w x h) so the quad [q] (source pixels, TL TR BR BL) fills an outW x outH image.
     * Uses Heckbert's closed-form unit-square-to-quad mapping and bilinear sampling.
     */
    fun warp(px: IntArray, w: Int, h: Int, q: FloatArray, outW: Int, outH: Int): IntArray {
        val x0 = q[0]; val y0 = q[1]; val x1 = q[2]; val y1 = q[3]; val x2 = q[4]; val y2 = q[5]; val x3 = q[6]; val y3 = q[7]
        val dx1 = x1 - x2; val dx2 = x3 - x2; val dx3 = x0 - x1 + x2 - x3
        val dy1 = y1 - y2; val dy2 = y3 - y2; val dy3 = y0 - y1 + y2 - y3
        val a: Float; val b: Float; val d: Float; val e: Float; val g: Float; val hh: Float
        if (abs(dx3) < 1e-6f && abs(dy3) < 1e-6f) {
            a = x1 - x0; b = x2 - x1; d = y1 - y0; e = y2 - y1; g = 0f; hh = 0f
        } else {
            val den = dx1 * dy2 - dx2 * dy1
            g = (dx3 * dy2 - dx2 * dy3) / den
            hh = (dx1 * dy3 - dx3 * dy1) / den
            a = x1 - x0 + g * x1; b = x3 - x0 + hh * x3
            d = y1 - y0 + g * y1; e = y3 - y0 + hh * y3
        }
        val out = IntArray(outW * outH)
        FilterKernels.inBands(outH) { r0, r1 ->
            for (oy in r0 until r1) {
                val v = (oy + 0.5f) / outH
                for (ox in 0 until outW) {
                    val u = (ox + 0.5f) / outW
                    val z = g * u + hh * v + 1f
                    val sx = ((a * u + b * v + x0) / z - 0.5f).coerceIn(0f, w - 1f)
                    val sy = ((d * u + e * v + y0) / z - 0.5f).coerceIn(0f, h - 1f)
                    out[oy * outW + ox] = bilinear(px, w, h, sx, sy)
                }
            }
        }
        return out
    }

    private fun bilinear(px: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val xi = x.toInt(); val yi = y.toInt()
        val x1 = min(w - 1, xi + 1); val y1 = min(h - 1, yi + 1)
        val tx = x - xi; val ty = y - yi
        val p00 = px[yi * w + xi]; val p10 = px[yi * w + x1]; val p01 = px[y1 * w + xi]; val p11 = px[y1 * w + x1]
        fun ch(shift: Int): Int {
            val c00 = (p00 shr shift) and 0xFF; val c10 = (p10 shr shift) and 0xFF
            val c01 = (p01 shr shift) and 0xFF; val c11 = (p11 shr shift) and 0xFF
            val top = c00 + (c10 - c00) * tx
            val bottom = c01 + (c11 - c01) * tx
            return (top + (bottom - top) * ty).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

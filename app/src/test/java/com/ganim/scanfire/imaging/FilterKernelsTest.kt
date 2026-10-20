package com.ganim.scanfire.imaging

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

class FilterKernelsTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun gray(v: Int) = rgb(v, v, v)
    private fun lumaAt(px: IntArray, i: Int) = FilterKernels.luma(px[i])
    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF

    /** Paper darkening from left (230) to right (130), with one dark "ink" column at x = 30. */
    private fun shadedPage(w: Int = 64, h: Int = 64) = IntArray(w * h) { i ->
        val x = i % w
        val paper = 230 - x * 100 / w
        if (x == 30) gray(paper - 90) else gray(paper)
    }

    /**
     * Yellowed paper (240, 225, 170) under a shadow that darkens toward the bottom right, with
     * blue ink strokes every 12 px: roughly what a phone photo of an old receipt under a lamp looks like.
     */
    private fun yellowShadowedPage(w: Int = 240, h: Int = 320) = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        val shade = 1.0 - 0.45 * (x + y) / (w + h)
        val ink = (y % 12 in 5..6) && x in 20..220
        if (ink) rgb((40 * shade).toInt(), (50 * shade).toInt(), (140 * shade).toInt())
        else rgb((240 * shade).toInt(), (225 * shade).toInt(), (170 * shade).toInt())
    }

    @Test
    fun `enhance stretches a dull range to full contrast`() {
        val px = IntArray(1000) { gray(100 + it % 50) } // values 100..149
        val out = FilterKernels.enhance(px, 40, 25)
        out.minOf { FilterKernels.luma(it) } shouldBeLessThan 15
        out.maxOf { FilterKernels.luma(it) } shouldBeGreaterThan 240
    }

    @Test
    fun `enhance neutralises a colour cast`() {
        // A bluish photo of white paper with grey text.
        val px = IntArray(4000) { if (it % 7 == 0) rgb(60, 70, 110) else rgb(190, 200, 245) }
        val paper = FilterKernels.enhance(px, 80, 50).first { FilterKernels.luma(it) > 200 }
        abs(r(paper) - b(paper)) shouldBeLessThan 20
    }

    @Test
    fun `black and white keeps ink in shadow and clears shaded paper`() {
        val w = 64
        val out = FilterKernels.blackWhite(shadedPage(w), w, 64)
        val row = 32 * w
        lumaAt(out, row + 30) shouldBe 0     // ink
        lumaAt(out, row + 5) shouldBe 255    // bright paper
        lumaAt(out, row + 60) shouldBe 255   // shaded paper must not turn black
    }

    @Test
    fun `magic colour whitens shaded paper but keeps ink dark`() {
        val w = 64
        val out = FilterKernels.magic(shadedPage(w), w, 64)
        val row = 32 * w
        lumaAt(out, row + 58) shouldBeGreaterThan 235
        lumaAt(out, row + 30) shouldBeLessThan 140
    }

    @Test
    fun `magic colour turns yellowed shadowed paper white and keeps blue ink blue`() {
        val w = 240; val h = 320
        val out = FilterKernels.magic(yellowShadowedPage(w, h), w, h)
        // Paper in the darkest corner (bottom right) and the brightest (top left).
        for ((x, y) in listOf(10 to 10, 230 to 310, 120 to 163)) {
            val p = out[y * w + x]
            r(p) shouldBeGreaterThan 235; g(p) shouldBeGreaterThan 235; b(p) shouldBeGreaterThan 235
        }
        // Ink in the shadow is still clearly ink, and still blue.
        val ink = out[305 * w + 200]
        FilterKernels.luma(ink) shouldBeLessThan 120
        b(ink) shouldBeGreaterThan r(ink) + 30
    }

    @Test
    fun `sharpen raises contrast at an edge and leaves flat areas alone`() {
        val w = 40; val h = 20
        val px = IntArray(w * h) { if (it % w < 20) gray(100) else gray(200) }
        val out = FilterKernels.sharpen(px, w, h, amount = 1.0)
        val row = 10 * w
        lumaAt(out, row + 19) shouldBeLessThan 100     // dark side of the edge gets darker
        lumaAt(out, row + 20) shouldBeGreaterThan 200  // bright side gets brighter
        lumaAt(out, row + 5) shouldBe 100              // flat region unchanged
        lumaAt(out, row + 35) shouldBe 200
    }

    @Test
    fun `parallel bands leave no seams`() {
        // Tall enough to be split across cores; every row is the same, so every output row must be too.
        val w = 60; val h = 900
        val px = IntArray(w * h) { val x = it % w; if (x % 10 < 3) gray(40) else gray(200 - x) }
        for (out in listOf(FilterKernels.sharpen(px, w, h, amount = 0.8), FilterKernels.magic(px, w, h))) {
            // Compare against row 5: the very top and bottom rows legitimately differ (edge replication).
            val reference = out.copyOfRange(5 * w, 6 * w).toList()
            for (y in 5 until h - 5) out.copyOfRange(y * w, (y + 1) * w).toList() shouldBe reference
        }
    }

    @Test
    fun `grayscale removes colour`() {
        val red = rgb(200, 30, 30)
        val out = FilterKernels.grayscale(IntArray(10) { if (it % 2 == 0) red else gray(250) })
        r(out[0]) shouldBe b(out[0])
    }
}

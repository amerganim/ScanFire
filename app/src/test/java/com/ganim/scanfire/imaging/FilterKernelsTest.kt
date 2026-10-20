package com.ganim.scanfire.imaging

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FilterKernelsTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun lumaAt(px: IntArray, i: Int) = FilterKernels.luma(px[i])

    /** A page whose paper darkens from left (230) to right (130), with one dark "ink" column at x = 30. */
    private fun shadedPage(w: Int = 64, h: Int = 64) = IntArray(w * h) { i ->
        val x = i % w
        val paper = 230 - x * 100 / w
        if (x == 30) gray(paper - 90) else gray(paper)
    }

    @Test
    fun `enhance stretches a dull range to full contrast`() {
        val px = IntArray(1000) { gray(100 + it % 50) } // values 100..149
        val out = FilterKernels.enhance(px)
        out.minOf { FilterKernels.luma(it) } shouldBeLessThan 10
        out.maxOf { FilterKernels.luma(it) } shouldBeGreaterThan 245
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
        lumaAt(out, row + 58) shouldBeGreaterThan 230
        lumaAt(out, row + 30) shouldBeLessThan 140
    }

    @Test
    fun `grayscale removes colour`() {
        val red = (0xFF shl 24) or (200 shl 16) or (30 shl 8) or 30
        val out = FilterKernels.grayscale(IntArray(10) { if (it % 2 == 0) red else gray(250) })
        val p = out[0]
        ((p shr 16) and 0xFF) shouldBe (p and 0xFF)
    }
}

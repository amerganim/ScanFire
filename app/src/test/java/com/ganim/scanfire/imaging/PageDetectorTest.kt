package com.ganim.scanfire.imaging

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PageDetectorTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** True when (x, y) is inside the convex quad [c] (pixel coords, TL TR BR BL). */
    private fun inside(c: FloatArray, x: Float, y: Float): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val cross = (c[j * 2] - c[i * 2]) * (y - c[i * 2 + 1]) - (c[j * 2 + 1] - c[i * 2 + 1]) * (x - c[i * 2])
            val s = if (cross >= 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /** A page (with some "text" lines) drawn as quad [c] onto a background. */
    private fun photo(w: Int, h: Int, c: FloatArray, background: Int, paper: Int) = IntArray(w * h) { i ->
        val x = i % w + 0.5f; val y = i / w + 0.5f
        when {
            !inside(c, x, y) -> background
            (i / w) % 20 < 3 && (i % w) % 50 < 35 -> rgb(30, 30, 30)
            else -> paper
        }
    }

    private fun assertCorners(found: Quad, expected: FloatArray, w: Int, h: Int, tolerance: Float = 0.03f) {
        for (k in 0 until 4) {
            found.x(k) shouldBe (expected[k * 2] / w plusOrMinus tolerance)
            found.y(k) shouldBe (expected[k * 2 + 1] / h plusOrMinus tolerance)
        }
    }

    @Test
    fun `finds a tilted white page on a dark table`() {
        val w = 600; val h = 800
        val corners = floatArrayOf(140f, 90f, 500f, 150f, 450f, 700f, 80f, 640f)
        val found = PageDetector.detect(photo(w, h, corners, rgb(70, 55, 45), rgb(235, 232, 225)), w, h)
        found.shouldNotBeNull()
        assertCorners(found, corners, w, h)
    }

    @Test
    fun `finds a page on a light grey surface`() {
        val w = 800; val h = 600
        val corners = floatArrayOf(100f, 80f, 700f, 70f, 720f, 540f, 90f, 520f)
        val found = PageDetector.detect(photo(w, h, corners, rgb(170, 175, 180), rgb(250, 250, 248)), w, h)
        found.shouldNotBeNull()
        assertCorners(found, corners, w, h)
    }

    @Test
    fun `uniform photo has no page`() {
        PageDetector.detect(IntArray(300 * 300) { rgb(120, 120, 120) }, 300, 300).shouldBeNull()
    }

    @Test
    fun `warp straightens the quad into the output rectangle`() {
        // A source where the left half is red and the right half blue, warped from a tilted quad
        // inside it: the output's left edge must be red and right edge blue.
        val w = 200; val h = 200
        val px = IntArray(w * h) { if (it % w < 100) rgb(255, 0, 0) else rgb(0, 0, 255) }
        val q = floatArrayOf(30f, 20f, 170f, 40f, 160f, 180f, 40f, 170f)
        val (ow, oh) = Perspective.outputSize(q, 1000)
        val out = Perspective.warp(px, w, h, q, ow, oh)
        ((out[oh / 2 * ow + 2] shr 16) and 0xFF) shouldBe 255
        (out[oh / 2 * ow + ow - 3] and 0xFF) shouldBe 255
    }

    @Test
    fun `quad helpers`() {
        Quad.FULL.area() shouldBe (1f plusOrMinus 0.0001f)
        Quad.FULL.isConvex() shouldBe true
        // Bow-tie (corners out of order) is not a page.
        Quad(floatArrayOf(0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f)).isConvex() shouldBe false
    }
}

package com.ganim.scanfire.export

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExporterLayoutTest {
    @Test
    fun `portrait image on A4 stays portrait`() {
        val box = Exporter.pageBox("a4", 1000, 1414)
        box.width shouldBe 595f
        box.height shouldBe 842f
    }

    @Test
    fun `landscape image turns the page landscape`() {
        val box = Exporter.pageBox("letter", 2000, 1000)
        box.width shouldBe 792f
        box.height shouldBe 612f
    }

    @Test
    fun `fit page follows the image aspect`() {
        val box = Exporter.pageBox("fit", 1000, 2000)
        box.height shouldBe 842f
        box.width shouldBe (421f plusOrMinus 0.5f)
    }

    @Test
    fun `image is centred inside the margins`() {
        val (x, y, w, h) = Exporter.fitRect(595f, 842f, 1000f, 1000f, 24f).toList()
        w shouldBe (547f plusOrMinus 0.01f)
        h shouldBe w
        x shouldBe (24f plusOrMinus 0.01f)
        y shouldBe ((842f - w) / 2 plusOrMinus 0.01f)
    }
}

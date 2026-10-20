package com.ganim.scanfire.pdf

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import org.junit.jupiter.api.Test

class PageRangesTest {
    @Test
    fun `singles and ranges in the order given`() {
        PageRanges.parse("3, 1-2", 5)!! shouldContainExactly listOf(2, 0, 1)
    }

    @Test
    fun `open ended ranges reach the ends`() {
        PageRanges.parse("4-", 6)!! shouldContainExactly listOf(3, 4, 5)
        PageRanges.parse("-2", 6)!! shouldContainExactly listOf(0, 1)
    }

    @Test
    fun `reversed range runs backwards and duplicates collapse`() {
        PageRanges.parse("3-1, 2", 3)!! shouldContainExactly listOf(2, 1, 0)
    }

    @Test
    fun `out of range or junk is rejected`() {
        PageRanges.parse("0", 3).shouldBeNull()
        PageRanges.parse("2-9", 3).shouldBeNull()
        PageRanges.parse("a", 3).shouldBeNull()
        PageRanges.parse("1-2-3", 3).shouldBeNull()
        PageRanges.parse(" ", 3).shouldBeNull()
    }
}

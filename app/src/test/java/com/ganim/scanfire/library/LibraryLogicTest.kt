package com.ganim.scanfire.library

import com.ganim.scanfire.data.SortOrder
import com.ganim.scanfire.data.db.DocumentSummary
import com.ganim.scanfire.ui.common.safeFileName
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LibraryLogicTest {
    private fun doc(id: Long, name: String, created: Long, updated: Long) =
        DocumentSummary(id, name, null, created, updated, 1, null, null)

    private val docs = listOf(doc(1, "beta", 10, 300), doc(2, "Alpha", 30, 100), doc(3, "gamma", 20, 200))

    @Test
    fun `sort by last modified puts newest first`() {
        docs.sortedWith(SortOrder.Updated.comparator()).map { it.id } shouldContainExactly listOf(1L, 3L, 2L)
    }

    @Test
    fun `sort by created puts newest first`() {
        docs.sortedWith(SortOrder.Created.comparator()).map { it.id } shouldContainExactly listOf(2L, 3L, 1L)
    }

    @Test
    fun `sort by name ignores case`() {
        docs.sortedWith(SortOrder.Name.comparator()).map { it.name } shouldContainExactly listOf("Alpha", "beta", "gamma")
    }

    @Test
    fun `file names lose illegal characters`() {
        safeFileName("Tax: 2026/Q1 \"final\"?") shouldBe "Tax_ 2026_Q1 _final__"
        safeFileName("   ") shouldBe "ScanFire"
    }
}

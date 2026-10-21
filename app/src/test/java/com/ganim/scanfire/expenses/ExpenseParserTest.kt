package com.ganim.scanfire.expenses

import com.ganim.scanfire.data.db.ExpenseEntity
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.util.Calendar

class ExpenseParserTest {
    private fun day(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d) }.timeInMillis

    @Test
    fun `reads amount currency and date from a receipt`() {
        val text = "Shwapno Super Shop\nDate: 12/10/2026\nRice 5kg 450.00\nOil 800.00\nTotal ৳ 1,250.00\nThank you"
        val g = ExpenseParser.guess(text, fallbackCurrency = "USD")
        g.amountMinor shouldBe 125000
        g.currency shouldBe "BDT"
        g.date shouldBe day(2026, 10, 12)
        g.title shouldBe "Shwapno Super Shop"
    }

    @Test
    fun `falls back to the local currency and leaves unknowns empty`() {
        val g = ExpenseParser.guess("Some note without numbers", fallbackCurrency = "EUR")
        g.amountMinor.shouldBeNull()
        g.date.shouldBeNull()
        g.currency shouldBe "EUR"
    }

    @Test
    fun `minor units are exact`() {
        ExpenseParser.toMinor("1,250.5") shouldBe 125050
        ExpenseParser.toMinor("৪৫০") shouldBe 45000
        ExpenseParser.toMinor("12.345").shouldBeNull()
        ExpenseParser.toMinor("abc").shouldBeNull()
    }

    @Test
    fun `groups by month with totals per currency`() {
        val items = listOf(
            ExpenseEntity(1, null, 1000, "BDT", day(2026, 10, 2), "a"),
            ExpenseEntity(2, null, 2550, "BDT", day(2026, 10, 20), "b"),
            ExpenseEntity(3, null, 999, "USD", day(2026, 10, 21), "c"),
            ExpenseEntity(4, null, 500, "BDT", day(2026, 9, 30), "d"),
        )
        val months = ExpenseParser.byMonth(items)
        months shouldHaveSize 2
        months[0].month shouldBe Calendar.OCTOBER
        months[0].totals shouldBe mapOf("BDT" to 3550L, "USD" to 999L)
        months[1].totals shouldBe mapOf("BDT" to 500L)
    }

    @Test
    fun `csv quotes commas and defuses formulas`() {
        val csv = ExpenseParser.csv(listOf(ExpenseEntity(1, null, 125050, "BDT", day(2026, 10, 12), "Rice, oil", "=HYPERLINK()")))
        csv shouldContain "2026-10-12,\"Rice, oil\",'=HYPERLINK(),1250.50,BDT"
    }
}

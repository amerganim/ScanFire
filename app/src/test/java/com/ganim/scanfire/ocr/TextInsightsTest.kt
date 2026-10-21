package com.ganim.scanfire.ocr

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.Locale

class TextInsightsTest {
    private val bengali = "উত্তর: দেশের জন্য যুদ্ধ করে যারা মারা যান তাঁরা শহিদ"
    private val english = "Customer Form Name: Test Person Phone: Date: 20/10/2026"

    @Test
    fun `readability prefers the script the page is written in`() {
        // Latin OCR on a Bengali page gives junk; Bengali OCR gives words.
        val latinOnBengali = "STUw Sl GTI Bie ef tA"
        TextInsights.readability(bengali, Character.UnicodeScript.BENGALI) shouldBeGreaterThan
            TextInsights.readability(latinOnBengali, Character.UnicodeScript.LATIN)
        // Bengali OCR on an English page reads the English through "+eng", so it doesn't win on Latin text alone.
        TextInsights.readability(english, Character.UnicodeScript.LATIN) shouldBe TextInsights.readability(english, Character.UnicodeScript.BENGALI)
        TextInsights.readability("51771 সু", Character.UnicodeScript.BENGALI) shouldBe 0
    }

    @Test
    fun `dates in several formats and digit systems`() {
        fun ymd(c: Calendar?) = c?.let { "%04d-%02d-%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }
        ymd(TextInsights.findDate("Date: 20/10/2026")) shouldBe "2026-10-20"
        ymd(TextInsights.findDate("Issued 2026-03-05")) shouldBe "2026-03-05"
        ymd(TextInsights.findDate("on 5 Mar 2026 at noon")) shouldBe "2026-03-05"
        ymd(TextInsights.findDate("March 5, 2026")) shouldBe "2026-03-05"
        ymd(TextInsights.findDate("তারিখ: ২০/১০/২০২৬")) shouldBe "2026-10-20"
        TextInsights.findDate("Total 1,250.00").shouldBeNull()
    }

    @Test
    fun `totals`() {
        TextInsights.findTotal("Subtotal 1,100.00\nVAT 150\nTotal: 1,250.00") shouldBe "1,250.00"
        TextInsights.findTotal("সর্বমোট ৳ ১২৫০") shouldBe "1250"
        TextInsights.findTotal("no money here").shouldBeNull()
    }

    @Test
    fun `name suggestions name the kind of document`() {
        val names = TextInsights.suggestNames("SHWAPNO SUPERSHOP\nCash Memo\nDate: 20/10/2026\nTotal: 1,250.00", 0L, Locale.UK)
        names.first() shouldBe "Receipt · 20 Oct 2026 · 1,250.00"
        names shouldContain "SHWAPNO SUPERSHOP"
        TextInsights.suggestNames("পাঠ ৫৪\nপ্রশ্ন- মুক্তিযোদ্ধা কারা?", 0L, Locale.UK).first() shouldStartWith "Notes · "
    }

    @Test
    fun `business card`() {
        val card = CardParser.parse(
            """
            ACME Technologies Ltd.
            Rahim Uddin
            Senior Software Engineer
            +880 1712-345678
            rahim.uddin@acme.com.bd
            www.acme.com.bd
            House 12, Road 5, Dhanmondi, Dhaka
            """.trimIndent(),
        )
        card.name shouldBe "Rahim Uddin"
        card.title shouldBe "Senior Software Engineer"
        card.company shouldBe "ACME Technologies Ltd."
        card.phones shouldContainExactly listOf("+880 1712-345678")
        card.emails shouldContainExactly listOf("rahim.uddin@acme.com.bd")
        card.websites shouldContainExactly listOf("www.acme.com.bd")
        card.address shouldBe "House 12, Road 5, Dhanmondi, Dhaka"
    }
}

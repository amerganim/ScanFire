package com.ganim.scanfire.privacy

import com.ganim.scanfire.ocr.OcrWord
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RedactorTest {
    /** Lays words out left to right on one line, 0.02 high, with small gaps. */
    private fun line(vararg texts: String, top: Float = 0.1f): List<OcrWord> {
        var x = 0.05f
        return texts.map { t ->
            val w = 0.012f * t.length
            OcrWord(t, x, top, x + w, top + 0.02f).also { x += w + 0.01f }
        }
    }

    @Test
    fun `phone number split across words is one span`() {
        val spans = Redactor.find(line("Call", "+880", "1712-345678", "today"))
        spans.map { it.kind } shouldContainExactly listOf(SensitiveKind.Phone)
        spans.single().words.map { it.text } shouldContainExactly listOf("+880", "1712-345678")
    }

    @Test
    fun `card number in four groups`() {
        val spans = Redactor.find(line("Card:", "4111", "1111", "1111", "1111"))
        spans.single().kind shouldBe SensitiveKind.LongNumber
        spans.single().words.size shouldBe 4
    }

    @Test
    fun `emails and links`() {
        val kinds = Redactor.find(line("Mail", "rahim.k@example.com", "or", "https://example.com/x")).map { it.kind }
        kinds shouldContainExactly listOf(SensitiveKind.Email, SensitiveKind.Link)
    }

    @Test
    fun `Bengali digits count as digits`() {
        // ০১৭১২৩৪৫৬৭৮ is an 11-digit mobile number written in Bengali numerals.
        Redactor.find(line("মোবাইল:", "০১৭১২-৩৪৫৬৭৮")).single().kind shouldBe SensitiveKind.Phone
    }

    @Test
    fun `national ID with letters and digits`() {
        Redactor.find(line("NID", "AB12345678")).single().kind shouldBe SensitiveKind.LongNumber
    }

    @Test
    fun `ordinary numbers are left alone`() {
        Redactor.find(line("Total", "1,250.00", "on", "20/10/2026", "page", "3")).shouldBeEmpty()
    }

    @Test
    fun `numbers on different lines are not joined`() {
        val words = line("1234", top = 0.1f) + line("5678", top = 0.3f)
        Redactor.find(words).shouldBeEmpty()
    }
}

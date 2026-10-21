package com.ganim.scanfire.privacy

import com.ganim.scanfire.ocr.OcrWord

enum class SensitiveKind(val label: String) {
    Email("Email"),
    Phone("Phone number"),
    LongNumber("Card / ID / account number"),
    Link("Web link"),
}

data class SensitiveSpan(val kind: SensitiveKind, val text: String, val words: List<OcrWord>)

/**
 * Finds personal data in OCR words so it can be blacked out before sharing. Pure Kotlin and
 * offline. Works on word boxes, joining neighbouring fragments because OCR splits
 * "+880 1712-345678" or "4111 1111 1111 1111" into several words. Any Unicode digits count
 * (Bengali ০-৯, Arabic-Indic, Devanagari...).
 */
object Redactor {
    private val email = Regex("""[\p{L}\p{N}._%+-]+@[\p{L}\p{N}.-]+\.\p{L}{2,}""")
    private val link = Regex("""(?i)^(https?://|www\.)\S+$""")
    /** Dates like 20/10/2026, 2026-10-20 or 20.10.26 look like long numbers but aren't personal data. */
    private val date = Regex("""^\p{Nd}{1,4}[/.\-]\p{Nd}{1,2}[/.\-]\p{Nd}{1,4}$""")

    /** Characters that can appear inside a phone/card/ID number run besides digits. */
    private fun isNumberPunct(c: Char) = c in "+-()./ "

    private fun digitCount(s: String) = s.count { Character.isDigit(it) }

    /** True when a word could be part of a number: mostly digits and number punctuation. */
    private fun isNumberish(s: String): Boolean {
        val t = s.trim().trimEnd(',', ';', ':')
        if (t.isEmpty()) return false
        val digits = digitCount(t)
        return digits > 0 && t.all { Character.isDigit(it) || isNumberPunct(it) } ||
            // IDs like "AB1234567": letters with a long digit body.
            (digits >= 6 && t.all { Character.isLetterOrDigit(it) || isNumberPunct(it) } && digits * 2 >= t.length)
    }

    fun find(words: List<OcrWord>): List<SensitiveSpan> {
        val spans = mutableListOf<SensitiveSpan>()
        var i = 0
        while (i < words.size) {
            val w = words[i]
            val text = w.text.trim()
            when {
                email.containsMatchIn(text) -> { spans += SensitiveSpan(SensitiveKind.Email, text, listOf(w)); i++ }
                link.matches(text) -> { spans += SensitiveSpan(SensitiveKind.Link, text, listOf(w)); i++ }
                isNumberish(text) -> {
                    // Grow a run of numberish words that sit on the same line, close together.
                    var j = i + 1
                    while (j < words.size && isNumberish(words[j].text) && sameLine(words[j - 1], words[j]) && close(words[j - 1], words[j])) j++
                    val run = words.subList(i, j)
                    val joined = run.joinToString(" ") { it.text.trim() }
                    val digits = digitCount(joined)
                    val kind = when {
                        date.matches(joined) -> null
                        // A leading + is an international phone number, whatever its length.
                        joined.startsWith("+") && digits >= 7 -> SensitiveKind.Phone
                        digits in 13..19 && run.size <= 5 -> SensitiveKind.LongNumber
                        digits >= 9 && (joined.contains('+') || joined.contains('-') || joined.contains('(') || run.size > 1) -> SensitiveKind.Phone
                        digits >= 8 -> SensitiveKind.LongNumber
                        digits >= 7 && joined.contains('+') -> SensitiveKind.Phone
                        else -> null
                    }
                    if (kind != null) spans += SensitiveSpan(kind, joined, run.toList())
                    i = j
                }
                else -> i++
            }
        }
        return spans
    }

    private fun sameLine(a: OcrWord, b: OcrWord): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return overlap > 0.5f * minOf(a.bottom - a.top, b.bottom - b.top)
    }

    /** Gap between words no wider than about two character heights. */
    private fun close(a: OcrWord, b: OcrWord): Boolean = b.left - a.right < 2.5f * (a.bottom - a.top)
}

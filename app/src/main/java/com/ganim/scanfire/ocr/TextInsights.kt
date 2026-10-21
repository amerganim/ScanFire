package com.ganim.scanfire.ocr

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Plain-Kotlin text analysis on OCR output: how "readable" a result is for a script (to pick the
 * OCR language automatically), and name suggestions from a page's content. Unit-tested on the JVM.
 */
object TextInsights {

    /** Letters of the given Unicode script in [text]. */
    fun lettersIn(text: String, script: Character.UnicodeScript): Int =
        text.codePoints().filter { Character.isLetter(it) && Character.UnicodeScript.of(it) == script }.count().toInt()

    /**
     * Score of an OCR result: real words count, garbage doesn't. Letters only count inside words of
     * 2+ letters, and words that mix scripts or are mostly symbols are ignored.
     */
    fun readability(text: String, script: Character.UnicodeScript): Int =
        text.split(Regex("""\s+""")).sumOf { raw ->
            val word = raw.trim { !Character.isLetterOrDigit(it) }
            val letters = word.codePoints().filter(Character::isLetter).toArray()
            if (letters.size < 2) return@sumOf 0
            val scripts = letters.map { Character.UnicodeScript.of(it) }.toSet()
            when {
                scripts.size > 1 && !(scripts.size == 2 && Character.UnicodeScript.LATIN in scripts) -> 0
                scripts.all { it == script || it == Character.UnicodeScript.LATIN } -> letters.size
                else -> 0
            }
        }

    /** The Unicode script a Tesseract language writes in, for scoring its output. */
    fun scriptOf(language: OcrLanguage): Character.UnicodeScript = when (language) {
        OcrLanguage.Auto -> Character.UnicodeScript.LATIN
        is OcrLanguage.MlKit -> when (language.script) {
            OcrScript.Latin -> Character.UnicodeScript.LATIN
            OcrScript.Devanagari -> Character.UnicodeScript.DEVANAGARI
            OcrScript.Chinese -> Character.UnicodeScript.HAN
            OcrScript.Japanese -> Character.UnicodeScript.HIRAGANA
            OcrScript.Korean -> Character.UnicodeScript.HANGUL
        }
        is OcrLanguage.Tesseract -> when (language.code) {
            "ben", "asm" -> Character.UnicodeScript.BENGALI
            "ara", "urd", "fas", "pus", "uig" -> Character.UnicodeScript.ARABIC
            "tam" -> Character.UnicodeScript.TAMIL
            "tel" -> Character.UnicodeScript.TELUGU
            "kan" -> Character.UnicodeScript.KANNADA
            "mal" -> Character.UnicodeScript.MALAYALAM
            "guj" -> Character.UnicodeScript.GUJARATI
            "pan" -> Character.UnicodeScript.GURMUKHI
            "ori" -> Character.UnicodeScript.ORIYA
            "sin" -> Character.UnicodeScript.SINHALA
            "tha" -> Character.UnicodeScript.THAI
            "lao" -> Character.UnicodeScript.LAO
            "khm" -> Character.UnicodeScript.KHMER
            "mya" -> Character.UnicodeScript.MYANMAR
            "bod" -> Character.UnicodeScript.TIBETAN
            "rus", "ukr", "bel", "bul", "srp", "mkd", "kaz", "kir", "mon", "tgk" -> Character.UnicodeScript.CYRILLIC
            "ell" -> Character.UnicodeScript.GREEK
            "heb", "yid" -> Character.UnicodeScript.HEBREW
            "hye" -> Character.UnicodeScript.ARMENIAN
            "kat" -> Character.UnicodeScript.GEORGIAN
            "amh", "tir" -> Character.UnicodeScript.ETHIOPIC
            "syr" -> Character.UnicodeScript.SYRIAC
            "div" -> Character.UnicodeScript.THAANA
            "chr" -> Character.UnicodeScript.CHEROKEE
            "iku" -> Character.UnicodeScript.CANADIAN_ABORIGINAL
            else -> Character.UnicodeScript.LATIN
        }
    }

    // ---- Name suggestions ----

    private data class Kind(val label: String, val words: List<String>)

    /** Document types by keyword, in English and the languages ScanFire reads (lowercase matching). */
    private val kinds = listOf(
        Kind("Invoice", listOf("invoice", "tax invoice", "চালান", "ইনভয়েস", "factura", "facture", "rechnung", "فاتورة")),
        Kind("Receipt", listOf("receipt", "cash memo", "রসিদ", "মানি রিসিপ্ট", "रसीद", "recibo", "reçu", "quittung", "إيصال")),
        Kind("Bill", listOf("bill", "electricity", "utility", "বিল", "बिल")),
        Kind("Bank statement", listOf("statement", "account no", "a/c", "ব্যাংক", "हिसाब")),
        Kind("Prescription", listOf("prescription", "rx", "dr.", "mbbs", "ব্যবস্থাপত্র", "प्रिस्क्रिप्शन")),
        Kind("Certificate", listOf("certificate", "certify", "সনদ", "সার্টিফিকেট", "प्रमाण पत्र")),
        Kind("ID card", listOf("national id", "nid", "identity card", "জাতীয় পরিচয়", "পরিচয়পত্র", "आधार")),
        Kind("Passport", listOf("passport", "পাসপোর্ট", "पासपोर्ट")),
        Kind("Contract", listOf("agreement", "contract", "চুক্তি", "अनुबंध")),
        Kind("Letter", listOf("dear ", "sincerely", "প্রিয়", "বিনীত", "प्रिय")),
        Kind("Notes", listOf("lesson", "chapter", "পাঠ", "প্রশ্ন", "অধ্যায়", "पाठ")),
    )

    /** Converts any Unicode digits (Bengali, Arabic-Indic, Devanagari...) to ASCII 0-9. */
    fun asciiDigits(s: String): String = buildString(s.length) {
        for (c in s) append(if (Character.isDigit(c) && c !in '0'..'9') ('0' + Character.getNumericValue(c)) else c)
    }

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** The first plausible calendar date in [text], or null. Day-first for numeric dates. */
    fun findDate(text: String): Calendar? {
        val t = asciiDigits(text).lowercase()
        fun make(y: Int, m: Int, d: Int): Calendar? {
            val year = if (y < 100) 2000 + y else y
            if (year !in 1950..2100 || m !in 1..12 || d !in 1..31) return null
            return Calendar.getInstance().apply { clear(); set(year, m - 1, d) }
        }
        Regex("""\b(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})\b""").find(t)?.let { m ->
            make(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
        }
        Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})\b""").find(t)?.let { m ->
            make(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let { return it }
        }
        Regex("""\b(\d{1,2})\s*(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?,?\s*(\d{2,4})\b""").find(t)?.let { m ->
            make(m.groupValues[3].toInt(), months.indexOf(m.groupValues[2]) + 1, m.groupValues[1].toInt())?.let { return it }
        }
        Regex("""\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s*(\d{1,2}),?\s*(\d{4})\b""").find(t)?.let { m ->
            make(m.groupValues[3].toInt(), months.indexOf(m.groupValues[1]) + 1, m.groupValues[2].toInt())?.let { return it }
        }
        return null
    }

    /** Amount after a total-like word ("Total", "Grand total", "Amount", "মোট", "সর্বমোট"...), as printed. */
    fun findTotal(text: String): String? {
        val t = asciiDigits(text)
        val label = """(?i)(grand\s+total|total\s+amount|net\s+total|total|amount\s+due|amount|সর্বমোট|মোট|कुल)"""
        return Regex("""$label\s*[:=]?\s*(?:[A-Za-z৳$€£₹]{0,3}\.?\s*)(\d{1,3}(?:[,\s]\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""")
            .findAll(t).lastOrNull()?.groupValues?.get(2)?.replace(" ", "")
    }

    /** A short title: the first line that reads like a heading (not a number, date, or long sentence). */
    fun findTitle(text: String): String? = text.lines().map { it.trim() }.firstOrNull { line ->
        val letters = line.count(Character::isLetter)
        letters >= 4 && line.length <= 48 && letters * 2 >= line.length && !line.contains('@') && !line.startsWith("http")
    }?.trimEnd(':', '.', ',')

    /** Up to four distinct name suggestions, best first. [dateFallback] is used when the text has no date. */
    fun suggestNames(text: String, dateFallback: Long, locale: Locale = Locale.getDefault()): List<String> {
        val lower = text.lowercase()
        val kind = kinds.firstOrNull { k -> k.words.any { lower.contains(it) } }?.label
        val date = findDate(text)
        val dateLabel = SimpleDateFormat("d MMM yyyy", locale).format((date ?: Calendar.getInstance().apply { timeInMillis = dateFallback }).time)
        val total = findTotal(text)
        val title = findTitle(text)
        return buildList {
            if (kind != null) add(listOfNotNull(kind, dateLabel, total).joinToString(" · "))
            if (title != null) add(if (kind == null) "$title · $dateLabel" else title)
            if (kind != null && total != null) add("$kind · $dateLabel")
            if (title != null && kind == null) add(title)
        }.map { it.take(80) }.distinct().take(4)
    }
}

package com.ganim.scanfire.expenses

import com.ganim.scanfire.data.db.ExpenseEntity
import com.ganim.scanfire.data.db.ScanFireDatabase
import com.ganim.scanfire.ocr.TextInsights
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.Calendar
import java.util.Currency
import java.util.Locale

/** A receipt's amount, currency and date as read from its text; every field may be missing. */
data class ReceiptGuess(val amountMinor: Long?, val currency: String, val date: Long?, val title: String?)

data class MonthTotal(val year: Int, val month: Int, val totals: Map<String, Long>, val items: List<ExpenseEntity>)

object ExpenseParser {
    private val symbols = listOf(
        Regex("""৳|\bTk\.?\b|\bBDT\b|টাকা""", RegexOption.IGNORE_CASE) to "BDT",
        Regex("""₹|\bRs\.?\b|\bINR\b""", RegexOption.IGNORE_CASE) to "INR",
        Regex("""€|\bEUR\b""", RegexOption.IGNORE_CASE) to "EUR",
        Regex("""£|\bGBP\b""", RegexOption.IGNORE_CASE) to "GBP",
        Regex("""\bUSD\b|US\$|\$""", RegexOption.IGNORE_CASE) to "USD",
    )

    fun localCurrency(locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(locale).currencyCode }.getOrNull() ?: "USD"

    fun currencyIn(text: String, fallback: String = localCurrency()): String =
        symbols.firstOrNull { (re, _) -> re.containsMatchIn(text) }?.second ?: fallback

    /** "1,250.50" → 125050. Accepts thousands separators and up to two decimals. */
    fun toMinor(amount: String): Long? {
        val clean = TextInsights.asciiDigits(amount).replace(",", "").replace(" ", "").trim()
        val value = clean.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0 || value.scale() > 2) return null
        return value.movePointRight(2).toLong()
    }

    fun format(minor: Long, currency: String, locale: Locale = Locale.getDefault()): String {
        val number = java.text.NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
        return "$currency " + number.format(BigDecimal.valueOf(minor, 2))
    }

    fun guess(text: String, fallbackCurrency: String = localCurrency()): ReceiptGuess = ReceiptGuess(
        amountMinor = TextInsights.findTotal(text)?.let(::toMinor),
        currency = currencyIn(text, fallbackCurrency),
        date = TextInsights.findDate(text)?.timeInMillis,
        title = TextInsights.findTitle(text),
    )

    /** Newest month first; totals are kept per currency because they can't be added together. */
    fun byMonth(items: List<ExpenseEntity>): List<MonthTotal> {
        val cal = Calendar.getInstance()
        return items.groupBy { cal.timeInMillis = it.date; cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH) }
            .map { (ym, list) ->
                MonthTotal(ym.first, ym.second, list.groupBy { it.currency }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }, list.sortedByDescending { it.date })
            }
            .sortedWith(compareByDescending<MonthTotal> { it.year }.thenByDescending { it.month })
    }

    /** RFC 4180 CSV that spreadsheets open directly. */
    fun csv(items: List<ExpenseEntity>): String = buildString {
        append("Date,Title,Category,Amount,Currency\r\n")
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        items.sortedBy { it.date }.forEach {
            append(day.format(java.util.Date(it.date))).append(',')
            append(quote(it.title)).append(',')
            append(quote(it.category)).append(',')
            append(BigDecimal.valueOf(it.amountMinor, 2).toPlainString()).append(',')
            append(it.currency).append("\r\n")
        }
    }

    private fun quote(s: String): String {
        // A leading = + - @ would run as a formula in a spreadsheet.
        val safe = if (s.firstOrNull() in listOf('=', '+', '-', '@')) "'$s" else s
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}

class ExpenseRepository(private val db: ScanFireDatabase) {
    fun all(): Flow<List<ExpenseEntity>> = db.expenses().observeAll()

    suspend fun forDocument(docId: Long): ExpenseEntity? = db.expenses().forDocument(docId)

    suspend fun save(expense: ExpenseEntity) {
        if (expense.id == 0L) db.expenses().insert(expense) else db.expenses().update(expense)
    }

    suspend fun delete(id: Long) = db.expenses().delete(id)
}

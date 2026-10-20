package com.ganim.scanfire.pdf

/**
 * Parses page selections like "1-3, 5, 8-" (1-based, inclusive, open-ended ranges run to the
 * last page) into 0-based indices in the order given, without duplicates.
 * Returns null when the text is not a valid selection for [pageCount] pages.
 */
object PageRanges {
    fun parse(text: String, pageCount: Int): List<Int>? {
        if (text.isBlank() || pageCount <= 0) return null
        val out = LinkedHashSet<Int>()
        for (raw in text.split(',', ';')) {
            val part = raw.trim()
            if (part.isEmpty()) continue
            val pieces = part.split('-').map { it.trim() }
            when (pieces.size) {
                1 -> {
                    val n = pieces[0].toIntOrNull() ?: return null
                    if (n !in 1..pageCount) return null
                    out += n - 1
                }
                2 -> {
                    val start = if (pieces[0].isEmpty()) 1 else pieces[0].toIntOrNull() ?: return null
                    val end = if (pieces[1].isEmpty()) pageCount else pieces[1].toIntOrNull() ?: return null
                    if (start !in 1..pageCount || end !in 1..pageCount) return null
                    val range = if (start <= end) start..end else start downTo end
                    range.forEach { out += it - 1 }
                }
                else -> return null
            }
        }
        return out.toList().ifEmpty { null }
    }
}

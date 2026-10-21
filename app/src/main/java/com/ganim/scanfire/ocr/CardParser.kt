package com.ganim.scanfire.ocr

/** Contact details read from a business card. Any field can be empty; the user reviews them. */
data class CardContact(
    val name: String = "",
    val title: String = "",
    val company: String = "",
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val websites: List<String> = emptyList(),
    val address: String = "",
)

/**
 * Heuristic business-card parser over OCR text (pure Kotlin, unit-tested). Emails, phones and
 * websites by pattern; job title and company by keywords; the name is the most name-like line
 * left (2-4 capitalised words, no digits), preferring one that matches the email's local part.
 */
object CardParser {
    private val email = Regex("""[\p{L}\p{N}._%+-]+@[\p{L}\p{N}.-]+\.\p{L}{2,}""")
    private val web = Regex("""(?i)\b((?:https?://)?(?:www\.)?[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|net|org|io|co|bd|in|pk|uk|us|info|biz|edu|gov)(?:\.[a-z]{2})?(?:/\S*)?)""")
    private val phone = Regex("""(?:\+?\p{Nd}[\p{Nd}\s().-]{6,}\p{Nd})""")
    private val titleWords = listOf(
        "manager", "director", "engineer", "officer", "executive", "ceo", "cto", "cfo", "coo", "founder", "president",
        "head", "lead", "consultant", "developer", "designer", "analyst", "advisor", "partner", "sales", "marketing",
        "doctor", "dr.", "professor", "lecturer", "teacher", "accountant", "architect", "specialist", "coordinator", "assistant",
    )
    private val companyWords = listOf(
        "ltd", "limited", "inc", "llc", "plc", "corp", "corporation", "company", "co.", "group", "industries", "enterprise",
        "solutions", "technologies", "tech", "bank", "hospital", "university", "college", "school", "associates", "agency", "studio",
    )
    private val addressWords = listOf("road", "rd", "street", "st.", "avenue", "house", "floor", "block", "sector", "lane", "dhaka", "city", "district", "po box", "suite")

    fun parse(text: String): CardContact {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val emails = email.findAll(text).map { it.value }.distinct().toList()
        // Emails are removed first so their domain isn't mistaken for a website.
        val websites = lines.flatMap { line -> web.findAll(email.replace(line, " ")).map { it.value.trimEnd('.', ',') }.toList() }.distinct()
        val phones = phone.findAll(text).map { it.value.trim() }
            .filter { p -> p.count(Character::isDigit) in 7..15 }
            .filterNot { p -> Regex("""^\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}$""").matches(p) } // dates
            .distinct().toList()

        fun isContactLine(l: String) = email.containsMatchIn(l) || web.containsMatchIn(l) || phone.containsMatchIn(l)
        val rest = lines.filterNot(::isContactLine)
        val title = rest.firstOrNull { l -> titleWords.any { l.lowercase().contains(it) } }.orEmpty()
        val company = rest.firstOrNull { l -> l != title && companyWords.any { w -> Regex("""(?i)\b${Regex.escape(w)}""").containsMatchIn(l) } }.orEmpty()
        val address = rest.filter { l -> l != title && l != company && addressWords.any { l.lowercase().contains(it) } }.joinToString(", ")

        val local = emails.firstOrNull()?.substringBefore('@')?.lowercase()?.replace(Regex("[^a-z]"), "").orEmpty()
        val candidates = rest.filter { l ->
            l != title && l != company && l !in address && l.none(Char::isDigit) &&
                l.split(Regex("\\s+")).let { words -> words.size in 1..4 && words.all { w -> w.firstOrNull()?.let { !it.isLetter() || it.isUpperCase() || !it.isLowerCase() } ?: false } }
        }
        val name = candidates.maxByOrNull { l ->
            val words = l.split(Regex("\\s+"))
            var score = 0
            if (words.size in 2..3) score += 2
            if (local.isNotEmpty() && words.any { it.lowercase().replace(Regex("[^a-z]"), "").let { w -> w.length > 2 && local.contains(w) } }) score += 5
            score - lines.indexOf(l) / 3 // earlier lines are a little more likely
        }.orEmpty()

        return CardContact(name, title, company, phones, emails, websites, address)
    }
}

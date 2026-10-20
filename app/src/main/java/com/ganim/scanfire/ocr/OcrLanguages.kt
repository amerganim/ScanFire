package com.ganim.scanfire.ocr

/**
 * Every language ScanFire can read. Two engines, both free and offline once set up:
 * - ML Kit (Google Play services) for its five scripts, fast and very accurate;
 * - Tesseract for scripts ML Kit doesn't have; each language model (~0.5–5 MB) is downloaded once.
 *
 * Keys are stored in settings: ML Kit keys are the script name ("latin"), Tesseract keys are
 * "tess:" plus Tesseract's code ("tess:ben").
 */
sealed interface OcrLanguage {
    val key: String
    val label: String

    data class MlKit(val script: OcrScript) : OcrLanguage {
        override val key get() = script.key
        override val label get() = script.label
    }

    data class Tesseract(val code: String, val name: String, val native: String = "") : OcrLanguage {
        override val key get() = "tess:$code"
        override val label get() = if (native.isEmpty()) name else "$name ($native)"
    }

    companion object {
        /** Languages ML Kit can't read, from Tesseract's fast models. Sorted by name in the UI. */
        val tesseract: List<Tesseract> = listOf(
            Tesseract("ben", "Bengali", "বাংলা"),
            Tesseract("asm", "Assamese", "অসমীয়া"),
            Tesseract("ara", "Arabic", "العربية"),
            Tesseract("urd", "Urdu", "اردو"),
            Tesseract("fas", "Persian", "فارسی"),
            Tesseract("pus", "Pashto", "پښتو"),
            Tesseract("tam", "Tamil", "தமிழ்"),
            Tesseract("tel", "Telugu", "తెలుగు"),
            Tesseract("kan", "Kannada", "ಕನ್ನಡ"),
            Tesseract("mal", "Malayalam", "മലയാളം"),
            Tesseract("guj", "Gujarati", "ગુજરાતી"),
            Tesseract("pan", "Punjabi", "ਪੰਜਾਬੀ"),
            Tesseract("ori", "Odia", "ଓଡ଼ିଆ"),
            Tesseract("sin", "Sinhala", "සිංහල"),
            Tesseract("tha", "Thai", "ไทย"),
            Tesseract("lao", "Lao", "ລາວ"),
            Tesseract("khm", "Khmer", "ខ្មែរ"),
            Tesseract("mya", "Burmese", "မြန်မာ"),
            Tesseract("bod", "Tibetan", "བོད་ཡིག"),
            Tesseract("rus", "Russian", "Русский"),
            Tesseract("ukr", "Ukrainian", "Українська"),
            Tesseract("bel", "Belarusian", "Беларуская"),
            Tesseract("bul", "Bulgarian", "Български"),
            Tesseract("srp", "Serbian", "Српски"),
            Tesseract("mkd", "Macedonian", "Македонски"),
            Tesseract("kaz", "Kazakh", "Қазақ"),
            Tesseract("kir", "Kyrgyz", "Кыргызча"),
            Tesseract("mon", "Mongolian", "Монгол"),
            Tesseract("tgk", "Tajik", "Тоҷикӣ"),
            Tesseract("ell", "Greek", "Ελληνικά"),
            Tesseract("heb", "Hebrew", "עברית"),
            Tesseract("yid", "Yiddish", "ייִדיש"),
            Tesseract("hye", "Armenian", "Հայերեն"),
            Tesseract("kat", "Georgian", "ქართული"),
            Tesseract("amh", "Amharic", "አማርኛ"),
            Tesseract("tir", "Tigrinya", "ትግርኛ"),
            Tesseract("syr", "Syriac", "ܣܘܪܝܝܐ"),
            Tesseract("div", "Dhivehi", "ދިވެހި"),
            Tesseract("uig", "Uyghur", "ئۇيغۇرچە"),
            Tesseract("chr", "Cherokee", "ᏣᎳᎩ"),
            Tesseract("iku", "Inuktitut", "ᐃᓄᒃᑎᑐᑦ"),
            // Also offered: Latin-script languages where Tesseract's dictionary sometimes beats ML Kit.
            Tesseract("eng", "English"),
            Tesseract("vie", "Vietnamese", "Tiếng Việt"),
            Tesseract("tur", "Turkish", "Türkçe"),
        ).sortedBy { it.name }

        val mlKit: List<MlKit> = OcrScript.entries.map { MlKit(it) }

        fun of(key: String): OcrLanguage =
            if (key.startsWith("tess:")) tesseract.firstOrNull { it.key == key } ?: MlKit(OcrScript.Latin)
            else MlKit(OcrScript.of(key))
    }
}

# Offline feature audit (2026-10-20, updated 2026-10-21)

> **Since the audit:** edge detection + corner editor for imported photos, searchable PDF, more OCR
> languages (44 via Tesseract, incl. Bengali), privacy redaction, read aloud and the recycle bin are
> all done, and so are smart name suggestions, OCR auto language detection, business card → contact,
> Wi-Fi transfer to a computer and the QR generator. Remaining ideas: private vault, expiry reminders,
> expense list, OCR of a region, TXT → PDF, annotation, app UI localization (all languages at once).

Every CamScanner feature from [camscanner-analysis.md](camscanner-analysis.md) that *can* work
without a server, checked against ScanFire. ✅ done · ⚠️ partly · ❌ missing.
Server-only features (sync, teams, fax, e-sign contacts, secure links) are out of scope by design.

## Capture

| Feature | ScanFire | Notes |
| --- | --- | --- |
| Edge detection, perspective fix, auto-capture | ✅ | ML Kit scanner |
| Batch / multi-page | ✅ | |
| Auto-enhance | ✅ | scanner's own enhance; Magic color by default for imports |
| **Edge detection on imported gallery photos** | ❌ | **Biggest gap.** Imports from our photo picker are never straightened. The scanner's *own* gallery button does it, but ours doesn't. Needs our own quad detection + 4-corner editor |
| ID card (both sides) | ✅ | |
| Book (split spreads) | ✅ | |
| Whiteboard / slides | ⚠️ | filters work; no dedicated preset |
| Timestamp camera | ❌ | easy: date/time stamp at capture |
| ID photo maker | ❌ | possible: ML Kit subject segmentation + passport sizes |
| Long-document stitching | ⚠️ | long-image export only, no overlap stitching |
| QR / barcode reading | ✅ | |
| QR generation | ❌ | easy with ZXing core (Apache 2.0) |
| Object counting (CountCam) | ❌ | low value |

## Editing

| Feature | ScanFire | Notes |
| --- | --- | --- |
| Crop / rotate | ⚠️ | rectangle crop only; no 4-corner perspective crop after capture |
| Filters | ✅ | Original, Enhance, Magic, Gray, B&W; sharpening |
| Stain / finger cleanup | ⚠️ | scanner's Clean tool at capture time only |
| Annotate (pen, highlighter, text) | ❌ | possible, moderate |
| Moiré removal, photo restore | ❌ | needs ML models; skip |

## Text

| Feature | ScanFire | Notes |
| --- | --- | --- |
| OCR, whole document | ✅ | 5 scripts |
| OCR of a selected region | ❌ | easy |
| Edit, copy, TXT, Word | ✅ | |
| Translate | ✅ | model download once |
| Searchable PDF (hidden text layer) | ❌ | high value, see below |
| To Excel / PPT | ❌ | PPT of page images is easy; Excel needs table detection |

## PDF

| Feature | ScanFire | Notes |
| --- | --- | --- |
| Export PDF / JPEG / long image / ZIP | ✅ | |
| Merge, extract, compress, password, watermark, page numbers, sign | ✅ | verified on device |
| Reorder or delete pages of an existing PDF | ⚠️ | via Import PDF → edit → export (rasterises) |
| TXT / Markdown / HTML → PDF | ❌ | TXT easy; HTML via WebView print |
| Office → PDF | ❌ | not feasible offline at a sensible size |

## Library and safety

| Feature | ScanFire | Notes |
| --- | --- | --- |
| Folders, search (names + text), sort, move, merge | ✅ | |
| Duplicate / copy document | ❌ | easy |
| Tags | ❌ | easy |
| Recycle bin | ❌ | easy, important: deletes are permanent today |
| App lock | ✅ | |
| Per-document encryption | ❌ | possible with Android Keystore |
| Backup / restore | ✅ | restore still needs its device check |
| Share in / open with / shortcuts | ✅ | |
| Home-screen widgets | ❌ | moderate |

## Summary of gaps worth closing first

1. Edge detection + 4-corner crop for imported photos
2. Recycle bin
3. Searchable PDF export
4. OCR of a selected region, duplicate document, QR generator, TXT → PDF

---

# Offline ideas that would set ScanFire apart

CamScanner keeps your files on its servers and charges for most of these. Done on the phone, they
cost nothing and are private by construction.

| # | Idea | Why it stands out | How (all free, on-device) | Effort |
| --- | --- | --- | --- | --- |
| 1 | **Searchable PDF** | Exported PDFs become searchable and copyable in any PDF reader. A paid feature elsewhere | Hidden text layer at ML Kit word boxes, drawn with PdfBox | M |
| 2 | **Smart auto-naming and filing** | "Receipt · 20 Oct 2026 · 1,250.00" instead of "Scan 2026-10-20" | OCR + ML Kit Entity Extraction (dates, money, addresses, phone numbers, all offline) | M |
| 3 | **Privacy redaction before sharing** | One tap blacks out phone numbers, emails, card and ID numbers | Entity Extraction + regex on OCR word boxes, burned in | M |
| 4 | **Private vault** | Chosen documents encrypted at rest, opened with fingerprint | AES-GCM with an Android Keystore key, biometric-bound | M |
| 5 | **Read aloud** | Listen to a scanned page; helps low vision and language learners | Android's on-device TextToSpeech | S |
| 6 | **Business card → contact** | Scan a card, get a pre-filled contact | OCR + Entity Extraction → contacts insert intent | S |
| 7 | **Expiry reminders** | ID, passport, warranty: get notified before they expire | Detected or entered date + local notification | S |
| 8 | **Expense list from receipts** | Totals and dates collected into a monthly CSV | Entity Extraction (money, dates) + CSV export | M |
| 9 | **Wi-Fi transfer to a computer** | Open a link on the PC, download documents. No cable, no cloud | Tiny local web server on the LAN, shown as a QR code | M |
| 10 | **More OCR languages (e.g. Bengali, Arabic, Thai)** | ML Kit lacks them; few free scanners have them offline | Tesseract (Apache 2.0) with per-language data downloaded once | L |
| 11 | **Recycle bin with 30-day restore** | Accidental deletes are recoverable | Soft-delete flag + cleanup worker | S |
| 12 | **QR generator incl. Wi-Fi share** | Make QR codes for links, text, Wi-Fi | ZXing core | S |

S = under a day, M = 1–3 days, L = about a week.

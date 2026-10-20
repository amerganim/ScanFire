# ScanFire build plan

A free, offline, ad-free document scanner for Android. The feature research behind it is
in [camscanner-analysis.md](camscanner-analysis.md).

**Stack:** Kotlin, Jetpack Compose (Material 3), Room, DataStore, Coil, Google ML Kit
(on-device), PdfBox-Android. minSdk 24, targetSdk 36. One `:app` module, split into
packages (`data`, `scan`, `edit`, `export`, `ocr`, `pdf`, `tools`, `settings`, `ui`).

**Storage model:** each document is a folder `files/docs/<docId>/` holding one JPEG per
page (`<pageId>.jpg`, the processed image) and an untouched original (`<pageId>_orig.jpg`)
so filters and crops can always be redone. Room holds folders, documents, pages (order,
rotation, filter, OCR text) and QR history.

Each phase ends with a build, a device check on the Galaxy A15, and a commit pushed to
`main`.

| Phase | Scope | Done when |
| --- | --- | --- |
| 0 | Scaffold: Gradle, theme, navigation shell, docs, CI | App launches; CI builds |
| 1 | Library: Room schema, home list/grid, folders, rename, delete, move, search, sort | Can create folders and see documents |
| 2 | Capture: ML Kit Document Scanner, gallery import, document screen, page viewer | Scan → document with pages |
| 3 | Editing: rotate, crop, filters (original / enhance / B&W / gray / magic), reorder, delete, add pages, rename | Edited page persists and can be reverted |
| 4 | Export and share: PDF (page size, margins, quality), JPEG, long image, ZIP; share sheet; save via SAF | Shared PDF opens elsewhere |
| 5 | OCR: per page and whole document, edit and copy text, TXT / DOCX export, search includes OCR text, on-device translate | Text recognized offline |
| 6 | PDF toolkit: import PDF, merge, extract pages, compress, password, watermark, signature, page numbers | Each tool produces a valid PDF |
| 7 | Scan tools: QR / barcode with history, ID card (both sides on one page), photo-to-text shortcut, tools hub | Tools tab complete |
| 8 | Security and settings: biometric/PIN app lock, settings, backup and restore (ZIP), open-with / share-to intents, app shortcuts | Lock works; restore round-trips |
| 9 | Release: R8, icon, about/licenses, README, release APK on GitHub | Signed-able release builds clean |

## Deliberately left out

These need servers (cost money) or exist only to make money: account and cloud sync, teams,
fax, e-sign contacts, secure share links, ads, subscriptions, analytics.

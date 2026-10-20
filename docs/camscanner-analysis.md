# What CamScanner does (and what ScanFire takes from it)

Source: static analysis of CamScanner 7.26.5 (`com.intsig.camscanner`, versionCode 72651)
pulled from our own test phone (Galaxy A15) on 2026-10-20 and read with REA plus the
Android SDK's `aapt2`. We studied **what features exist**, from the manifest, the tool-menu
config and the native-library list. No CamScanner code, artwork or text is copied into ScanFire.

## Size of the app

| Thing | Count |
| --- | --- |
| Activities (screens) | 356 + 20 aliases |
| Services / receivers / providers | 26 / 29 / 24 |
| Native libraries (arm64) | 68 |
| DEX files | 17 |
| APK set | ~241 MB over 5 splits |

Most of that weight is from ads (Pangle, Mintegral, Meta, Vungle), tracking (AppsFlyer,
Firebase, Sentry, ByteDance APM), payments, and cloud and enterprise features that need
CamScanner's servers.

## Features found, grouped

1. **Capture**: document scan with edge detection and auto-crop, batch mode, ID cards/passports,
   ID photo maker, book mode, slides/whiteboard, timestamp camera, long-document stitching,
   CountCam (object counting), invoices, QR/barcode with history, homework/formula scans.
2. **Image editing**: crop, rotate, filters (original, enhance, B&W, grayscale, "magic color"),
   moiré removal, AI erase / watermark removal, smart erase, doodle and text, restore photo.
3. **OCR**: on-device OCR, region OCR, batch OCR, OCR language choice, photo translation.
4. **Conversion**: image/PDF to Word, Excel and PPT, PDF to images or a long image, Office,
   Markdown, HTML, TXT, CAD, PSD and OFD files to PDF.
5. **PDF tools**: sign, watermark, reorder, merge, compress, extract pages, password,
   PDF editing, e-signature workflow with contacts.
6. **Library**: folders, tags, search, move/copy, encrypted documents and folders,
   biometric lock, recycle bin, backup.
7. **Import and export**: open-with/share-to for images, PDF and Office files, gallery import,
   cloud drives (Google Drive, Dropbox, OneDrive, Box, Evernote), auto-upload, secure links,
   fax, print.
8. **Account, sync and teams**: login, cloud sync, enterprise workspaces. *(Server-only)*
9. **Home screen**: widgets (capture, QR, search, recent documents), shortcuts and deep links.
10. **Monetization**: subscriptions, ads, rewarded video. *(Not wanted)*

## ScanFire's rule: free for users, and no cost for us either

ScanFire has no server, no account, no ads, no analytics and no paid API. Every feature has
to run on the phone using free libraries:

| CamScanner feature | ScanFire approach | Cost |
| --- | --- | --- |
| Edge detection, crop, cleanup | Google ML Kit Document Scanner (on-device, via Play services) | Free |
| Filters | Own Kotlin image filters (ColorMatrix + adaptive threshold) | Free |
| OCR | ML Kit Text Recognition v2 (Latin, Devanagari, Chinese, Japanese, Korean), bundled | Free |
| Translation | ML Kit on-device Translation (models downloaded once) | Free |
| QR / barcode | ML Kit Code Scanner (Play services) | Free |
| PDF create / read | Android `PdfDocument` / `PdfRenderer` | Free |
| Merge, split, password, watermark, sign | PdfBox-Android (Apache-2.0) | Free |
| To Word | Hand-written minimal `.docx` (OOXML) writer | Free |
| Cloud drives | Android Storage Access Framework (the user's own Drive, OneDrive etc.) | Free |
| App lock | AndroidX Biometric | Free |
| Sync, teams, fax, e-sign contacts, secure links | **Skipped**: these need servers | n/a |
| Ads, subscriptions | **Skipped on purpose** | n/a |

The only possible cost is the **one-time US$25 Google Play developer fee**, and only if
ScanFire is published on Play. Sideloading and GitHub releases are free.

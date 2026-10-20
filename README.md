# ScanFire

A free, offline, ad-free document scanner for Android.

- Scan documents with automatic edge detection and cropping
- Filters, rotate, crop, reorder and delete pages
- Export to PDF, JPEG or one long image, and share them
- Recognize text (OCR) offline, then copy it or export to TXT or Word
- PDF tools: merge, extract pages, compress, password-protect, watermark and sign
- QR and barcode scanner, ID-card mode
- App lock, folders, search, backup and restore

**Costs nothing, for anyone.** There's no account, no server, no ads and no tracking.
Everything runs on the phone with free, on-device Google ML Kit models and open-source
libraries. See [docs/camscanner-analysis.md](docs/camscanner-analysis.md) for how the
feature set was chosen, and [docs/PLAN.md](docs/PLAN.md) for the build phases.

## Build

Requires JDK 17+ (the Android Studio JBR works) and the Android SDK (compileSdk 37).

```
gradlew assembleDebug
```

On Windows without `JAVA_HOME`:

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cmd /c "<repo>\gradlew.bat -p <repo> assembleDebug"
```

## License

MIT. See [LICENSE](LICENSE).

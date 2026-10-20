# ScanFire

A free, offline, ad-free document scanner for Android.

**Costs nothing, for anyone.** There's no account, no server, no ads and no tracking. Everything
runs on the phone using free, on-device Google ML Kit models and open-source libraries.

## Features

**Scan**
- Automatic edge detection, perspective correction and auto-capture (ML Kit Document Scanner)
- Import photos from the gallery, or share photos and PDFs into ScanFire from any app
- ID card mode (front and back on one page), book mode (splits spreads), scan-to-text
- QR code and barcode scanner with local history

**Edit**
- Filters: Original, Enhance, Magic color, Grayscale, B&W (adaptive threshold)
- Crop, rotate, reorder by drag, delete, add pages; every edit can be redone from the original
- Sign pages with a drawn signature

**Text (OCR)**
- Offline text recognition for Latin, Devanagari, Chinese, Japanese and Korean scripts
- Edit, copy and share text, or export it as TXT or Word (.docx)
- Search your library by document name and recognised text
- Translate offline (each language model downloads once)

**PDF and export**
- Export as PDF (A4, Letter or fit-to-image), JPEG, ZIP or one long image
- Share, save anywhere through the system file picker (Drive, USB, etc.), or print
- PDF tools: import, merge, extract pages, compress, add or remove a password, watermark, page numbers

**Organise and protect**
- Folders and subfolders, list or grid view, sort, multi-select, move, merge documents
- App lock with fingerprint, face or screen lock; hidden from the recent-apps preview
- Backup and restore everything as one ZIP

How the feature set was chosen: [docs/camscanner-analysis.md](docs/camscanner-analysis.md).
Build phases: [docs/PLAN.md](docs/PLAN.md).

## Privacy

Documents never leave the phone unless you share or save them. The app has no analytics and
no server. The only network use is ML Kit downloading a translation language model the first
time you pick a language. Scanner, OCR and barcode models come from Google Play services, so
ScanFire needs a phone with Google Play services.

## Build

Requires JDK 17+ (the Android Studio JBR works) and the Android SDK (compileSdk 37).

```
./gradlew assembleDebug            # debug APK (all CPU types in one file)
./gradlew testDebugUnitTest        # unit tests
./gradlew bundleRelease            # App Bundle for Google Play
./gradlew assembleRelease -PsplitAbi   # one release APK per CPU type (~16-22 MB each)
```

On Windows without `JAVA_HOME`:

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cmd /c "<repo>\gradlew.bat -p <repo> assembleDebug"
```

### Release signing

Add these to `local.properties` (it is gitignored):

```
release.storeFile=C:/path/to/scanfire-release.jks
release.storePassword=...
release.keyAlias=scanfire
release.keyPassword=...
```

Without them, release builds are signed with the debug key. That's fine for testing, but
they can't go on Play, and a properly signed build can't upgrade them later.

## Costs

None to build, run or distribute on GitHub. Publishing on Google Play needs Google's one-time
US$25 developer registration. That's optional.

## License

MIT. See [LICENSE](LICENSE).

# Google Play release kit

Everything needed to fill in Play Console for ScanFire 1.1.0 (versionCode 2).

## Files

| What | Where |
|---|---|
| App bundle (upload this) | `app/build/outputs/bundle/release/app-release.aab`, built with `gradlew bundleRelease` |
| App icon 512×512 | `docs/play-store/graphics/icon-512.png` |
| Feature graphic 1024×500 | `docs/play-store/graphics/feature-graphic-1024x500.png` |
| Phone screenshots (2–8, 9:16) | still to take on the phone; see "Screenshots" below |
| Privacy policy URL | https://amerganim.github.io/scanfire-privacy/ (source: github.com/amerganim/scanfire-privacy) |

## Signing

- The bundle is signed with an **upload key** at `C:\Users\USER\.scanfire-signing\scanfire-upload.jks`
  (alias `scanfire`). Its password is in `README-KEEP-SAFE.txt` in that folder and in `local.properties`. Neither is in git.
- **Back up that folder somewhere safe** (a USB stick or your own password manager). Without the upload key you
  can't publish updates. If it's ever lost, Play support can reset the upload key, but only because of the next point.
- In Play Console, accept **Play App Signing** (the default). Google keeps the real app-signing key; yours only uploads.

## Store listing

**App name** (30 max): `ScanFire: PDF & Doc Scanner`
(Play's metadata policy bans price or promo words such as "free" or "no ads" in the title, icon and graphics;
they're fine in the descriptions.)

**Short description** (80 max):
`Free document scanner: clean scans, text from any page, PDFs. No ads, offline.`

**Full description** (4000 max):

```
ScanFire turns your phone into a document scanner. It's completely free: no ads, no subscription, no account, and your documents never leave your phone.

SCAN CLEAN, SHARP PAGES
• Point the camera at a page: ScanFire finds the edges, flattens it and crops it for you
• Clear scan whitens paper, removes shadows and sharpens text, so photos look like real scans
• Import photos from your gallery: edges are found and cleaned automatically
• Filters: Original, Clear scan, Bright, Grey, Black & white
• Special modes for ID cards (front and back on one page) and books (splits two-page spreads)

TEXT FROM ANY PAGE
• Copy, search, share or translate the text on a page
• Read part of a page: drag a box around a phone number or a paragraph
• More than 50 languages, including Bengali, Hindi, Arabic, Urdu, Tamil, Chinese, Japanese and Korean
• Listen to a page with Read aloud
• Searchable PDFs: find words inside your scans later
• Save text as Word (.docx) or TXT

PDF TOOLS, ALL ON THE PHONE
• Merge, split, compress, password-protect, unlock, watermark and number pages
• Sign documents with your finger, mark them up with pen, highlighter and notes
• Black out private details (phone numbers, IDs, emails) before you share

STAY ORGANISED
• Folders, sorting, and search across names and the text inside documents
• Private vault: encrypted documents that open with your fingerprint
• Reminders: get told before your passport or ID expires; ScanFire suggests the date from the page
• Expenses: scan receipts and see what you spent each month; export to a spreadsheet
• Recycle bin keeps deleted documents for 30 days
• QR and barcode scanner, and a QR maker for links, text and Wi-Fi
• Business cards straight into your contacts

PRIVATE BY DESIGN
• No account, no cloud, no tracking
• App lock with fingerprint, face or screen lock
• Send to computer over your own Wi-Fi, no cable and no cloud
• Back up everything into one file you keep wherever you like

ScanFire is open source (MIT): github.com/amerganim/ScanFire
```

**Category**: Productivity · **Tags**: Document scanner, PDF, OCR
**Contact email**: the developer account email (required by Play; shown on the listing).
**Website**: https://github.com/amerganim/ScanFire

## Data safety form

ScanFire itself collects nothing. Google ML Kit (used through Google Play services for scanning, text recognition,
code scanning, language detection and translation) sends Google anonymous diagnostics. Per Google's
[ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure), answer:

- Does your app collect or share any of the required user data types? **Yes**
- Is all user data encrypted in transit? **Yes** (ML Kit uses HTTPS)
- Do you provide a way for users to request that their data is deleted? **Yes**: all app data is on the device and
  is removed by deleting documents or uninstalling. (Diagnostics are not tied to a person.)
- Data types collected (all **Collected**, **not shared**, **processed ephemerally: No**, **required**, purpose
  **Analytics** and **App functionality**):
  - **App info and performance → Diagnostics** (latency, errors, API configuration)
  - **App info and performance → Other app performance data**
  - **Device or other IDs** (per-installation identifier used by ML Kit)
- Not collected: location, personal info, financial info, health, messages, photos/videos, audio, files and docs,
  calendar, contacts, app activity, web browsing. Photos and documents are processed **on the device only** and are
  never sent anywhere, so they don't count as collected.

## Other Play Console questions

- **Ads**: No ads.
- **App access**: All functionality is available without an account or special access.
- **Content rating** (IARC questionnaire): Utility/productivity; no violence, sex, gambling, user-generated content
  shared with others, or location sharing → expected rating "Everyone / PEGI 3".
- **Target audience**: 18+ is the simplest choice (13+ is fine too); not designed for children.
- **News app**: No. **COVID-19 app**: No. **Government app**: No. **Financial features**: None.
- **Health apps**: No.
- **Permissions declarations**: none of the restricted permissions (SMS, call log, all-files, exact alarm,
  background location) are used, so no declaration forms are needed.
- **Foreground service**: none.

## Screenshots to take (phone, portrait)

1. Home with a few documents (light theme)
2. A scan before/after Clear scan in the editor
3. Text from a page (OCR) with the language chip visible
4. Document screen with the ink action bar
5. Private vault unlocked (with a dummy document)
6. Expenses month view
7. Tools tab

Use demo documents only (no real personal papers). `adb exec-out screencap -p > shot.png` gives full-resolution PNGs.

## Release steps

1. `gradlew testDebugUnitTest bundleRelease`
2. Play Console → Create app → fill the listing above, upload graphics and screenshots.
3. Testing → Internal testing → create a release, upload `app-release.aab`, add yourself as tester, install from the
   Play link and check scanning, OCR, export.
4. New personal developer accounts must run a **closed test with at least 12 testers for 14 days** before Production
   access is granted. Invite friends by email list or a Google Group.
5. Production → promote the tested release.

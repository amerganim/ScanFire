# ScanFire release rules.

# PdfBox-Android: optional JPEG2000 codec isn't shipped, and fonts/encodings are loaded reflectively.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }

# Tesseract4Android: JNI calls back into these classes by name.
-keep class com.googlecode.tesseract.android.** { *; }

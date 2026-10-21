package com.ganim.scanfire.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR code generation with ZXing (Apache 2.0), offline. Pure JVM so it is unit-tested. */
object QrMaker {
    enum class WifiSecurity(val code: String, val label: String) { WPA("WPA", "WPA/WPA2/WPA3"), WEP("WEP", "WEP"), None("nopass", "Open (no password)") }

    /**
     * The de-facto Wi-Fi QR format understood by Android and iOS cameras:
     * `WIFI:T:WPA;S:<ssid>;P:<password>;H:<hidden>;;` with \ ; , : " escaped by a backslash.
     */
    fun wifiPayload(ssid: String, password: String, security: WifiSecurity, hidden: Boolean = false): String {
        fun esc(s: String) = s.replace(Regex("""([\\;,:"])"""), """\\$1""")
        return buildString {
            append("WIFI:T:").append(security.code).append(';')
            append("S:").append(esc(ssid)).append(';')
            if (security != WifiSecurity.None) append("P:").append(esc(password)).append(';')
            if (hidden) append("H:true;")
            append(';')
        }
    }

    /**
     * ARGB pixels (black on white, with a 4-module quiet zone) of a QR code for [text], [size] x [size].
     * Medium error correction: robust to smudges while keeping modules large.
     */
    fun pixels(text: String, size: Int): IntArray {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        return IntArray(size * size) { i -> if (matrix[i % size, i / size]) black else white }
    }
}

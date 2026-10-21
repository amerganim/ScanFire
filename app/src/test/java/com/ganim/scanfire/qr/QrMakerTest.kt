package com.ganim.scanfire.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class QrMakerTest {
    @Test
    fun `wifi payload escapes special characters`() {
        QrMaker.wifiPayload("Home;Net", "pa:ss\\word", QrMaker.WifiSecurity.WPA) shouldBe """WIFI:T:WPA;S:Home\;Net;P:pa\:ss\\word;;"""
        QrMaker.wifiPayload("Cafe", "", QrMaker.WifiSecurity.None, hidden = true) shouldBe "WIFI:T:nopass;S:Cafe;H:true;;"
    }

    @Test
    fun `generated code decodes back to the same text, including Bengali`() {
        for (text in listOf("https://example.com/a?b=1", "আমাদের জাতীয় মাছ ইলিশ")) {
            val size = 400
            val px = QrMaker.pixels(text, size)
            val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, px)))).text
            decoded shouldBe text
        }
    }
}

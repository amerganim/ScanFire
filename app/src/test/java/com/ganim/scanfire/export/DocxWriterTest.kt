package com.ganim.scanfire.export

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class DocxWriterTest {
    @Test
    fun `package has the parts Word needs and well-formed XML`() {
        val bytes = ByteArrayOutputStream().also { DocxWriter.write(listOf("Hello & <world>", "Page two"), it, "T") }.toByteArray()
        val parts = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { parts[it.name] = zip.readBytes() }
        }
        parts.keys shouldContainAll listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "docProps/core.xml")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        parts.values.forEach { factory.newDocumentBuilder().parse(ByteArrayInputStream(it)) } // throws if malformed
        val doc = parts.getValue("word/document.xml").decodeToString()
        doc shouldContain "Hello &amp; &lt;world&gt;"
        doc shouldContain """<w:br w:type="page"/>"""
    }

    @Test
    fun `control characters are dropped`() {
        DocxWriter.escape("a\u0001b\tc") shouldBe "ab\tc"
    }
}

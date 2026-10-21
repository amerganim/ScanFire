package com.ganim.scanfire.transfer

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URL

class TransferServerTest {
    private val server = TransferServer(token = "abc234", route = { path ->
        when (path) {
            "/" -> Reply.Html("<h1>Library</h1>")
            "/doc/1.pdf" -> Reply.Html("pdf for 1")
            else -> Reply.NotFound
        }
    })

    @Test
    fun `paths need the token`() {
        server.resolve("/").shouldBeInstanceOf<Reply.NotFound>()
        server.resolve("/wrong/").shouldBeInstanceOf<Reply.NotFound>()
        server.resolve("/abc234x/").shouldBeInstanceOf<Reply.NotFound>()
        (server.resolve("/abc234") as Reply.Html).body shouldContain "Library"
        (server.resolve("/abc234/doc/1.pdf?x=1") as Reply.Html).body shouldBe "pdf for 1"
    }

    @Test
    fun `only GET requests are read`() {
        TransferServer.readRequestPath("GET /abc/ HTTP/1.1\r\nHost: x\r\n\r\n".byteInputStream()) shouldBe "/abc/"
        TransferServer.readRequestPath("POST /abc/ HTTP/1.1\r\n\r\n".byteInputStream()) shouldBe null
        TransferServer.readRequestPath("GET http://evil/ HTTP/1.1\r\n\r\n".byteInputStream()) shouldBe null
    }

    @Test
    fun `serves over a real socket`() {
        server.start(preferredPort = 18080)
        try {
            val ok = URL("http://127.0.0.1:${server.port}/abc234/").openConnection() as HttpURLConnection
            ok.responseCode shouldBe 200
            ok.inputStream.bufferedReader().readText() shouldContain "Library"
            val denied = URL("http://127.0.0.1:${server.port}/").openConnection() as HttpURLConnection
            denied.responseCode shouldBe 404
        } finally {
            server.stop()
        }
    }

    @Test
    fun `tokens avoid look-alike characters`() {
        repeat(50) { TransferServer.newToken().none { it in "0o1lI" } shouldBe true }
    }
}

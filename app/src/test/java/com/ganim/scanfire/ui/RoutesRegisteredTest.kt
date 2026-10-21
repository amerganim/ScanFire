package com.ganim.scanfire.ui

import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Navigating to a route that isn't in the NavHost crashes at runtime, and the compiler can't see it.
 * This guards against that by checking every Routes constant is passed to composable(...).
 */
class RoutesRegisteredTest {
    @Test
    fun `every route constant has a composable destination`() {
        val source = listOf("src/main/java/com/ganim/scanfire/ui/ScanFireRoot.kt", "app/src/main/java/com/ganim/scanfire/ui/ScanFireRoot.kt")
            .map(::File).first { it.exists() }.readText()
        val routesBlock = source.substringAfter("object Routes {").substringBefore("\n}")
        val names = Regex("""const val (\w+) =""").findAll(routesBlock).map { it.groupValues[1] }.toList()
        val missing = names.filterNot { Regex("""composable\(\s*Routes\.$it\b""").containsMatchIn(source) }
        missing.shouldBeEmpty()
    }
}

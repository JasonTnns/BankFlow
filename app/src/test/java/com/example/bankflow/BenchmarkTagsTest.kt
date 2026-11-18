package com.example.bankflow

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BenchmarkTagsTest {

    private val appSources = File("src/main/java/com/example/bankflow")
    private val idsFile = File("../baselineprofile/src/main/java/com/example/bankflow/baselineprofile/Ids.kt")

    private val source: String by lazy {
        appSources.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
    }

    private fun tagsUsedByBenchmarks(): List<String> {
        assertTrue("${idsFile.path} was not found (is the test running from the app/ folder?)", idsFile.exists())
        return Regex("""const val \w+ = "([^"]+)"""").findAll(idsFile.readText()).map { it.groupValues[1] }.toList()
    }

    private fun existsInApp(tag: String): Boolean =
        if (tag.startsWith("tab_")) {
            source.contains("\"tab_\${tab.name}\"") &&
                    Regex("""\b${tag.removePrefix("tab_")}\(""").containsMatchIn(source)
        } else {
            source.contains("testTag(\"$tag\")")
        }

    @Test
    fun `every tag the benchmarks use still exists in the app`() {
        val tags = tagsUsedByBenchmarks()
        assertTrue("no tags found in Ids.kt", tags.isNotEmpty())

        val missing = tags.filterNot(::existsInApp)

        assertTrue("These tags are used by the benchmarks but not found in the app: $missing", missing.isEmpty())
    }

    @Test
    fun `the root exposes test tags as resource ids, which the benchmarks need`() {
        val root = File(appSources, "presentation/BankFlowRoot.kt").readText()

        assertTrue(root.contains("testTagsAsResourceId = true"))
    }
}
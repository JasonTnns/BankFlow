package com.example.bankflow.presentation.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NoHardcodedTextTest {

    private val root = File("src/main/java/com/example/bankflow")

    private val englishWord = Regex("\"[A-Z][a-z][^\"]*\"")

    private val sentenceTemplate = Regex("\"[^\"]*\\$[^\"]*\\s[^\"]*\"")

    private fun offences(folder: String, ignoredLines: List<String> = emptyList()): List<String> {
        val dir = File(root, folder)
        assertTrue("$folder was not found (is the test running from the app/ folder?)", dir.exists())

        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, raw ->
                val line = raw.trim()
                val skipped = line.startsWith("*") || line.startsWith("/*") || line.startsWith("//") ||
                        line.startsWith("import ") || line.startsWith("package ")
                if (skipped) return@mapIndexedNotNull null

                val code = line.substringBefore("//")
                if (ignoredLines.any { code.contains(it) }) return@mapIndexedNotNull null

                if (englishWord.containsMatchIn(code) || sentenceTemplate.containsMatchIn(code)) {
                    "${file.relativeTo(root)}:${index + 1}: $line"
                } else {
                    null
                }
            }
        }.toList()
    }

    @Test
    fun `screens and view models read their text from resources`() {
        val found = offences("presentation")

        assertTrue("Hard-coded text found:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun `the domain layer carries codes, never sentences for the user`() {
        val found = offences("domain", ignoredLines = listOf("require(", "check(", "error(", "throw "))

        assertTrue("User-facing text found in the domain layer:\n" + found.joinToString("\n"), found.isEmpty())
    }
}
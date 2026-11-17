package com.example.bankflow.presentation.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NoHardcodedTextTest {

    private val sourceRoot = File("src/main/java/com/example/bankflow")

    private val migrated = listOf(
        "presentation/common/ErrorMessages.kt",
        "presentation/transfer/TransferMessages.kt",
        "presentation/components/Components.kt",
        "presentation/security/LockScreen.kt",
        "presentation/security/SecuritySection.kt",
        "presentation/navigation/AppNavHost.kt",
        "presentation/BankFlowRoot.kt",
    )

    private val textLiteral = Regex("\\bText\\(\\s*(?:text\\s*=\\s*)?\"")
    private val describedLiteral = Regex("contentDescription\\s*=\\s*\"")
    private val returnedSentence = Regex("->\\s*\"")

    @Test
    fun `migrated files read their text from resources`() {
        val offences = migrated.flatMap { path ->
            val file = File(sourceRoot, path)
            assertTrue("$path was not found (is the test running from the app/ folder?)", file.exists())
            val checks = buildList {
                add(textLiteral)
                add(describedLiteral)
                if (path.endsWith("Messages.kt")) add(returnedSentence)
            }
            file.readLines().mapIndexedNotNull { index, line ->
                if (checks.any { it.containsMatchIn(line) }) "$path:${index + 1}: ${line.trim()}" else null
            }
        }

        assertTrue("Hard-coded text found:\n" + offences.joinToString("\n"), offences.isEmpty())
    }
}
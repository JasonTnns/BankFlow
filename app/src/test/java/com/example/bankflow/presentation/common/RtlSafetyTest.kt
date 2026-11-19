package com.example.bankflow.presentation.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RtlSafetyTest {

    private val root = File("src/main/java/com/example/bankflow/presentation")

    private val unsafe = listOf(
        Regex("""TextAlign\.(Left|Right)\b"""),
        Regex("""\babsolute(Padding|Offset)\b"""),
        Regex("""\.padding\(\s*(left|right)\s*="""),
        Regex("""Arrangement\.(Left|Right)\b"""),
        Regex("""Icons\.(Filled|Outlined|Rounded|Sharp|TwoTone)\.(ArrowBack|ArrowForward|KeyboardArrowLeft|KeyboardArrowRight|Logout|Send|ExitToApp)\b"""),
    )

    @Test
    fun `presentation code uses layout-aware alignment and mirrored icons`() {
        assertTrue("presentation/ was not found (is the test running from the app/ folder?)", root.exists())

        val offences = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.trim()
                val comment = code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")
                if (!comment && unsafe.any { it.containsMatchIn(code) }) {
                    "${file.relativeTo(root)}:${index + 1}: $code"
                } else {
                    null
                }
            }
        }.toList()

        assertTrue("Not right-to-left safe:\n" + offences.joinToString("\n"), offences.isEmpty())
    }
}
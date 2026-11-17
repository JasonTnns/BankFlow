package com.example.bankflow.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourcesTest {

    private val resDir = File("src/main/res")
    private val placeholder = Regex("%(?:(\\d+)\\$)?[sdf]")

    private fun load(file: File): List<Pair<String, String>> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).mapNotNull { index ->
            val node = nodes.item(index)
            val attributes = node.attributes
            if (attributes.getNamedItem("translatable")?.nodeValue == "false") {
                null
            } else {
                attributes.getNamedItem("name").nodeValue to node.textContent
            }
        }
    }

    private val default = load(File(resDir, "values/strings.xml"))

    private fun placeholders(text: String) = placeholder.findAll(text).map { it.groupValues[1] }.toList()

    @Test
    fun `names are unique`() {
        val names = default.map { it.first }
        assertEquals("duplicate names: ${names.groupBy { it }.filter { it.value.size > 1 }.keys}", names.size, names.toSet().size)
    }

    @Test
    fun `a string with several placeholders numbers them, so translations can reorder`() {
        val offenders = default.filter { (_, text) ->
            val found = placeholders(text)
            found.size > 1 && found.any { it.isEmpty() }
        }.map { it.first }

        assertTrue("use %1\$s, %2\$s...: $offenders", offenders.isEmpty())
    }

    @Test
    fun `every translation has exactly the same strings and placeholders as the default`() {
        val defaults = default.toMap()
        val translations = resDir.listFiles { file -> file.isDirectory && file.name.startsWith("values-") }.orEmpty()
            .map { File(it, "strings.xml") }
            .filter { it.exists() }

        translations.forEach { file ->
            val translated = load(file).toMap()
            val label = file.parentFile.name
            assertEquals("$label: missing or extra strings", defaults.keys, translated.keys)
            defaults.forEach { (name, text) ->
                assertEquals(
                    "$label/$name: placeholders differ",
                    placeholders(text).sorted(),
                    placeholders(translated.getValue(name)).sorted(),
                )
            }
        }
    }
}
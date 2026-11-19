package com.example.bankflow.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourcesTest {

    private val resDir = File("src/main/res")
    private val placeholder = Regex("%(?:\\d+\\$)?[sdf]")
    private val validQuantities = setOf("zero", "one", "two", "few", "many", "other")

    private val requiredQuantities = mapOf(
        "ar" to validQuantities,
        "fr" to setOf("one", "many", "other"),
    )

    private class Loaded(
        val stringList: List<Pair<String, String>>,
        val plurals: Map<String, Map<String, String>>,
    ) {
        val strings: Map<String, String> get() = stringList.toMap()
    }

    private fun load(file: File): Loaded {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

        val strings = mutableListOf<Pair<String, String>>()
        val stringNodes = document.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i) as Element
            if (node.getAttribute("translatable") != "false") {
                strings += node.getAttribute("name") to node.textContent
            }
        }

        val plurals = linkedMapOf<String, Map<String, String>>()
        val pluralNodes = document.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val node = pluralNodes.item(i) as Element
            val items = node.getElementsByTagName("item")
            plurals[node.getAttribute("name")] = (0 until items.length).associate { index ->
                val item = items.item(index) as Element
                item.getAttribute("quantity") to item.textContent
            }
        }
        return Loaded(strings, plurals)
    }

    private fun tokens(text: String): List<String> = placeholder.findAll(text).map { it.value }.toList()

    private val default = load(File(resDir, "values/strings.xml"))

    private val translations: List<Pair<String, File>> =
        resDir.listFiles { file -> file.isDirectory && file.name.startsWith("values-") }.orEmpty()
            .map { it.name.removePrefix("values-") to File(it, "strings.xml") }
            .filter { it.second.exists() }


    @Test
    fun `names are unique`() {
        val names = default.stringList.map { it.first } + default.plurals.keys
        assertEquals(
            "duplicate names: ${names.groupBy { it }.filter { it.value.size > 1 }.keys}",
            names.size,
            names.toSet().size,
        )
    }

    @Test
    fun `a string with several placeholders numbers them, so translations can reorder`() {
        val offenders = default.stringList.filter { (_, text) ->
            val found = tokens(text)
            found.size > 1 && found.any { !it.contains('$') }
        }.map { it.first }

        assertTrue("use %1\$s, %2\$s...: $offenders", offenders.isEmpty())
    }

    @Test
    fun `no string is empty`() {
        val empty = default.stringList.filter { it.second.isBlank() }.map { it.first }
        assertTrue("empty strings: $empty", empty.isEmpty())
    }


    @Test
    fun `arabic exists and the manifest supports right-to-left`() {
        assertTrue(File(resDir, "values-ar/strings.xml").exists())
        assertTrue(File("src/main/AndroidManifest.xml").readText().contains("android:supportsRtl=\"true\""))
    }

    @Test
    fun `every translation has exactly the same strings and placeholders as the default`() {
        val defaults = default.strings

        translations.forEach { (label, file) ->
            val translated = load(file).strings
            assertEquals("$label: missing or extra strings", defaults.keys, translated.keys)
            defaults.forEach { (name, text) ->
                assertEquals(
                    "$label/$name: placeholders differ",
                    tokens(text).sorted(),
                    tokens(translated.getValue(name)).sorted(),
                )
            }
            val blank = translated.filter { it.value.isBlank() }.keys
            assertTrue("$label: empty translations $blank", blank.isEmpty())
        }
    }

    @Test
    fun `every translation has the same plurals, with every form its language needs`() {
        translations.forEach { (label, file) ->
            val translated = load(file).plurals
            assertEquals("$label: missing or extra plurals", default.plurals.keys, translated.keys)

            val language = label.substringBefore('-')
            val required = requiredQuantities[language] ?: setOf("other")

            default.plurals.forEach { (name, defaultItems) ->
                val items = translated.getValue(name)
                val everyToken = defaultItems.values.flatMap(::tokens).toSet()

                assertTrue("$label/$name: unknown plural form", items.keys.all { it in validQuantities })
                assertTrue("$label/$name: missing forms ${required - items.keys}", items.keys.containsAll(required))
                assertEquals(
                    "$label/$name: the 'other' form must carry every placeholder",
                    everyToken,
                    tokens(items.getValue("other")).toSet(),
                )
                items.forEach { (quantity, text) ->
                    assertTrue(
                        "$label/$name/$quantity: uses a placeholder the default does not have",
                        tokens(text).all { it in everyToken },
                    )
                }
            }
        }
    }
}
package dev.jsjh.timebox

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class StringResourceParityTest {
    private val localeDirectories = listOf(
        "values-ko",
        "values-es",
        "values-hi",
        "values-fil",
        "values-zu",
        "values-fa",
        "values-sn",
        "values-fr"
    )

    @Test
    fun `localized strings match default keys and placeholders`() {
        val resourceRoot = resourceRoot()
        val defaultStrings = readStrings(resourceRoot.resolve("values/strings.xml"))

        localeDirectories.forEach { directory ->
            val localizedStrings = readStrings(resourceRoot.resolve("$directory/strings.xml"))
            assertEquals("String keys differ for $directory", defaultStrings.keys, localizedStrings.keys)

            defaultStrings.forEach { (name, defaultValue) ->
                val localizedValue = localizedStrings.getValue(name)
                assertTrue("Blank localized value for $directory/$name", localizedValue.isNotBlank())
                assertEquals(
                    "Format placeholders differ for $directory/$name",
                    placeholders(defaultValue),
                    placeholders(localizedValue)
                )
            }
        }
    }

    @Test
    fun `tutorial copy is localized for every supported locale`() {
        val resourceRoot = resourceRoot()
        val defaultStrings = readStrings(resourceRoot.resolve("values/strings.xml"))
        val tutorialKeys = defaultStrings.keys.filter { name ->
            name.startsWith("tutorial_") || name.startsWith("settings_tutorial_")
        }

        assertTrue("No tutorial strings found in default resources", tutorialKeys.isNotEmpty())
        localeDirectories.forEach { directory ->
            val localizedStrings = readStrings(resourceRoot.resolve("$directory/strings.xml"))
            val copiedEnglishKeys = tutorialKeys.filter { name ->
                localizedStrings[name] == defaultStrings[name]
            }
            assertTrue(
                "English tutorial copy remains in $directory: $copiedEnglishKeys",
                copiedEnglishKeys.isEmpty()
            )
        }
    }

    private fun resourceRoot(): Path =
        sequenceOf(Path.of("src/main/res"), Path.of("app/src/main/res"))
            .first { Files.isDirectory(it) }

    private fun readStrings(path: Path): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(path.toFile())
        val nodes = document.getElementsByTagName("string")
        return buildMap {
            repeat(nodes.length) { index ->
                val element = nodes.item(index) as Element
                put(element.getAttribute("name"), element.textContent)
            }
        }
    }

    private fun placeholders(value: String): List<String> =
        PLACEHOLDER.findAll(value).map { it.value }.sorted().toList()

    private companion object {
        val PLACEHOLDER = Regex("%\\d+\\$[sd]")
    }
}

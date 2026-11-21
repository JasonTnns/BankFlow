package com.example.bankflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Settings that must stay true for a release build. Runs from the app/ module folder (Gradle's default),
 * so a mistake fails the unit tests instead of reaching a store.
 */
class ReleaseHardeningTest {

    private val manifest by lazy { File("src/main/AndroidManifest.xml").readText() }
    private val buildScript by lazy { File("build.gradle.kts").readText() }
    private val sources = File("src/main/java/com/example/bankflow")

    @Test
    fun `backups are off, nothing is debuggable and plain HTTP is not enabled for every build`() {
        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertFalse(manifest.contains("android:debuggable"))
        assertFalse(manifest.contains("usesCleartextTraffic"))
        assertFalse(manifest.contains("networkSecurityConfig"))
    }

    @Test
    fun `only the launcher activity is exported`() {
        assertEquals(1, Regex("""android:exported="true"""").findAll(manifest).count())
    }

    @Test
    fun `the app asks for exactly the permissions it needs`() {
        val permissions = Regex("""<uses-permission android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()

        assertEquals(setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"), permissions)
    }

    @Test
    fun `plain HTTP exists only in the debug and profiling source sets`() {
        val allowed = setOf("debug", "minified", "benchmarkRelease", "nonMinifiedRelease")
        val withCleartext = File("src").listFiles { file -> file.isDirectory }.orEmpty()
            .filter { File(it, "res/xml/network_security_config.xml").exists() }
            .map { it.name }

        assertTrue("cleartext allowed in: $withCleartext", withCleartext.all { it in allowed })
        assertFalse(File("src/main/res/xml/network_security_config.xml").exists())
        assertFalse(File("src/release").exists())
    }

    @Test
    fun `release builds are shrunk, signed from outside the repository, and pointed at a property`() {
        assertTrue(Regex("""release\s*\{[^}]*isMinifyEnabled\s*=\s*true""").containsMatchIn(buildScript))
        assertTrue(buildScript.contains("releaseSigningReady"))
        assertTrue(buildScript.contains("bankflow.apiBaseUrl"))
        assertFalse("no password may be written into the build script", Regex("""storePassword\s*=\s*"[^"]""").containsMatchIn(buildScript))
        assertTrue(File("proguard-rules.pro").readText().contains("kotlinx.serialization"))
    }

    @Test
    fun `no credentials or key files are tracked next to the project`() {
        val root = File("..")
        val stray = root.walkTopDown().maxDepth(3)
            .filter { it.isFile && (it.extension == "jks" || it.extension == "keystore" || it.name == "keystore.properties") }
            .map { it.path }.toList()

        assertTrue("key material inside the project: $stray", stray.isEmpty())
    }

    @Test
    fun `nothing is printed to the log except the debug-only HTTP logger`() {
        val offences = sources.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "HttpClientFactory.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.trim()
                    val comment = code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")
                    val logs = Regex("""\bLog\.[a-z]\(|\bprintln\(|printStackTrace|System\.out""").containsMatchIn(code)
                    if (!comment && logs) "${file.relativeTo(sources)}:${index + 1}: $code" else null
                }
            }.toList()

        assertTrue("Logging found:\n" + offences.joinToString("\n"), offences.isEmpty())
    }

    @Test
    fun `http logging never includes bodies or headers`() {
        val factory = File(sources, "core/network/HttpClientFactory.kt").readText()

        assertFalse(Regex("""LogLevel\.(BODY|ALL|HEADERS)""").containsMatchIn(factory))
        assertTrue(factory.contains("BuildConfig.DEBUG") || factory.contains("enableLogging"))
    }
}
package com.jawtrack.corelogic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Enforces JawTrackSpec §6.1/§12 structurally: "no INTERNET permission" must fail loudly
 * if it ever regresses, not just be a comment someone can forget. Runs as plain JVM code
 * (core-logic has no Android SDK dependency) so it works even where the Android module
 * itself can't be compiled, e.g. this build environment.
 */
class PrivacyManifestTest {

    private fun findAppManifest(): File {
        // core-logic and app are sibling modules; walk up from the working directory to the repo root.
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(5) {
            val candidate = File(dir, "app/src/main/AndroidManifest.xml")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not locate app/src/main/AndroidManifest.xml from ${System.getProperty("user.dir")}")
    }

    @Test
    fun `manifest never declares INTERNET permission`() {
        val manifestText = findAppManifest().readText()
        val internetPermissionTag = Regex("""<uses-permission[^>]*name="android\.permission\.INTERNET"""")

        assertFalse(
            internetPermissionTag.containsMatchIn(manifestText),
            "AndroidManifest.xml must never request INTERNET (JawTrackSpec §6.1: audio never leaves the device)"
        )
    }

    @Test
    fun `manifest declares the microphone and foreground service permissions Phase 1 needs`() {
        val manifestText = findAppManifest().readText()

        listOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_MICROPHONE",
            "android.permission.WAKE_LOCK"
        ).forEach { permission ->
            assertTrue(manifestText.contains(permission), "expected manifest to declare $permission")
        }
    }

    @Test
    fun `manifest disables backup so clips can never leave the device via cloud backup`() {
        val manifestText = findAppManifest().readText()

        assertTrue(
            manifestText.contains("android:allowBackup=\"false\""),
            "JawTrackSpec §6.6: allowBackup must be false so encrypted clips can't be exfiltrated via auto-backup"
        )
    }

    @Test
    fun `recording service declares microphone foreground service type`() {
        val manifestText = findAppManifest().readText()

        assertTrue(manifestText.contains("android:foregroundServiceType=\"microphone\""))
    }
}

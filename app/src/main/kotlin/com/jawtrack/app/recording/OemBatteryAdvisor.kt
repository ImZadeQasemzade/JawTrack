package com.jawtrack.app.recording

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

data class OemGuidance(
    val manufacturerLabel: String,
    val steps: List<String>,
    /** Best-effort deep link into the OEM's autostart/background-activity manager, if resolvable. */
    val oemSettingsIntent: Intent?
)

/**
 * Per-manufacturer guidance for surviving Doze and OEM battery killers (JawTrackSpec §4.6.1).
 * A silently-killed service is the worst failure mode this app has, so onboarding walks the
 * user through both the standard Android battery-optimization exemption and whatever
 * OEM-specific "autostart" setting their phone hides behind.
 */
object OemBatteryAdvisor {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Intent for the standard Android "ignore battery optimizations" system dialog. */
    fun batteryOptimizationExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun appDetailsSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun guidanceFor(context: Context): OemGuidance {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val candidate = OEM_ACTIVITIES[manufacturerKeyFor(manufacturer)]
        val resolvedIntent = candidate?.let { (pkg, cls) ->
            Intent().setComponent(ComponentName(pkg, cls)).takeIf {
                it.resolveActivity(context.packageManager) != null
            }
        }

        val steps = when (manufacturerKeyFor(manufacturer)) {
            "xiaomi" -> listOf(
                "Open Security app -> Permissions -> Autostart, and enable it for JawTrack.",
                "Settings -> Apps -> Manage apps -> JawTrack -> Battery saver -> No restrictions."
            )
            "oppo", "realme", "oneplus" -> listOf(
                "Settings -> Battery -> JawTrack -> Allow background activity.",
                "Open the recent-apps list, find JawTrack, and tap the lock icon so it isn't swiped away."
            )
            "huawei", "honor" -> listOf(
                "Phone Manager -> App launch -> JawTrack -> switch to Manage manually and enable all three toggles (auto-launch, secondary launch, run in background)."
            )
            "vivo" -> listOf(
                "i Manager -> App manager -> Autostart -> enable for JawTrack.",
                "Settings -> Battery -> Background power consumption management -> allow JawTrack."
            )
            "samsung" -> listOf(
                "Settings -> Apps -> JawTrack -> Battery -> set to Unrestricted.",
                "Settings -> Battery and device care -> Background usage limits -> remove JawTrack from any sleeping-apps list."
            )
            else -> listOf(
                "Look for a battery/autostart/background-activity setting for JawTrack in your phone's system settings and allow it to run unrestricted overnight."
            )
        }

        return OemGuidance(
            manufacturerLabel = Build.MANUFACTURER,
            steps = steps,
            oemSettingsIntent = resolvedIntent
        )
    }

    private fun manufacturerKeyFor(manufacturer: String): String = when {
        "xiaomi" in manufacturer || "redmi" in manufacturer || "poco" in manufacturer -> "xiaomi"
        "oppo" in manufacturer -> "oppo"
        "realme" in manufacturer -> "realme"
        "oneplus" in manufacturer -> "oneplus"
        "huawei" in manufacturer -> "huawei"
        "honor" in manufacturer -> "honor"
        "vivo" in manufacturer -> "vivo"
        "samsung" in manufacturer -> "samsung"
        else -> "other"
    }

    // Best-effort component names; these change across OEM firmware versions, hence the
    // resolveActivity() check before ever offering the intent, with app-details as fallback.
    private val OEM_ACTIVITIES: Map<String, Pair<String, String>> = mapOf(
        "xiaomi" to ("com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        "huawei" to ("com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        "honor" to ("com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        "vivo" to ("com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        "oppo" to ("com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        "realme" to ("com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity")
    )
}

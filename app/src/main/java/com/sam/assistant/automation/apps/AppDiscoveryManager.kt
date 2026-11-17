package com.sam.assistant.automation.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log

data class DiscoveredApp(
    val label: String,
    val packageName: String,
    val mainActivity: String?,
    val isSystemApp: Boolean
)

class AppDiscoveryManager(private val context: Context) {

    companion object {
        private const val TAG = "AppDiscoveryManager"

        // Common app aliases / synonyms
        private val aliasMap = mapOf(
            "yt" to "youtube",
            "yt music" to "youtube music",
            "browser" to "brave",
            "google" to "chrome",
            "photos" to "gallery",
            "dialer" to "phone",
            "sms" to "messages"
        )
    }

    /**
     * Dynamically discovers all installed applications with a launchable main activity.
     */
    fun getLaunchableApps(): List<DiscoveredApp> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveList: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)
        val discovered = mutableListOf<DiscoveredApp>()

        for (info in resolveList) {
            val label = info.loadLabel(pm).toString()
            val pkg = info.activityInfo.packageName
            val activity = info.activityInfo.name
            val isSystem = (info.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            discovered.add(
                DiscoveredApp(
                    label = label,
                    packageName = pkg,
                    mainActivity = activity,
                    isSystemApp = isSystem
                )
            )
        }

        Log.d(TAG, "Discovered ${discovered.size} launchable apps on device via PackageManager.")
        return discovered
    }

    /**
     * Resolves an app query (e.g. "brave", "youtube", "yt", "photos") to a launchable package name using exact and fuzzy matching.
     */
    fun resolveApp(query: String): DiscoveredApp? {
        val clean = query.trim().lowercase()
        val canonicalQuery = aliasMap[clean] ?: clean

        val apps = getLaunchableApps()

        // 1. Exact label or package match
        apps.find { it.label.equals(canonicalQuery, ignoreCase = true) || it.packageName.equals(canonicalQuery, ignoreCase = true) }?.let {
            return it
        }

        // 2. Contains match
        apps.find { it.label.contains(canonicalQuery, ignoreCase = true) || canonicalQuery.contains(it.label, ignoreCase = true) }?.let {
            return it
        }

        // 3. Package name suffix match (e.g. "com.brave.browser" for "brave")
        apps.find { it.packageName.substringAfterLast(".").contains(canonicalQuery, ignoreCase = true) }?.let {
            return it
        }

        Log.w(TAG, "No installed launchable application matched query: \"$query\"")
        return null
    }

    /**
     * Safely launches an application by query or package.
     */
    fun launchApp(queryOrPackage: String): Boolean {
        val app = resolveApp(queryOrPackage)
        val pkg = app?.packageName ?: queryOrPackage
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(launchIntent)
            Log.d(TAG, "Successfully dispatched launch intent for $pkg")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $pkg", e)
            false
        }
    }
}

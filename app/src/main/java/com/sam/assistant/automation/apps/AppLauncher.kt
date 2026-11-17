package com.sam.assistant.automation.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.sam.assistant.automation.executor.ErrorRecoveryManager
import com.sam.assistant.automation.executor.ExecutionResult
import java.net.URLEncoder

data class AppInfo(
    val label: String,
    val packageName: String
)

class AppLauncher(private val context: Context) {

    val discoveryManager = AppDiscoveryManager(context)
    val resultVerifier = com.sam.assistant.automation.core.ResultVerifier(context)

    private val commonPackageMap = mapOf(
        "brave" to "com.brave.browser",
        "brave browser" to "com.brave.browser",
        "chrome" to "com.android.chrome",
        "google chrome" to "com.android.chrome",
        "youtube" to "com.google.android.youtube",
        "youtube music" to "com.google.android.apps.youtube.music",
        "yt music" to "com.google.android.apps.youtube.music",
        "spotify" to "com.spotify.music",
        "whatsapp" to "com.whatsapp",
        "camera" to "com.google.android.GoogleCamera",
        "settings" to "com.android.settings",
        "maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "telegram" to "org.telegram.messenger",
        "instagram" to "com.instagram.android",
        "clock" to "com.google.android.deskclock",
        "calculator" to "com.google.android.calculator",
        "calendar" to "com.google.android.calendar",
        "photos" to "com.google.android.apps.photos",
        "files" to "com.google.android.documentsui",
        "firefox" to "org.mozilla.firefox",
        "edge" to "com.microsoft.emmx"
    )

    fun isAppInstalled(nameOrPackage: String): Boolean {
        val query = nameOrPackage.trim().lowercase()
        val pm = context.packageManager

        // Check map
        val directPackage = commonPackageMap[query]
        if (directPackage != null) {
            try {
                pm.getPackageInfo(directPackage, 0)
                return true
            } catch (_: PackageManager.NameNotFoundException) {}
        }

        // Check directly by package name
        try {
            pm.getPackageInfo(query, 0)
            return true
        } catch (_: PackageManager.NameNotFoundException) {}

        // Check launcher activities by label
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = pm.queryIntentActivities(launcherIntent, 0)
        return resolveInfoList.any { info ->
            val label = info.loadLabel(pm).toString().lowercase()
            label == query || label.contains(query) || query.contains(label)
        }
    }

    fun getPackageForApp(name: String): String? {
        val query = name.trim().lowercase()
        val direct = commonPackageMap[query]
        if (direct != null && isPackageInstalled(direct)) return direct

        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = pm.queryIntentActivities(launcherIntent, 0)
        for (info in resolveInfoList) {
            val label = info.loadLabel(pm).toString().lowercase()
            if (label == query || label.contains(query)) {
                return info.activityInfo.packageName
            }
        }
        return direct
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun launchAppByName(name: String): ExecutionResult {
        val query = name.trim().lowercase()

        // 1. Dynamic Discovery via PackageManager
        val discovered = discoveryManager.resolveApp(query)
        if (discovered != null) {
            val verification = resultVerifier.verifyAppLaunch(discovered.packageName)
            if (verification.isVerified) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(discovered.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                if (launchIntent != null) {
                    return try {
                        context.startActivity(launchIntent)
                        ExecutionResult.Success(
                            message = "Opening ${discovered.label}.",
                            spokenResponse = "Opening ${discovered.label}."
                        )
                    } catch (e: Exception) {
                        ExecutionResult.Error("Failed to start ${discovered.label}: ${e.message}")
                    }
                }
            } else {
                return ExecutionResult.Error(verification.details)
            }
        }

        // 2. Direct package lookup fallback
        val pm = context.packageManager
        val directPackage = commonPackageMap[query]
        if (directPackage != null) {
            val intent = pm.getLaunchIntentForPackage(directPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return ExecutionResult.Success(
                    message = "Opening $name.",
                    spokenResponse = "Opening $name."
                )
            }
        }

        return ErrorRecoveryManager.appNotInstalled(name)
    }

    fun searchWithBrowser(query: String, browserName: String? = null): ExecutionResult {
        val targetBrowser = browserName?.trim()?.lowercase() ?: "browser"
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (_: Exception) {
            query.replace(" ", "+")
        }

        val searchUrl = when {
            targetBrowser.contains("brave") -> "https://search.brave.com/search?q=$encodedQuery"
            targetBrowser.contains("chrome") -> "https://www.google.com/search?q=$encodedQuery"
            else -> "https://www.google.com/search?q=$encodedQuery"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pkg = when {
            targetBrowser.contains("brave") -> getPackageForApp("brave")
            targetBrowser.contains("chrome") -> getPackageForApp("chrome")
            else -> null
        }

        if (pkg != null && isPackageInstalled(pkg)) {
            intent.setPackage(pkg)
        } else if (targetBrowser.contains("brave")) {
            // Brave requested but not installed
            return ErrorRecoveryManager.appNotInstalled("Brave Browser", "Would you like me to search using Chrome instead?")
        }

        return try {
            context.startActivity(intent)
            val appLabel = if (pkg != null && targetBrowser.contains("brave")) "Brave Browser" else if (pkg != null) "Chrome" else "web browser"
            ExecutionResult.Success(
                message = "Searching for \"$query\" in $appLabel.",
                spokenResponse = "Searching for \"$query\" in $appLabel."
            )
        } catch (e: Exception) {
            ErrorRecoveryManager.genericFailure(
                component = "AppLauncher",
                technicalError = "Failed to launch browser intent: ${e.message}",
                userExplanation = "Could not open browser to search for \"$query\"."
            )
        }
    }

    fun openUrlInBrowser(url: String, browserName: String? = null): ExecutionResult {
        val targetBrowser = browserName?.trim()?.lowercase()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pkg = targetBrowser?.let { getPackageForApp(it) }
        if (pkg != null && isPackageInstalled(pkg)) {
            intent.setPackage(pkg)
        } else if (targetBrowser != null && !isAppInstalled(targetBrowser)) {
            return ErrorRecoveryManager.appNotInstalled(targetBrowser)
        }

        return try {
            context.startActivity(intent)
            ExecutionResult.Success("Opening destination in ${browserName ?: "browser"}.")
        } catch (e: Exception) {
            ErrorRecoveryManager.genericFailure(
                component = "AppLauncher",
                technicalError = "Failed to open URL $url: ${e.message}",
                userExplanation = "Could not open URL in ${browserName ?: "browser"}."
            )
        }
    }

    fun searchYouTube(query: String): ExecutionResult {
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (_: Exception) {
            query.replace(" ", "+")
        }

        val isYtInstalled = isAppInstalled("youtube")
        if (isYtInstalled) {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                ExecutionResult.Success(
                    message = "Opening YouTube and searching for \"$query\".",
                    spokenResponse = "Opening YouTube and searching for \"$query\"."
                )
            } catch (_: Exception) {
                // Fallback to web URL for YouTube
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com/results?search_query=$encodedQuery")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                ExecutionResult.Success("Opening YouTube search for \"$query\".")
            }
        } else {
            // Open in browser
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com/results?search_query=$encodedQuery")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            return ExecutionResult.Success("Opening YouTube in your browser for \"$query\".")
        }
    }

    fun playMedia(mediaSource: String?, query: String? = null): ExecutionResult {
        val source = mediaSource?.lowercase()?.trim() ?: "youtube"
        val cleanQuery = query?.ifBlank { null }

        when {
            source.contains("brave") -> {
                if (!isAppInstalled("brave")) {
                    return ErrorRecoveryManager.appNotInstalled("Brave Browser", "Would you like me to play music on YouTube or Spotify instead?")
                }
                val destinationUrl = if (cleanQuery != null) {
                    val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                    "https://m.youtube.com/results?search_query=$encoded"
                } else {
                    "https://m.youtube.com"
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(destinationUrl)).apply {
                    setPackage(commonPackageMap["brave"])
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return try {
                    context.startActivity(intent)
                    ExecutionResult.Success(
                        message = "Opened Brave Browser with music destination: $destinationUrl. Due to browser security and autoplay restrictions, please tap play on the desired track.",
                        spokenResponse = "Opened Brave Browser with music destination. Please tap play to start listening."
                    )
                } catch (e: Exception) {
                    ErrorRecoveryManager.genericFailure(
                        component = "AppLauncher",
                        technicalError = "Failed to launch Brave with music URL: ${e.message}",
                        userExplanation = "Could not open music in Brave Browser."
                    )
                }
            }
            source.contains("spotify") -> {
                if (!isAppInstalled("spotify")) {
                    return ErrorRecoveryManager.appNotInstalled("Spotify", "Would you like to use YouTube or Brave instead?")
                }
                val intent = if (cleanQuery != null) {
                    Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${URLEncoder.encode(cleanQuery, "UTF-8")}")).apply {
                        setPackage("com.spotify.music")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    context.packageManager.getLaunchIntentForPackage("com.spotify.music")?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                return if (intent != null) {
                    try {
                        context.startActivity(intent)
                        ExecutionResult.Success("Opening Spotify" + (cleanQuery?.let { " for \"$it\"" } ?: "") + ".")
                    } catch (e: Exception) {
                        ErrorRecoveryManager.genericFailure("AppLauncher", "Spotify launch error: ${e.message}", "Could not launch Spotify.")
                    }
                } else {
                    ErrorRecoveryManager.appNotInstalled("Spotify")
                }
            }
            source.contains("youtube music") || source.contains("yt music") -> {
                if (!isAppInstalled("youtube music")) {
                    return ErrorRecoveryManager.appNotInstalled("YouTube Music", "Would you like to use the YouTube app or Brave Browser instead?")
                }
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.youtube.music")?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return if (launchIntent != null) {
                    context.startActivity(launchIntent)
                    ExecutionResult.Success("Opening YouTube Music.")
                } else {
                    ErrorRecoveryManager.appNotInstalled("YouTube Music")
                }
            }
            else -> {
                // Default YouTube
                if (cleanQuery != null) {
                    return searchYouTube(cleanQuery)
                }
                val ytLaunch = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return if (ytLaunch != null) {
                    context.startActivity(ytLaunch)
                    ExecutionResult.Success("Opening YouTube.")
                } else {
                    // Fallback to web
                    openUrlInBrowser("https://m.youtube.com")
                }
            }
        }
    }

    fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = pm.queryIntentActivities(launcherIntent, 0)
        return resolveInfoList.map { info ->
            AppInfo(
                label = info.loadLabel(pm).toString(),
                packageName = info.activityInfo.packageName
            )
        }.sortedBy { it.label }
    }
}

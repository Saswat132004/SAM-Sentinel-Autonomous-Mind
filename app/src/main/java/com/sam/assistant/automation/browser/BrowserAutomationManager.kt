package com.sam.assistant.automation.browser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.util.Log
import com.sam.assistant.automation.core.ResultVerifier
import com.sam.assistant.automation.core.VerificationReport
import com.sam.assistant.automation.executor.ExecutionResult
import java.net.URLEncoder

data class InstalledBrowser(
    val label: String,
    val packageName: String,
    val isBrave: Boolean,
    val isChrome: Boolean
)

class BrowserAutomationManager(
    private val context: Context,
    private val resultVerifier: ResultVerifier
) {
    companion object {
        private const val TAG = "BrowserAutomationManager"
        private const val BRAVE_PACKAGE = "com.brave.browser"
        private const val CHROME_PACKAGE = "com.android.chrome"
    }

    /**
     * Discovers all installed web browsers on the device.
     */
    fun getInstalledBrowsers(): List<InstalledBrowser> {
        val pm = context.packageManager
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
        val resolveList: List<ResolveInfo> = pm.queryIntentActivities(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)

        return resolveList.map { info ->
            val pkg = info.activityInfo.packageName
            val label = info.loadLabel(pm).toString()
            InstalledBrowser(
                label = label,
                packageName = pkg,
                isBrave = pkg == BRAVE_PACKAGE,
                isChrome = pkg == CHROME_PACKAGE
            )
        }
    }

    /**
     * Selects the most appropriate browser based on user preference or availability:
     * 1. Requested browser (e.g. Brave)
     * 2. Brave if installed
     * 3. Chrome if installed
     * 4. System default
     */
    fun selectBrowser(requestedBrowser: String? = null): InstalledBrowser? {
        val installed = getInstalledBrowsers()
        if (installed.isEmpty()) return null

        if (!requestedBrowser.isNullOrBlank()) {
            val clean = requestedBrowser.lowercase()
            installed.find { it.label.contains(clean, ignoreCase = true) || it.packageName.contains(clean, ignoreCase = true) }?.let {
                return it
            }
        }

        // Default priority: Brave -> Chrome -> First available
        return installed.find { it.isBrave }
            ?: installed.find { it.isChrome }
            ?: installed.firstOrNull()
    }

    /**
     * Opens a web search query in the selected browser.
     */
    fun openWebSearch(query: String, requestedBrowser: String? = null): ExecutionResult {
        val browser = selectBrowser(requestedBrowser)
        val encodedQuery = try {
            URLEncoder.encode(query.trim(), "UTF-8")
        } catch (e: Exception) {
            query.trim().replace(" ", "+")
        }

        val searchUrl = "https://search.brave.com/search?q=$encodedQuery"
        return openUrl(searchUrl, browser?.packageName, browser?.label ?: "Browser", query)
    }

    /**
     * Safely opens a destination URL in the target browser.
     */
    fun openUrl(
        url: String,
        targetPackage: String? = null,
        browserLabel: String = "Browser",
        originalQuery: String? = null
    ): ExecutionResult {
        return try {
            val safeUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (targetPackage != null) {
                    setPackage(targetPackage)
                }
            }

            context.startActivity(intent)

            val verification: VerificationReport = resultVerifier.verifyBrowserDispatch(
                browser = browserLabel,
                queryOrUrl = originalQuery ?: safeUrl
            )

            Log.d(TAG, "Browser dispatch verified: ${verification.details}")
            ExecutionResult.Success(
                message = "Opened web search in $browserLabel for \"${originalQuery ?: safeUrl}\".",
                spokenResponse = "Opening $browserLabel to search for ${originalQuery ?: "your request"}."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch browser intent", e)
            ExecutionResult.Error("Unable to open browser: ${e.message}")
        }
    }
}

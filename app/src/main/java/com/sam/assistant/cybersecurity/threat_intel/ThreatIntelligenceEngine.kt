package com.sam.assistant.cybersecurity.threat_intel

sealed class ThreatAnalysisVerdict {
    data class KnownThreatMatch(val indicator: ThreatIndicatorEntity, val userAdvice: String) : ThreatAnalysisVerdict()
    data class SuspiciousPattern(val reason: String, val confidence: Float, val userAdvice: String) : ThreatAnalysisVerdict()
    object UnverifiedClean : ThreatAnalysisVerdict()
}

class ThreatIntelligenceEngine(
    private val indicatorDao: ThreatIndicatorDao? = null,
    val importer: ThreatFeedImporter = ThreatFeedImporter(indicatorDao)
) {

    /**
     * Initializes default offline threat rules if the indicator database is empty.
     */
    suspend fun initializeDefaultRulesIfNeeded() {
        val count = indicatorDao?.getAllIndicatorsSync()?.size ?: 0
        if (count == 0) {
            val defaults = listOf(
                ThreatIndicatorEntity(
                    indicatorType = "DOMAIN",
                    indicatorValue = "malware-traffic-analysis.net",
                    threatCategory = "SECURITY_RESEARCH",
                    confidence = 0.95f,
                    sourceFeed = "Built-in Intel"
                ),
                ThreatIndicatorEntity(
                    indicatorType = "DOMAIN",
                    indicatorValue = "c2-sample-beacon.org",
                    threatCategory = "MALWARE_C2",
                    confidence = 0.99f,
                    sourceFeed = "Built-in Intel"
                ),
                ThreatIndicatorEntity(
                    indicatorType = "IP",
                    indicatorValue = "198.51.100.23",
                    threatCategory = "SUSPICIOUS_SCANNER",
                    confidence = 0.90f,
                    sourceFeed = "Built-in Intel"
                ),
                ThreatIndicatorEntity(
                    indicatorType = "DOMAIN",
                    indicatorValue = "login-secure-paypa1.com",
                    threatCategory = "PHISHING",
                    confidence = 0.98f,
                    sourceFeed = "Built-in Intel"
                )
            )
            indicatorDao?.insertIndicators(defaults)
        }
    }

    /**
     * Evaluates a domain or IP address against local threat intelligence and heuristic rules.
     */
    suspend fun evaluateIndicator(target: String): ThreatAnalysisVerdict {
        val clean = target.lowercase().trim().removeSuffix(".")

        // 1. Exact match against local threat intelligence database
        val match = indicatorDao?.findExactMatch(clean)
        if (match != null) {
            val advice = when (match.threatCategory) {
                "PHISHING" -> "High danger: Confirmed phishing indicator. Do not submit credentials or enter passwords."
                "MALWARE_C2" -> "Critical threat: Known command-and-control server. Block outbound communication."
                else -> "Caution: Known threat indicator associated with ${match.threatCategory}."
            }
            return ThreatAnalysisVerdict.KnownThreatMatch(match, advice)
        }

        // 2. Heuristic Pattern Detection
        if (clean.contains("paypa1") || clean.contains("goog1e") || clean.contains("micros0ft") || clean.contains("app1e")) {
            return ThreatAnalysisVerdict.SuspiciousPattern(
                reason = "Heuristic brand typosquatting: Domain name imitates a trusted brand with letter-digit substitution.",
                confidence = 0.85f,
                userAdvice = "Inspect domain carefully. Official services do not use character substitutions."
            )
        }

        if (clean.endsWith(".tk") || clean.endsWith(".ml") || clean.endsWith(".cf") || clean.endsWith(".buzz")) {
            return ThreatAnalysisVerdict.SuspiciousPattern(
                reason = "Domain is registered under a high-abuse disposable top-level domain.",
                confidence = 0.70f,
                userAdvice = "Exercise caution before downloading files or submitting personal details."
            )
        }

        return ThreatAnalysisVerdict.UnverifiedClean
    }
}

package com.sam.assistant.cybersecurity.dns

import kotlin.math.ln

data class DnsInspectionResult(
    val domain: String,
    val isSuspicious: Boolean,
    val reasons: List<String>,
    val riskScore: Int, // 0 to 100
    val detectedPatterns: List<String>
)

class DnsQueryAnalyzer {

    companion object {
        private val HIGH_RISK_TLDS = setOf(
            ".tk", ".ml", ".ga", ".cf", ".gq", ".buzz", ".top", ".work", ".click", ".surf"
        )

        private val PHISHING_TYPOSQUATS = listOf(
            Regex("(?i)paypa[l1i]"),
            Regex("(?i)goog[l1i]e"),
            Regex("(?i)micros[o0]ft"),
            Regex("(?i)app[l1i]e"),
            Regex("(?i)netf[l1i]x"),
            Regex("(?i)amaz[o0]n"),
            Regex("(?i)wha[t7]sapp")
        )
    }

    /**
     * Inspects a requested domain against offline heuristic security rules.
     */
    fun inspectDomain(domain: String): DnsInspectionResult {
        val cleanDomain = domain.lowercase().trim().removeSuffix(".")
        val reasons = mutableListOf<String>()
        val patterns = mutableListOf<String>()
        var score = 0

        // 1. Punycode / Internationalized Domain Name (IDN) homograph check
        if (cleanDomain.startsWith("xn--") || cleanDomain.contains(".xn--")) {
            reasons.add("Punycode (xn--) encoding detected: Homograph attacks frequently use look-alike unicode glyphs.")
            patterns.add("PUNYCODE_HOMOGRAPH")
            score += 35
        }

        // 2. High-abuse TLDs
        for (tld in HIGH_RISK_TLDS) {
            if (cleanDomain.endsWith(tld)) {
                reasons.add("Domain uses a high-abuse top-level domain ($tld) frequently associated with disposable malware campaigns.")
                patterns.add("HIGH_RISK_TLD")
                score += 35
                break
            }
        }

        // 3. Phishing / Typosquatting heuristics
        for (typoPattern in PHISHING_TYPOSQUATS) {
            val isOfficialDomain = isLegitimateDomain(cleanDomain)
            if (!isOfficialDomain && typoPattern.containsMatchIn(cleanDomain)) {
                reasons.add("Suspicious brand-lookalike name pattern detected imitating popular services.")
                patterns.add("BRAND_TYPOSQUAT")
                score += 45
                break
            }
        }

        // 4. Shannon Entropy / DGA (Domain Generation Algorithm) check
        val sld = cleanDomain.substringBeforeLast(".")
        val entropy = calculateShannonEntropy(sld)
        if (entropy > 3.8f && sld.length > 14) {
            reasons.add("High character entropy ($entropy): Domain structure exhibits algorithmic pseudo-random generation patterns typical of DGA botnets.")
            patterns.add("HIGH_ENTROPY_DGA")
            score += 35
        }

        val clampedScore = score.coerceIn(0, 100)
        return DnsInspectionResult(
            domain = cleanDomain,
            isSuspicious = clampedScore >= 35,
            reasons = reasons,
            riskScore = clampedScore,
            detectedPatterns = patterns
        )
    }

    private fun isLegitimateDomain(domain: String): Boolean {
        return domain.endsWith("paypal.com") || domain.endsWith("google.com") ||
                domain.endsWith("microsoft.com") || domain.endsWith("apple.com") ||
                domain.endsWith("netflix.com") || domain.endsWith("amazon.com") ||
                domain.endsWith("whatsapp.com")
    }

    private fun calculateShannonEntropy(input: String): Float {
        if (input.isEmpty()) return 0f
        val freqs = input.groupingBy { it }.eachCount()
        var entropy = 0.0
        val len = input.length.toDouble()
        for (count in freqs.values) {
            val p = count / len
            entropy -= p * (ln(p) / ln(2.0))
        }
        return entropy.toFloat()
    }
}

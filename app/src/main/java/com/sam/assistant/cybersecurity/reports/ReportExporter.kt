package com.sam.assistant.cybersecurity.reports

import com.google.gson.GsonBuilder

/**
 * Handles exporting and formatting security reports for clipboard, SAF, or file sharing.
 */
object ReportExporter {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun exportAsPlainText(report: SecurityReportEntity): String {
        return report.fullReportContent
    }

    fun exportAsMarkdown(report: SecurityReportEntity): String {
        return buildString {
            appendLine("# ${report.title}")
            appendLine("**Report ID:** `${report.reportId}`")
            appendLine("**Risk Score:** ${report.overallRiskScore} / 100 (`${report.riskLevel}`)")
            appendLine("**Network:** ${report.connectedNetworkSsid}")
            appendLine()
            appendLine("## Summary")
            appendLine(report.summaryText)
            appendLine()
            appendLine("## Recommendations")
            appendLine(report.recommendationsText)
            appendLine()
            appendLine("## Detailed Audit Log")
            appendLine("```text")
            appendLine(report.fullReportContent)
            appendLine("```")
        }
    }

    fun exportAsJson(report: SecurityReportEntity): String {
        val map = linkedMapOf(
            "reportId" to report.reportId,
            "title" to report.title,
            "timestamp" to report.generatedTimestamp,
            "overallRiskScore" to report.overallRiskScore,
            "riskLevel" to report.riskLevel,
            "connectedNetworkSsid" to report.connectedNetworkSsid,
            "summary" to report.summaryText,
            "recommendations" to report.recommendationsText,
            "rawAudit" to report.fullReportContent
        )
        return gson.toJson(map)
    }
}

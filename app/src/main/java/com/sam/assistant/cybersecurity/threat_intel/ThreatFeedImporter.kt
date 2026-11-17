package com.sam.assistant.cybersecurity.threat_intel

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ThreatImportResult(
    val importedCount: Int,
    val feedName: String,
    val message: String
)

class ThreatFeedImporter(private val indicatorDao: ThreatIndicatorDao? = null) {

    private val gson = Gson()

    suspend fun importThreatFeed(content: String, format: String, feedName: String = "Imported Feed"): ThreatImportResult {
        return if (format.equals("JSON", ignoreCase = true)) {
            importFromJson(content, feedName)
        } else {
            importFromCsv(content, feedName)
        }
    }

    /**
     * Imports threat indicators from CSV content:
     * Format: indicatorType,indicatorValue,threatCategory,confidence
     */
    suspend fun importFromCsv(csvContent: String, feedName: String = "User CSV Import"): ThreatImportResult {
        val lines = csvContent.lines().filter { it.isNotBlank() }
        val entities = mutableListOf<ThreatIndicatorEntity>()

        for ((index, line) in lines.withIndex()) {
            if (index == 0 && line.lowercase().contains("type")) continue // Skip header

            val parts = line.split(",").map { it.trim() }
            if (parts.size >= 2) {
                val type = parts[0].uppercase()
                val value = parts[1].lowercase()
                val category = if (parts.size >= 3) parts[2].uppercase() else "SUSPICIOUS"
                val confidence = if (parts.size >= 4) parts[3].toFloatOrNull() ?: 0.8f else 0.8f

                entities.add(
                    ThreatIndicatorEntity(
                        indicatorType = type,
                        indicatorValue = value,
                        threatCategory = category,
                        confidence = confidence,
                        sourceFeed = feedName
                    )
                )
            }
        }

        if (entities.isNotEmpty()) {
            indicatorDao?.insertIndicators(entities)
        }

        return ThreatImportResult(
            importedCount = entities.size,
            feedName = feedName,
            message = "Successfully imported ${entities.size} threat indicators from feed \"$feedName\"."
        )
    }

    /**
     * Imports threat indicators from JSON content:
     * [{"indicatorType":"IP","indicatorValue":"198.51.100.1","threatCategory":"BOTNET","confidence":0.9}]
     */
    suspend fun importFromJson(jsonContent: String, feedName: String = "User JSON Import"): ThreatImportResult {
        return try {
            val typeToken = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = gson.fromJson(jsonContent, typeToken)

            val entities = rawList.mapNotNull { map ->
                val value = map["indicatorValue"]?.toString()?.lowercase() ?: return@mapNotNull null
                val type = map["indicatorType"]?.toString()?.uppercase() ?: "DOMAIN"
                val category = map["threatCategory"]?.toString()?.uppercase() ?: "SUSPICIOUS"
                val confidence = (map["confidence"] as? Number)?.toFloat() ?: 0.85f

                ThreatIndicatorEntity(
                    indicatorType = type,
                    indicatorValue = value,
                    threatCategory = category,
                    confidence = confidence,
                    sourceFeed = feedName
                )
            }

            if (entities.isNotEmpty()) {
                indicatorDao?.insertIndicators(entities)
            }

            ThreatImportResult(
                importedCount = entities.size,
                feedName = feedName,
                message = "Successfully imported ${entities.size} threat indicators from JSON feed."
            )
        } catch (e: Exception) {
            ThreatImportResult(
                importedCount = 0,
                feedName = feedName,
                message = "JSON import failed: ${e.message}"
            )
        }
    }
}

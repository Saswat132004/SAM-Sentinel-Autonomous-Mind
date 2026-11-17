package com.sam.assistant.cybersecurity.dns

import kotlinx.coroutines.flow.Flow

class DnsMonitor(
    private val dnsDao: DnsEventDao? = null,
    val analyzer: DnsQueryAnalyzer = DnsQueryAnalyzer()
) {

    /**
     * Logs and evaluates a DNS query event locally.
     */
    suspend fun recordDnsQuery(
        domain: String,
        queryType: String = "A",
        responseIp: String? = null
    ): DnsEventEntity {
        val inspection = analyzer.inspectDomain(domain)
        val event = DnsEventEntity(
            queryDomain = domain,
            queryType = queryType,
            responseIp = responseIp,
            isFlagged = inspection.isSuspicious,
            riskReason = if (inspection.reasons.isNotEmpty()) inspection.reasons.joinToString("; ") else null
        )
        val id = dnsDao?.insertEvent(event) ?: 0L
        return event.copy(id = id)
    }

    fun getAllEvents(): Flow<List<DnsEventEntity>> = dnsDao?.getAllEvents() ?: kotlinx.coroutines.flow.emptyFlow()

    suspend fun getRecentEvents(limit: Int = 50): List<DnsEventEntity> = dnsDao?.getRecentEvents(limit) ?: emptyList()

    suspend fun getFlaggedEvents(): List<DnsEventEntity> = dnsDao?.getFlaggedEvents() ?: emptyList()

    suspend fun clearHistory() {
        dnsDao?.clearAll()
    }
}

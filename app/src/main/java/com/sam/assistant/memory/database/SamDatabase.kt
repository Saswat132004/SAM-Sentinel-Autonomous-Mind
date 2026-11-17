package com.sam.assistant.memory.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sam.assistant.agent.memory.AgentTaskDao
import com.sam.assistant.agent.memory.AgentTaskEntity
import com.sam.assistant.cybersecurity.dns.DnsEventDao
import com.sam.assistant.cybersecurity.dns.DnsEventEntity
import com.sam.assistant.cybersecurity.reports.SecurityEventDao
import com.sam.assistant.cybersecurity.reports.SecurityEventEntity
import com.sam.assistant.cybersecurity.reports.SecurityReportDao
import com.sam.assistant.cybersecurity.reports.SecurityReportEntity
import com.sam.assistant.cybersecurity.threat_detection.NetworkFingerprintDao
import com.sam.assistant.cybersecurity.threat_detection.NetworkFingerprintEntity
import com.sam.assistant.cybersecurity.threat_intel.ThreatIndicatorDao
import com.sam.assistant.cybersecurity.threat_intel.ThreatIndicatorEntity
import com.sam.assistant.cybersecurity.traffic.TrafficStatisticsDao
import com.sam.assistant.cybersecurity.traffic.TrafficStatisticsEntity
import com.sam.assistant.knowledge.documents.DocumentEntity
import com.sam.assistant.knowledge.documents.DocumentSectionEntity
import com.sam.assistant.knowledge.documents.KnowledgeDocumentDao
import com.sam.assistant.knowledge.memory.PersonalFactDao
import com.sam.assistant.knowledge.memory.PersonalFactEntity
import com.sam.assistant.knowledge.productivity.AssignmentEntity
import com.sam.assistant.knowledge.productivity.StudyPlannerDao
import com.sam.assistant.knowledge.productivity.StudySessionEntity
import com.sam.assistant.knowledge.study.FlashcardDao
import com.sam.assistant.knowledge.study.FlashcardEntity
import com.sam.assistant.memory.model.CommandLogEntity
import com.sam.assistant.memory.model.ConversationEntity
import com.sam.assistant.memory.model.MemoryEntity
import com.sam.assistant.memory.model.ReminderEntity

@Database(
    entities = [
        MemoryEntity::class,
        ConversationEntity::class,
        CommandLogEntity::class,
        ReminderEntity::class,
        AgentTaskEntity::class,
        PersonalFactEntity::class,
        DocumentEntity::class,
        DocumentSectionEntity::class,
        FlashcardEntity::class,
        StudySessionEntity::class,
        AssignmentEntity::class,
        // Stage 7: Cybersecurity Entities
        NetworkFingerprintEntity::class,
        TrafficStatisticsEntity::class,
        DnsEventEntity::class,
        ThreatIndicatorEntity::class,
        SecurityReportEntity::class,
        SecurityEventEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SamDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun commandLogDao(): CommandLogDao
    abstract fun reminderDao(): ReminderDao
    abstract fun agentTaskDao(): AgentTaskDao
    abstract fun personalFactDao(): PersonalFactDao
    abstract fun knowledgeDocumentDao(): KnowledgeDocumentDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun studyPlannerDao(): StudyPlannerDao

    // Stage 7 DAOs
    abstract fun networkFingerprintDao(): NetworkFingerprintDao
    abstract fun trafficStatisticsDao(): TrafficStatisticsDao
    abstract fun dnsEventDao(): DnsEventDao
    abstract fun threatIndicatorDao(): ThreatIndicatorDao
    abstract fun securityReportDao(): SecurityReportDao
    abstract fun securityEventDao(): SecurityEventDao

    companion object {
        @Volatile
        private var INSTANCE: SamDatabase? = null

        fun getInstance(context: Context): SamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SamDatabase::class.java,
                    "sam_memory.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

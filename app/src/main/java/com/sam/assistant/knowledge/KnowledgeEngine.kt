package com.sam.assistant.knowledge

import android.content.Context
import com.sam.assistant.automation.reminders.ReminderScheduler
import com.sam.assistant.knowledge.conversation.ConversationalMemoryManager
import com.sam.assistant.knowledge.documents.DocumentIngestionManager
import com.sam.assistant.knowledge.documents.KnowledgeDocumentDao
import com.sam.assistant.knowledge.memory.PersonalFactDao
import com.sam.assistant.knowledge.memory.PersonalMemoryEngine
import com.sam.assistant.knowledge.productivity.ProductivityManager
import com.sam.assistant.knowledge.productivity.StudyPlanner
import com.sam.assistant.knowledge.productivity.StudyPlannerDao
import com.sam.assistant.knowledge.retrieval.HybridRetrievalEngine
import com.sam.assistant.knowledge.study.CodeAssistant
import com.sam.assistant.knowledge.study.ConceptExplainer
import com.sam.assistant.knowledge.study.FlashcardDao
import com.sam.assistant.knowledge.study.FlashcardManager
import com.sam.assistant.knowledge.study.PdfAssistant
import com.sam.assistant.knowledge.study.ProjectAssistant
import com.sam.assistant.knowledge.study.QuizGenerator
import com.sam.assistant.memory.database.SamDatabase
import com.sam.assistant.memory.repository.MemoryRepository

class KnowledgeEngine(
    val context: Context,
    val database: SamDatabase,
    val memoryRepository: MemoryRepository,
    val reminderScheduler: ReminderScheduler
) {
    // 1. Personal Memory
    val personalFactDao: PersonalFactDao = database.personalFactDao()
    val memoryEngine: PersonalMemoryEngine = PersonalMemoryEngine(personalFactDao)

    // 2. Conversational Memory
    val conversationManager: ConversationalMemoryManager = ConversationalMemoryManager(memoryEngine)

    // 3. Document Ingestion
    val documentDao: KnowledgeDocumentDao = database.knowledgeDocumentDao()
    val documentIngestionManager: DocumentIngestionManager = DocumentIngestionManager(documentDao)

    // 4. Hybrid Retrieval
    val retrievalEngine: HybridRetrievalEngine = HybridRetrievalEngine(personalFactDao, documentDao)

    // 5. Study Intelligence
    val conceptExplainer: ConceptExplainer = ConceptExplainer()
    val pdfAssistant: PdfAssistant = PdfAssistant(documentDao)
    val quizGenerator: QuizGenerator = QuizGenerator()
    val flashcardDao: FlashcardDao = database.flashcardDao()
    val flashcardManager: FlashcardManager = FlashcardManager(flashcardDao)
    val codeAssistant: CodeAssistant = CodeAssistant()
    val projectAssistant: ProjectAssistant = ProjectAssistant(memoryEngine)

    // 6. Productivity & Study Planner
    val studyPlannerDao: StudyPlannerDao = database.studyPlannerDao()
    val studyPlanner: StudyPlanner = StudyPlanner(studyPlannerDao)
    val productivityManager: ProductivityManager = ProductivityManager(
        memoryRepository = memoryRepository,
        reminderScheduler = reminderScheduler,
        studyPlanner = studyPlanner
    )
}

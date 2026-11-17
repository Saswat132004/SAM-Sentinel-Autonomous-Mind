package com.sam.assistant.knowledge

import com.sam.assistant.automation.reminders.ReminderScheduler
import com.sam.assistant.knowledge.conversation.ConversationalMemoryManager
import com.sam.assistant.knowledge.conversation.FollowUpResolution
import com.sam.assistant.knowledge.documents.*
import com.sam.assistant.knowledge.memory.*
import com.sam.assistant.knowledge.productivity.*
import com.sam.assistant.knowledge.retrieval.HybridRetrievalEngine
import com.sam.assistant.knowledge.study.*
import com.sam.assistant.memory.model.CommandLogEntity
import com.sam.assistant.memory.model.ConversationEntity
import com.sam.assistant.memory.model.MemoryEntity
import com.sam.assistant.memory.model.ReminderEntity
import com.sam.assistant.memory.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class Stage6KnowledgeEngineTest {

    // In-memory DAOs
    private lateinit var fakeFactDao: FakePersonalFactDao
    private lateinit var fakeDocDao: FakeKnowledgeDocumentDao
    private lateinit var fakeFlashcardDao: FakeFlashcardDao
    private lateinit var fakeStudyPlannerDao: FakeStudyPlannerDao

    // Engines
    private lateinit var memoryEngine: PersonalMemoryEngine
    private lateinit var conversationManager: ConversationalMemoryManager
    private lateinit var documentManager: DocumentIngestionManager
    private lateinit var retrievalEngine: HybridRetrievalEngine
    private lateinit var conceptExplainer: ConceptExplainer
    private lateinit var pdfAssistant: PdfAssistant
    private lateinit var quizGenerator: QuizGenerator
    private lateinit var flashcardManager: FlashcardManager
    private lateinit var codeAssistant: CodeAssistant
    private lateinit var projectAssistant: ProjectAssistant
    private lateinit var studyPlanner: StudyPlanner

    @Before
    fun setUp() {
        fakeFactDao = FakePersonalFactDao()
        fakeDocDao = FakeKnowledgeDocumentDao()
        fakeFlashcardDao = FakeFlashcardDao()
        fakeStudyPlannerDao = FakeStudyPlannerDao()

        memoryEngine = PersonalMemoryEngine(fakeFactDao)
        conversationManager = ConversationalMemoryManager(memoryEngine)
        documentManager = DocumentIngestionManager(fakeDocDao)
        retrievalEngine = HybridRetrievalEngine(fakeFactDao, fakeDocDao)
        conceptExplainer = ConceptExplainer()
        pdfAssistant = PdfAssistant(fakeDocDao)
        quizGenerator = QuizGenerator()
        flashcardManager = FlashcardManager(fakeFlashcardDao)
        codeAssistant = CodeAssistant()
        projectAssistant = ProjectAssistant(memoryEngine)
        studyPlanner = StudyPlanner(fakeStudyPlannerDao)
    }

    // ==========================================
    // 1. Save Approved Memory
    // ==========================================
    @Test
    fun testSaveApprovedMemory() = runBlocking {
        val result = memoryEngine.saveApprovedMemory("Remember that I prefer studying in the evening.")
        assertTrue(result is SaveMemoryResult.Success)
        val success = result as SaveMemoryResult.Success
        assertEquals(MemoryCategory.STUDY_PREFERENCES.name, success.fact.category)
        assertTrue(success.fact.content.contains("studying in the evening"))
    }

    // ==========================================
    // 2. Retrieve Saved Memory
    // ==========================================
    @Test
    fun testRetrieveSavedMemory() = runBlocking {
        memoryEngine.saveApprovedMemory("Remember that my favorite browser is Brave.")
        val memories = memoryEngine.getMemoriesByCategory(MemoryCategory.APPLICATION_PREFERENCES)
        assertEquals(1, memories.size)
        assertTrue(memories.first().content.contains("Brave"))
    }

    // ==========================================
    // 3. Update Memory
    // ==========================================
    @Test
    fun testUpdateMemory() = runBlocking {
        val res = memoryEngine.saveApprovedMemory("Remember that ARES uses PostgreSQL.")
        val factId = (res as SaveMemoryResult.Success).fact.factId
        val updated = memoryEngine.updateMemory(factId, "ARES uses PostgreSQL and Redis for caching.")
        assertTrue(updated)

        val retrieved = fakeFactDao.getFactByFactId(factId)
        assertNotNull(retrieved)
        assertTrue(retrieved!!.content.contains("Redis for caching"))
    }

    // ==========================================
    // 4. Delete Individual Memory
    // ==========================================
    @Test
    fun testDeleteIndividualMemory() = runBlocking {
        val res = memoryEngine.saveApprovedMemory("Remember that workout time is 7 AM.")
        val factId = (res as SaveMemoryResult.Success).fact.factId
        val deleted = memoryEngine.deleteMemory(factId)
        assertTrue(deleted)

        val retrieved = fakeFactDao.getFactByFactId(factId)
        assertNull(retrieved)
    }

    // ==========================================
    // 5. Delete All Memory
    // ==========================================
    @Test
    fun testDeleteAllMemory() = runBlocking {
        memoryEngine.saveApprovedMemory("Fact 1")
        memoryEngine.saveApprovedMemory("Fact 2")
        assertEquals(2, memoryEngine.getAllMemoriesSync().size)

        val cleared = memoryEngine.deleteAllMemories()
        assertTrue(cleared)
        assertEquals(0, memoryEngine.getAllMemoriesSync().size)
    }

    // ==========================================
    // 6. Export Memory
    // ==========================================
    @Test
    fun testExportMemory() = runBlocking {
        memoryEngine.saveApprovedMemory("Remember that project ARES uses Kotlin.")
        val textExport = memoryEngine.exportAsText()
        assertTrue(textExport.contains("SAM PERSONAL INTELLIGENCE MEMORY EXPORT"))
        assertTrue(textExport.contains("project ARES uses Kotlin"))

        val jsonExport = memoryEngine.exportAsJson()
        assertTrue(jsonExport.contains("ARES"))
    }

    // ==========================================
    // 7. Disable Long-Term Memory
    // ==========================================
    @Test
    fun testDisableLongTermMemory() = runBlocking {
        memoryEngine.privacyController.setLongTermMemoryEnabled(false)
        val res = memoryEngine.saveApprovedMemory("Remember that test is enabled.")
        assertTrue(res is SaveMemoryResult.Denied)
        val denied = res as SaveMemoryResult.Denied
        assertTrue(denied.reason.contains("disabled"))
    }

    // ==========================================
    // 8. Retrieve Project Information
    // ==========================================
    @Test
    fun testRetrieveProjectInformation() = runBlocking {
        memoryEngine.saveApprovedMemory("Remember that ARES architecture uses PostgreSQL, Redis, and gRPC.")
        val summary = projectAssistant.getProjectDetails("ARES")
        assertEquals("ARES", summary.projectName)
        assertTrue(summary.factsCount >= 1)
        assertTrue(summary.architectureDetails.isNotEmpty())
    }

    // ==========================================
    // 9. Resolve Contextual Follow-Up
    // ==========================================
    @Test
    fun testResolveContextualFollowUp() = runBlocking {
        memoryEngine.saveApprovedMemory("Remember that ARES architecture database is PostgreSQL.")

        // Turn 1: user discusses ARES
        conversationManager.recordTurn("Explain my ARES architecture.", "ARES is your personal intelligence system.")

        // Turn 2: contextual follow-up referencing database
        val followUp = conversationManager.resolveFollowUpQuery("What database did I use?")
        assertTrue(followUp is FollowUpResolution.Resolved)
        val resolved = followUp as FollowUpResolution.Resolved
        assertTrue(resolved.answer.contains("PostgreSQL"))
        assertTrue(resolved.sourceCitation.contains("Personal Memory"))
    }

    // ==========================================
    // 10. Import Selected PDF
    // ==========================================
    @Test
    fun testImportSelectedPdf() = runBlocking {
        val result = documentManager.ingestPdf(
            fileName = "operating_systems_ch1.pdf",
            extractedText = "Operating Systems manage hardware resources. Process scheduling allocates CPU time.",
            pageCount = 12,
            sizeBytes = 204800L
        )
        assertTrue(result is IngestionResult.Success)
        val success = result as IngestionResult.Success
        assertEquals(12, success.document.pageCount)
        assertFalse(success.document.isScanned)
    }

    // ==========================================
    // 11. Extract PDF Text & Section Chunking
    // ==========================================
    @Test
    fun testExtractPdfTextAndSectionChunking() = runBlocking {
        val sampleText = "Chapter 1: Virtual Memory.\n\nPaging divides memory into frames.\n\nTLB caches address translations."
        val result = documentManager.ingestPdf(
            fileName = "vm_notes.pdf",
            extractedText = sampleText,
            pageCount = 3,
            sizeBytes = 50000L
        )
        assertTrue(result is IngestionResult.Success)
        val docId = (result as IngestionResult.Success).document.docId
        val sections = fakeDocDao.getSectionsForDocument(docId)
        assertTrue(sections.isNotEmpty())
        assertTrue(sections.first().content.contains("Virtual Memory"))
    }

    // ==========================================
    // 12. Detect Scanned PDF Limitation
    // ==========================================
    @Test
    fun testDetectScannedPdfLimitation() = runBlocking {
        // Scanned PDF: pageCount > 0 but extractedText is blank
        val result = documentManager.ingestPdf(
            fileName = "scanned_handwritten_notes.pdf",
            extractedText = "   ",
            pageCount = 8,
            sizeBytes = 1500000L
        )
        assertTrue(result is IngestionResult.ScannedLimitation)
        val limitation = result as IngestionResult.ScannedLimitation
        assertTrue(limitation.document.isScanned)
        assertTrue(limitation.warning.contains("OCR"))
    }

    // ==========================================
    // 13. Search Exact Technical Terms (BM25)
    // ==========================================
    @Test
    fun testSearchExactTechnicalTerms() = runBlocking {
        documentManager.ingestText(
            fileName = "dsa.txt",
            content = "Dijkstra algorithm computes shortest path. Bellman-Ford handles negative weights."
        )
        val results = retrievalEngine.retrieve("Dijkstra")
        assertTrue(results.isNotEmpty())
        assertEquals("dsa.txt - Section 1", results.first().title)
        assertTrue(results.first().content.contains("shortest path"))
    }

    // ==========================================
    // 14. Semantic & Hybrid Retrieval
    // ==========================================
    @Test
    fun testSemanticAndHybridRetrieval() = runBlocking {
        memoryEngine.saveApprovedMemory("Remember that ARES backend uses Redis cache.")
        val results = retrievalEngine.retrieve(query = "ARES Redis", activeTopic = "ARES")
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().score > 0.4f)
        assertTrue(results.first().sourceCitation.contains("Personal Memory"))
    }

    // ==========================================
    // 15. Generate Study Revision Notes
    // ==========================================
    @Test
    fun testGenerateStudyNotes() = runBlocking {
        val result = documentManager.ingestPdf(
            fileName = "os_summary.pdf",
            extractedText = "Deadlock prevention requires breaking circular wait. Paging avoids external fragmentation.",
            pageCount = 5,
            sizeBytes = 120000L
        )
        val docId = (result as IngestionResult.Success).document.docId
        val notes = pdfAssistant.generateRevisionNotes(docId)
        assertNotNull(notes)
        assertTrue(notes!!.revisionPoints.isNotEmpty())
    }

    // ==========================================
    // 16. Generate Quizzes with Explanations
    // ==========================================
    @Test
    fun testGenerateQuizzes() {
        val quiz = quizGenerator.generateQuiz("Operating Systems", "INTERMEDIATE", 2)
        assertEquals(2, quiz.questions.size)
        val q1 = quiz.questions.first()
        assertEquals(4, q1.options.size)
        assertTrue(q1.explanation.isNotBlank())
    }

    // ==========================================
    // 17. Create Flashcards & Spaced Repetition
    // ==========================================
    @Test
    fun testCreateFlashcardsAndSpacedRepetition() = runBlocking {
        val cardId = flashcardManager.createFlashcard(
            deckName = "OS",
            question = "What is Coffman condition #4?",
            answer = "Circular Wait",
            topic = "Operating Systems"
        )
        assertTrue(cardId > 0)

        // Initial mastery level is 0
        val card1 = flashcardManager.recordReview(cardId, wasCorrect = true)
        assertNotNull(card1)
        assertEquals(1, card1!!.masteryLevel)
        assertEquals(1, card1.reviewCount)

        // Second correct review advances mastery to 2
        val card2 = flashcardManager.recordReview(cardId, wasCorrect = true)
        assertEquals(2, card2!!.masteryLevel)
    }

    // ==========================================
    // 18. Create Study Reminders
    // ==========================================
    @Test
    fun testCreateStudyReminders() = runBlocking {
        val fakeRepo = FakeMemoryRepository()
        val fakeScheduler = object : ReminderScheduler(android.content.ContextWrapper(null), fakeRepo) {
            override suspend fun createReminder(title: String, delayMinutes: Int): com.sam.assistant.automation.executor.ExecutionResult {
                return com.sam.assistant.automation.executor.ExecutionResult.Success("Reminder set.")
            }
        }
        val prodManager = ProductivityManager(fakeRepo, fakeScheduler, studyPlanner)

        val res = prodManager.scheduleStudyReminder(
            message = "Operating Systems Revision",
            targetTimeMs = System.currentTimeMillis() + 3600000L
        )
        assertTrue(res is ScheduleReminderResult.Scheduled)
        assertTrue((res as ScheduleReminderResult.Scheduled).reminderId > 0)
    }

    // ==========================================
    // 19. Prevent Duplicate Reminders
    // ==========================================
    @Test
    fun testPreventDuplicateReminders() = runBlocking {
        val fakeRepo = FakeMemoryRepository()
        val targetTime = System.currentTimeMillis() + 3600000L
        // Pre-insert an existing reminder
        fakeRepo.saveReminder("Study Operating Systems", targetTime)

        val fakeScheduler = object : ReminderScheduler(android.content.ContextWrapper(null), fakeRepo) {
            override suspend fun createReminder(title: String, delayMinutes: Int): com.sam.assistant.automation.executor.ExecutionResult {
                return com.sam.assistant.automation.executor.ExecutionResult.Success("Reminder set.")
            }
        }
        val prodManager = ProductivityManager(fakeRepo, fakeScheduler, studyPlanner)

        val res = prodManager.scheduleStudyReminder(
            message = "Study Operating Systems",
            targetTimeMs = targetTime + 60000L // 1 minute difference (within 5-min threshold)
        )
        assertTrue(res is ScheduleReminderResult.DuplicatePrevented)
        val dup = res as ScheduleReminderResult.DuplicatePrevented
        assertTrue(dup.reason.contains("Duplicate creation prevented"))
    }

    // ==========================================
    // 20. Delete Document and Associated Index
    // ==========================================
    @Test
    fun testDeleteDocumentAndIndex() = runBlocking {
        val result = documentManager.ingestText("to_delete.txt", "Some temporary study notes.")
        val docId = (result as IngestionResult.Success).document.docId

        assertTrue(fakeDocDao.getSectionsForDocument(docId).isNotEmpty())

        val deleted = documentManager.deleteDocument(docId)
        assertTrue(deleted)
        assertNull(fakeDocDao.getDocumentByDocId(docId))
        assertTrue(fakeDocDao.getSectionsForDocument(docId).isEmpty())
    }

    // ==========================================
    // 21. Static Code Analysis (No Untrusted Exec)
    // ==========================================
    @Test
    fun testCodeAssistantStaticSafety() {
        val code = """
            fun process(items: List<Int>) {
                while(true) {
                    println(items.size)
                }
            }
        """.trimIndent()

        val analysis = codeAssistant.analyzeCode(code, "Kotlin")
        assertEquals("Kotlin", analysis.language)
        assertTrue(analysis.potentialIssues.any { it.contains("infinite loop") })
        assertTrue(analysis.suggestions.any { it.contains("strictly prohibits automated execution") })
    }

    // ==========================================
    // 22. Study Planner Weekly Schedule Proposal
    // ==========================================
    @Test
    fun testStudyPlannerWeeklySchedule() {
        val plan = studyPlanner.generateProposedWeeklySchedule(
            subjects = listOf("OS", "Networks", "Databases"),
            availableHoursPerDay = 2
        )
        assertTrue(plan.totalSessions > 0)
        assertTrue(plan.totalHours > 0f)
        assertTrue(plan.explanation.contains("Proposed 7-day revision schedule"))
    }

    // ==========================================
    // 23. Sensitive Secret Filtration
    // ==========================================
    @Test
    fun testSensitiveSecretFiltration() {
        val privacy = MemoryPrivacyController()
        assertTrue(privacy.containsSensitiveSecret("my password is Password123!"))
        assertTrue(privacy.containsSensitiveSecret("api_key = abcdef1234567890"))
        assertTrue(privacy.containsSensitiveSecret("bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertFalse(privacy.containsSensitiveSecret("I prefer studying in the evening."))
    }
}

// ==========================================
// In-Memory Test Doubles (DAOs & Repositories)
// ==========================================

class FakePersonalFactDao : PersonalFactDao {
    private val facts = mutableListOf<PersonalFactEntity>()
    private val idGen = AtomicLong(1)

    override suspend fun insertFact(fact: PersonalFactEntity): Long {
        facts.removeIf { it.factId == fact.factId }
        val id = idGen.getAndIncrement()
        facts.add(fact.copy(id = id))
        return id
    }

    override suspend fun insertFacts(facts: List<PersonalFactEntity>) {
        facts.forEach { insertFact(it) }
    }

    override suspend fun updateFact(fact: PersonalFactEntity) {
        val idx = facts.indexOfFirst { it.factId == fact.factId }
        if (idx != -1) facts[idx] = fact
    }

    override suspend fun deleteFact(fact: PersonalFactEntity) {
        facts.removeIf { it.factId == fact.factId }
    }

    override suspend fun deleteById(id: Long) {
        facts.removeIf { it.id == id }
    }

    override suspend fun deleteByFactId(factId: String): Int {
        val count = facts.count { it.factId == factId }
        facts.removeIf { it.factId == factId }
        return count
    }

    override suspend fun deleteBySubject(subject: String): Int {
        val count = facts.count { it.subject.equals(subject, ignoreCase = true) }
        facts.removeIf { it.subject.equals(subject, ignoreCase = true) }
        return count
    }

    override suspend fun getFactById(id: Long): PersonalFactEntity? = facts.find { it.id == id }

    override suspend fun getFactByFactId(factId: String): PersonalFactEntity? = facts.find { it.factId == factId }

    override fun getAllFacts(): Flow<List<PersonalFactEntity>> = flowOf(facts.toList())

    override suspend fun getAllFactsSync(): List<PersonalFactEntity> = facts.toList()

    override fun getFactsByCategory(category: String): Flow<List<PersonalFactEntity>> =
        flowOf(facts.filter { it.category == category })

    override suspend fun getFactsBySubject(subject: String): List<PersonalFactEntity> =
        facts.filter { it.subject.contains(subject, ignoreCase = true) }

    override suspend fun searchFacts(query: String): List<PersonalFactEntity> =
        facts.filter {
            it.subject.contains(query, ignoreCase = true) ||
                    it.content.contains(query, ignoreCase = true) ||
                    it.tags.contains(query, ignoreCase = true)
        }

    override suspend fun clearAllFacts() {
        facts.clear()
    }
}

class FakeKnowledgeDocumentDao : KnowledgeDocumentDao {
    private val documents = mutableListOf<DocumentEntity>()
    private val sections = mutableListOf<DocumentSectionEntity>()
    private val docIdGen = AtomicLong(1)
    private val secIdGen = AtomicLong(1)

    override suspend fun insertDocument(document: DocumentEntity): Long {
        documents.removeIf { it.docId == document.docId }
        val id = docIdGen.getAndIncrement()
        documents.add(document.copy(id = id))
        return id
    }

    override suspend fun updateDocument(document: DocumentEntity) {
        val idx = documents.indexOfFirst { it.docId == document.docId }
        if (idx != -1) documents[idx] = document
    }

    override suspend fun deleteDocument(document: DocumentEntity) {
        documents.removeIf { it.docId == document.docId }
    }

    override suspend fun getDocumentByDocId(docId: String): DocumentEntity? =
        documents.find { it.docId == docId }

    override fun getAllDocuments(): Flow<List<DocumentEntity>> = flowOf(documents.toList())

    override suspend fun getAllDocumentsSync(): List<DocumentEntity> = documents.toList()

    override suspend fun insertSections(sections: List<DocumentSectionEntity>) {
        sections.forEach {
            val id = secIdGen.getAndIncrement()
            this.sections.add(it.copy(id = id))
        }
    }

    override suspend fun getSectionsForDocument(docId: String): List<DocumentSectionEntity> =
        sections.filter { it.docId == docId }

    override suspend fun searchSections(query: String): List<DocumentSectionEntity> =
        sections.filter { it.content.contains(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true) }

    override suspend fun deleteSectionsForDocument(docId: String): Int {
        val count = sections.count { it.docId == docId }
        sections.removeIf { it.docId == docId }
        return count
    }

    override suspend fun deleteDocumentByDocId(docId: String): Int {
        val count = documents.count { it.docId == docId }
        documents.removeIf { it.docId == docId }
        return count
    }

    override suspend fun clearAllDocuments() {
        documents.clear()
    }

    override suspend fun clearAllSections() {
        sections.clear()
    }
}

class FakeFlashcardDao : FlashcardDao {
    private val cards = mutableListOf<FlashcardEntity>()
    private val idGen = AtomicLong(1)

    override suspend fun insertFlashcard(card: FlashcardEntity): Long {
        val id = idGen.getAndIncrement()
        cards.add(card.copy(id = id))
        return id
    }

    override suspend fun insertFlashcards(cards: List<FlashcardEntity>) {
        cards.forEach { insertFlashcard(it) }
    }

    override suspend fun updateFlashcard(card: FlashcardEntity) {
        val idx = cards.indexOfFirst { it.id == card.id }
        if (idx != -1) cards[idx] = card
    }

    override suspend fun deleteFlashcard(card: FlashcardEntity) {
        cards.removeIf { it.id == card.id }
    }

    override fun getAllFlashcards(): Flow<List<FlashcardEntity>> = flowOf(cards.toList())

    override suspend fun getFlashcardsByDeck(deckName: String): List<FlashcardEntity> =
        cards.filter { deckName.isBlank() || it.deckName.equals(deckName, ignoreCase = true) }

    override suspend fun getDueFlashcards(currentTimestamp: Long): List<FlashcardEntity> =
        cards.filter { it.nextReviewTimestamp <= currentTimestamp }

    override suspend fun deleteById(id: Long) {
        cards.removeIf { it.id == id }
    }

    override suspend fun deleteByDeck(deckName: String) {
        cards.removeIf { it.deckName.equals(deckName, ignoreCase = true) }
    }

    override suspend fun clearAll() {
        cards.clear()
    }
}

class FakeStudyPlannerDao : StudyPlannerDao {
    private val sessions = mutableListOf<StudySessionEntity>()
    private val assignments = mutableListOf<AssignmentEntity>()
    private val sessIdGen = AtomicLong(1)
    private val assIdGen = AtomicLong(1)

    override suspend fun insertSession(session: StudySessionEntity): Long {
        val id = sessIdGen.getAndIncrement()
        sessions.add(session.copy(id = id))
        return id
    }

    override suspend fun insertSessions(sessions: List<StudySessionEntity>) {
        sessions.forEach { insertSession(it) }
    }

    override suspend fun updateSession(session: StudySessionEntity) {
        val idx = sessions.indexOfFirst { it.id == session.id }
        if (idx != -1) sessions[idx] = session
    }

    override suspend fun deleteSession(session: StudySessionEntity) {
        sessions.removeIf { it.id == session.id }
    }

    override fun getAllSessions(): Flow<List<StudySessionEntity>> = flowOf(sessions.toList())

    override suspend fun getSessionsForDate(date: String): List<StudySessionEntity> =
        sessions.filter { date.isBlank() || it.scheduledDate == date }

    override suspend fun getSessionsBySubject(subject: String): List<StudySessionEntity> =
        sessions.filter { it.subject.equals(subject, ignoreCase = true) }

    override suspend fun insertAssignment(assignment: AssignmentEntity): Long {
        val id = assIdGen.getAndIncrement()
        assignments.add(assignment.copy(id = id))
        return id
    }

    override suspend fun updateAssignment(assignment: AssignmentEntity) {
        val idx = assignments.indexOfFirst { it.id == assignment.id }
        if (idx != -1) assignments[idx] = assignment
    }

    override suspend fun deleteAssignment(assignment: AssignmentEntity) {
        assignments.removeIf { it.id == assignment.id }
    }

    override fun getAllAssignments(): Flow<List<AssignmentEntity>> = flowOf(assignments.toList())

    override suspend fun getPendingAssignments(): List<AssignmentEntity> =
        assignments.filter { !it.isSubmitted }

    override suspend fun clearSessions() {
        sessions.clear()
    }

    override suspend fun clearAssignments() {
        assignments.clear()
    }
}

class FakeMemoryRepository : MemoryRepository(null) {
    private val reminders = mutableListOf<ReminderEntity>()
    private val remIdGen = AtomicLong(1)

    override suspend fun saveReminder(title: String, triggerTimeMs: Long, recurrence: String): Long {
        val id = remIdGen.getAndIncrement()
        reminders.add(ReminderEntity(id = id, title = title, triggerTimeMs = triggerTimeMs, recurrence = recurrence))
        return id
    }

    override fun getActiveReminders(): Flow<List<ReminderEntity>> = flowOf(reminders.toList())
}

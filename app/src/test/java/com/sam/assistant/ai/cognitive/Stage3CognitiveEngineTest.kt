package com.sam.assistant.ai.cognitive

import com.sam.assistant.ai.cognitive.context.ContextFrame
import com.sam.assistant.ai.cognitive.context.ContextManager
import com.sam.assistant.ai.cognitive.dialogue.DialogueManager
import com.sam.assistant.ai.cognitive.entity.EntityExtractor
import com.sam.assistant.ai.cognitive.intent.IntentClassifier
import com.sam.assistant.ai.cognitive.models.CognitiveEntities
import com.sam.assistant.ai.cognitive.models.CognitiveIntent
import com.sam.assistant.ai.cognitive.models.DialogueState
import com.sam.assistant.ai.cognitive.normalizer.LanguageNormalizer
import com.sam.assistant.ai.cognitive.reasoning.ReasoningEngine
import com.sam.assistant.voice.language.LanguageDetectionManager
import com.sam.assistant.voice.language.SpokenLanguage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Stage3CognitiveEngineTest {

    @Before
    fun setUp() {
        ContextManager.clearAll()
        DialogueManager.reset()
    }

    // ==========================================
    // Test Case A: English Command Understanding
    // "Open YouTube and search Python tutorials."
    // ==========================================
    @Test
    fun testCaseA_EnglishOpenAndSearch() {
        val input = "Open YouTube and search Python tutorials."
        val language = LanguageDetectionManager.detectLanguage(input)
        assertEquals(SpokenLanguage.ENGLISH, language)

        val classification = IntentClassifier.classify(input)
        assertTrue(
            classification.intent == CognitiveIntent.SEARCH_WEB ||
                    classification.intent == CognitiveIntent.OPEN_APP
        )
        assertTrue(classification.confidence >= 0.90f)

        val entities = EntityExtractor.extract(input, classification.intent)
        assertEquals("youtube", entities.appName)
        assertEquals("Python tutorials", entities.searchQuery)
    }

    // ==========================================
    // Test Case B: Hindi Command Understanding
    // "YouTube kholo aur Python tutorials search karo."
    // ==========================================
    @Test
    fun testCaseB_HindiOpenAndSearch() {
        val input = "YouTube kholo aur Python tutorials search karo."
        val language = LanguageDetectionManager.detectLanguage(input)
        assertTrue(language == SpokenLanguage.HINGLISH || language == SpokenLanguage.HINDI)

        val classification = IntentClassifier.classify(input)
        assertTrue(
            classification.intent == CognitiveIntent.SEARCH_WEB ||
                    classification.intent == CognitiveIntent.OPEN_APP
        )
        assertTrue(classification.confidence >= 0.90f)

        val entities = EntityExtractor.extract(input, classification.intent)
        assertEquals("youtube", entities.appName)
        assertEquals("Python tutorials", entities.searchQuery)
    }

    // ==========================================
    // Test Case C: Hinglish Command Understanding
    // "Brave browser open karo aur cybersecurity news search karo."
    // ==========================================
    @Test
    fun testCaseC_HinglishBraveSearch() {
        val input = "Brave browser open karo aur cybersecurity news search karo."
        val language = LanguageDetectionManager.detectLanguage(input)
        assertTrue(language == SpokenLanguage.HINGLISH || language == SpokenLanguage.HINDI)

        val classification = IntentClassifier.classify(input)
        assertEquals(CognitiveIntent.SEARCH_WEB, classification.intent)
        assertTrue(classification.confidence >= 0.90f)

        val entities = EntityExtractor.extract(input, classification.intent)
        assertEquals("brave", entities.appName)
        assertEquals("cybersecurity news", entities.searchQuery)
    }

    // ==========================================
    // Test Case D: Contextual Multi-Turn Conversation
    // Turn 1: "Open YouTube."
    // Turn 2: "Search Python tutorials."
    // Turn 3: "Play the second one."
    // ==========================================
    @Test
    fun testCaseD_ContextualSearchAndOrdinalSelection() {
        // Turn 1: Open YouTube
        val t1 = "Open YouTube."
        val c1 = IntentClassifier.classify(t1)
        val e1 = EntityExtractor.extract(t1, c1.intent)
        assertEquals(CognitiveIntent.OPEN_APP, c1.intent)
        assertEquals("youtube", e1.appName)
        ContextManager.updateContext(
            ContextFrame(intent = c1.intent, entities = e1, rawUtterance = t1)
        )

        // Turn 2: Search Python tutorials
        val t2 = "Search Python tutorials."
        val c2 = IntentClassifier.classify(t2)
        val e2 = EntityExtractor.extract(t2, c2.intent)
        assertEquals(CognitiveIntent.SEARCH_WEB, c2.intent)
        val searchResults = listOf("Python for Beginners", "Python Crash Course", "Intermediate Python")
        ContextManager.updateContext(
            ContextFrame(
                intent = c2.intent,
                entities = e2.copy(appName = "youtube"),
                rawUtterance = t2,
                searchResults = searchResults
            )
        )

        // Turn 3: "Play the second one."
        val t3 = "Play the second one."
        val ordinal = LanguageNormalizer.extractOrdinal(t3)
        assertEquals(2, ordinal)

        val resolved = ContextManager.resolveReference(t3, ordinal)
        assertNotNull(resolved)
        assertEquals(CognitiveIntent.PLAY_MEDIA, resolved?.first)
        assertEquals(2, resolved?.second?.ordinal)
        assertEquals("Python Crash Course", resolved?.second?.mediaTitle)
    }

    // ==========================================
    // Test Case E: Clarification & Contact Disambiguation
    // User: "Call Rahul."
    // SAM: "Which Rahul?" (Rahul Sharma vs Rahul Verma)
    // User: "The second one."
    // ==========================================
    @Test
    fun testCaseE_ClarificationAndContactDisambiguation() {
        val userTurn1 = "Call Rahul."
        val classification = IntentClassifier.classify(userTurn1)
        val entities = EntityExtractor.extract(userTurn1, classification.intent)
        assertEquals(CognitiveIntent.MAKE_CALL, classification.intent)
        assertEquals("Rahul", entities.contactName)

        // SAM finds multiple candidates
        val candidates = listOf("Rahul Sharma", "Rahul Verma")
        DialogueManager.startDisambiguation(
            intent = CognitiveIntent.MAKE_CALL,
            question = "I found two contacts named Rahul: 1. Rahul Sharma, 2. Rahul Verma. Which one should I call?",
            options = candidates,
            targetProperty = "contactName",
            entities = entities
        )

        assertEquals(DialogueState.NEEDS_CLARIFICATION, DialogueManager.currentState.value)
        assertTrue(DialogueManager.hasPendingDisambiguation())

        // User responds: "The second one."
        val userTurn2 = "The second one."
        val ordinal = LanguageNormalizer.extractOrdinal(userTurn2)
        assertEquals(2, ordinal)

        val resolved = DialogueManager.resolveDisambiguation(userTurn2, ordinal)
        assertNotNull(resolved)
        assertEquals(CognitiveIntent.MAKE_CALL, resolved?.first)
        assertEquals("Rahul Verma", resolved?.second?.contactName)
        assertEquals(DialogueState.READY_FOR_ACTION, DialogueManager.currentState.value)
        assertFalse(DialogueManager.hasPendingDisambiguation())
    }

    // ==========================================
    // Test Case F: User Correction
    // User: "Set an alarm for 6 AM."
    // User: "Actually, make it 7 AM."
    // ==========================================
    @Test
    fun testCaseF_AlarmCorrection() {
        // Initial command
        val t1 = "Set an alarm for 6 AM."
        val c1 = IntentClassifier.classify(t1)
        val e1 = EntityExtractor.extract(t1, c1.intent)
        assertEquals(CognitiveIntent.SET_ALARM, c1.intent)
        assertEquals("06:00", e1.time)
        ContextManager.updateContext(
            ContextFrame(intent = c1.intent, entities = e1, rawUtterance = t1)
        )

        // User correction: "Actually, make it 7 AM."
        val t2 = "Actually, make it 7 AM."
        val resolvedCorrection = ContextManager.resolveCorrection(t2)
        assertNotNull(resolvedCorrection)
        assertEquals(CognitiveIntent.SET_ALARM, resolvedCorrection?.first)
        assertEquals("07:00", resolvedCorrection?.second?.time)
    }

    // ==========================================
    // Test Case G: Ambiguity Detection & Clarification
    // User: "Open that application."
    // ==========================================
    @Test
    fun testCaseG_AmbiguityDetection() {
        val input = "Open that application."
        val classification = IntentClassifier.classify(input)
        assertEquals(CognitiveIntent.OPEN_APP, classification.intent)
        assertTrue(classification.isAmbiguous)
        assertTrue(classification.confidence < 0.60f)
        assertTrue(classification.clarificationOptions.isNotEmpty())
    }

    // ==========================================
    // Test Case H: Complex Structured Reasoning
    // "Find a good Python course, search its syllabus and explain whether it is suitable for beginners."
    // ==========================================
    @Test
    fun testCaseH_StructuredReasoningEngine() {
        val input = "Find a good Python course, search its syllabus and explain whether it is suitable for beginners."
        assertTrue(ReasoningEngine.requiresStructuredReasoning(input))

        val entities = EntityExtractor.extract(input, CognitiveIntent.EXPLAIN_CONCEPT)
        val plan = ReasoningEngine.produceStructuredPlan(input, entities)

        assertNotNull(plan)
        assertTrue(plan.subtasks.size >= 3)
        assertTrue(plan.requiredInfo.isNotEmpty())
        assertTrue(plan.conciseUserExplanation.isNotBlank())
        // Verified: Internal reasoning trace is concise and not cluttered with chain-of-thought dumps
        assertFalse(plan.conciseUserExplanation.contains("internal thoughts"))
        assertTrue(plan.executableActions.isNotEmpty())
        assertEquals("brave", plan.executableActions.first().target)
    }

    // ==========================================
    // Additional Test 1: Hinglish Reminder with Relative Date & Hindi Time
    // "Kal subah saat baje Python padhne ka reminder laga do."
    // ==========================================
    @Test
    fun testHinglishReminderStructuredOutput() {
        val input = "Kal subah saat baje Python padhne ka reminder laga do."
        val language = LanguageDetectionManager.detectLanguage(input)
        assertTrue(language == SpokenLanguage.HINGLISH || language == SpokenLanguage.HINDI)

        val classification = IntentClassifier.classify(input)
        assertEquals(CognitiveIntent.CREATE_REMINDER, classification.intent)

        val entities = EntityExtractor.extract(input, classification.intent)
        assertEquals("tomorrow", entities.date)
        assertEquals("07:00", entities.time)
        assertEquals("study Python", entities.subject)
        assertEquals(7, entities.number)
    }

    // ==========================================
    // Additional Test 2: Multilingual Device Control Normalization
    // "Phone ka torch on karo.", "Flashlight chalu karo.", "Turn on the flashlight."
    // ==========================================
    @Test
    fun testDeviceControlNormalizationVariants() {
        val v1 = "Phone ka torch on karo."
        val v2 = "Flashlight chalu karo."
        val v3 = "Turn on the flashlight."

        val c1 = IntentClassifier.classify(v1)
        val c2 = IntentClassifier.classify(v2)
        val c3 = IntentClassifier.classify(v3)

        assertEquals(CognitiveIntent.CONTROL_DEVICE, c1.intent)
        assertEquals(CognitiveIntent.CONTROL_DEVICE, c2.intent)
        assertEquals(CognitiveIntent.CONTROL_DEVICE, c3.intent)

        val e1 = EntityExtractor.extract(v1, c1.intent)
        val e2 = EntityExtractor.extract(v2, c2.intent)
        val e3 = EntityExtractor.extract(v3, c3.intent)

        assertEquals("torch", e1.actionParameters["target"])
        assertEquals("on", e1.actionParameters["state"])

        assertEquals("torch", e2.actionParameters["target"])
        assertEquals("on", e2.actionParameters["state"])

        assertEquals("torch", e3.actionParameters["target"])
        assertEquals("on", e3.actionParameters["state"])
    }

    // ==========================================
    // Additional Test 3: Processing Latency Benchmark
    // Target: Sub-15ms parsing latency on JVM
    // ==========================================
    @Test
    fun testCognitiveProcessingLatency() {
        val testQueries = listOf(
            "Open YouTube and search Python tutorials.",
            "Kal subah saat baje Python padhne ka reminder laga do.",
            "Brave browser open karo aur cybersecurity news search karo.",
            "Turn on flashlight.",
            "Call Rahul."
        )

        // Warmup
        for (q in testQueries) {
            IntentClassifier.classify(q)
            EntityExtractor.extract(q, CognitiveIntent.OPEN_APP)
        }

        val startTime = System.nanoTime()
        for (q in testQueries) {
            val c = IntentClassifier.classify(q)
            EntityExtractor.extract(q, c.intent)
        }
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
        val avgLatencyMs = elapsedMs / testQueries.size

        println("Cognitive Engine Average Parsing Latency: $avgLatencyMs ms")
        assertTrue("Parsing must be ultra-fast (under 15 ms)", avgLatencyMs < 15.0)
    }
}

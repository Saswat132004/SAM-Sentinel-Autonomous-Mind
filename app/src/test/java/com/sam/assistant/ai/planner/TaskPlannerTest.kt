package com.sam.assistant.ai.planner

import com.sam.assistant.ai.intent.FastCommandEngine
import com.sam.assistant.ai.intent.SessionContextManager
import com.sam.assistant.automation.executor.ActionRequest
import com.sam.assistant.automation.executor.ActionType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TaskPlannerTest {

    private lateinit var planner: TaskPlanner
    private lateinit var engine: FastCommandEngine

    @Before
    fun setUp() {
        engine = FastCommandEngine()
        planner = TaskPlanner(engine)
        SessionContextManager.clearAll()
    }

    @Test
    fun testPlayMusicFromBraveDecomposition() {
        val plan = planner.createPlan("Play music from Brave Browser")
        assertEquals("PLAY_MEDIA", plan.intent)
        assertEquals("brave", plan.targetApp)
        assertEquals(2, plan.steps.size)

        // Step 1: Verify installed
        assertEquals(ActionType.VERIFY_APP_INSTALLED, plan.steps[0].actionRequest.actionType)
        assertEquals("brave", plan.steps[0].actionRequest.target)

        // Step 2: Open music destination in Brave
        assertEquals(ActionType.PLAY_MEDIA, plan.steps[1].actionRequest.actionType)
        assertEquals("brave", plan.steps[1].actionRequest.target)
    }

    @Test
    fun testOpenBraveAndSearchDecomposition() {
        val plan = planner.createPlan("Open Brave and search for the latest cybersecurity news")
        assertEquals("SEARCH_WEB", plan.intent)
        assertEquals("brave", plan.targetApp)
        assertEquals(2, plan.steps.size)

        assertEquals(ActionType.VERIFY_APP_INSTALLED, plan.steps[0].actionRequest.actionType)
        assertEquals("brave", plan.steps[0].actionRequest.target)

        assertEquals(ActionType.SEARCH_WEB, plan.steps[1].actionRequest.actionType)
        assertEquals("latest cybersecurity news", plan.steps[1].actionRequest.payload)
    }

    @Test
    fun testOpenBraveAndSearchMLCourses() {
        val plan = planner.createPlan("Open Brave and search for machine learning courses")
        assertEquals("SEARCH_WEB", plan.intent)
        assertEquals("brave", plan.targetApp)
        assertEquals(2, plan.steps.size)

        assertEquals(ActionType.VERIFY_APP_INSTALLED, plan.steps[0].actionRequest.actionType)
        assertEquals(ActionType.SEARCH_WEB, plan.steps[1].actionRequest.actionType)
        assertEquals("machine learning courses", plan.steps[1].actionRequest.payload)
    }

    @Test
    fun testOpenYouTubeSearchCybersecurity() {
        val plan = planner.createPlan("Open YouTube and search for cybersecurity tutorials")
        assertEquals("SEARCH_YOUTUBE", plan.intent)
        assertEquals(1, plan.steps.size)
        assertEquals(ActionType.SEARCH_YOUTUBE, plan.steps[0].actionRequest.actionType)
        assertEquals("cybersecurity tutorials", plan.steps[0].actionRequest.payload)
    }

    @Test
    fun testWhatsAppPrepareMessageDecomposition() {
        val plan = planner.createPlan("WhatsApp kholo aur Rahul ko message bhejne ke liye prepare karo")
        assertEquals("PREPARE_WHATSAPP", plan.intent)
        assertEquals(2, plan.steps.size)

        assertEquals(ActionType.VERIFY_APP_INSTALLED, plan.steps[0].actionRequest.actionType)
        assertEquals("whatsapp", plan.steps[0].actionRequest.target)

        assertEquals(ActionType.PREPARE_WHATSAPP, plan.steps[1].actionRequest.actionType)
        assertEquals("rahul", plan.steps[1].actionRequest.target)
    }

    @Test
    fun testCallRequiresConfirmationPlan() {
        val plan = planner.createPlan("Call Rahul and confirm before calling")
        assertEquals("MAKE_CALL", plan.intent)
        assertTrue(plan.requiresConfirmation)
        assertTrue(plan.confirmationPrompt.contains("Rahul"))
        assertEquals(1, plan.steps.size)
        assertEquals(ActionType.MAKE_CALL, plan.steps[0].actionRequest.actionType)
        assertEquals("rahul", plan.steps[0].actionRequest.target)
    }

    @Test
    fun testOpenChromeSearchWeatherAndReadDecomposition() {
        val plan = planner.createPlan("Open Chrome, search for the weather and read the result")
        assertEquals("SEARCH_WEB", plan.intent)
        assertEquals("chrome", plan.targetApp)
        assertEquals(3, plan.steps.size)

        // Step 1: Verify Chrome
        assertEquals(ActionType.VERIFY_APP_INSTALLED, plan.steps[0].actionRequest.actionType)
        assertEquals("chrome", plan.steps[0].actionRequest.target)

        // Step 2: Search Chrome
        assertEquals(ActionType.SEARCH_WEB, plan.steps[1].actionRequest.actionType)
        assertEquals("weather", plan.steps[1].actionRequest.payload)

        // Step 3: Read weather summary
        assertEquals(ActionType.READ_WEB_SUMMARY, plan.steps[2].actionRequest.actionType)
    }

    @Test
    fun testClarificationFlow() {
        // User says ambiguous "Play music"
        val clarifyPlan = planner.createPlan("Play music")
        assertEquals("CLARIFICATION_REQUIRED", clarifyPlan.intent)
        assertEquals(ActionType.ASK_CLARIFICATION, clarifyPlan.steps[0].actionRequest.actionType)

        // Set pending clarification in session context
        SessionContextManager.setPendingClarification(
            prompt = "Should I use YouTube, Spotify or Brave?",
            contextKey = "music_source",
            options = listOf("YouTube", "Spotify", "Brave")
        )

        // User responds with "Brave"
        val resolvedPlan = planner.createPlan("Brave")
        assertEquals("PLAY_MEDIA", resolvedPlan.intent)
        assertEquals("brave", resolvedPlan.targetApp)
        assertEquals(2, resolvedPlan.steps.size)
    }

    @Test
    fun testConfirmationAffirmativeAndNegative() {
        val dummyCall = ActionRequest(
            actionType = ActionType.MAKE_CALL,
            target = "Rahul",
            requiresConfirmation = true
        )
        SessionContextManager.setPendingConfirmation("Are you sure?", dummyCall)

        // User says "Yes" / "Haan"
        val confirmPlan = planner.createPlan("haan")
        assertEquals("CONFIRMED_EXECUTION", confirmPlan.intent)
        assertEquals(ActionType.MAKE_CALL, confirmPlan.steps[0].actionRequest.actionType)
        assertFalse(confirmPlan.steps[0].actionRequest.requiresConfirmation)

        // Reset and test negative
        SessionContextManager.setPendingConfirmation("Are you sure?", dummyCall)
        val cancelPlan = planner.createPlan("nahi")
        assertEquals("CANCEL_EXECUTION", cancelPlan.intent)
        assertEquals(ActionType.CANCEL_ACTION, cancelPlan.steps[0].actionRequest.actionType)
    }

    @Test
    fun testMultiStepCompoundPlanning() {
        val plan = planner.createPlan("turn on flashlight and check battery")
        assertEquals("MULTI_STEP_COMPOUND", plan.intent)
        assertEquals(2, plan.steps.size)
        assertEquals(ActionType.TOGGLE_FLASHLIGHT, plan.steps[0].actionRequest.actionType)
        assertEquals(ActionType.READ_BATTERY, plan.steps[1].actionRequest.actionType)
    }
}

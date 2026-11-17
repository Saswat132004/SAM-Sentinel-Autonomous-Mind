package com.sam.assistant.automation

import com.sam.assistant.ai.intent.SessionContextManager
import com.sam.assistant.ai.planner.TaskPlan
import com.sam.assistant.ai.planner.TaskStep
import com.sam.assistant.automation.executor.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ActionExecutionTest {

    @Before
    fun setUp() {
        SessionContextManager.clearAll()
    }

    @Test
    fun testErrorRecoveryForMissingApp() {
        val result = ErrorRecoveryManager.appNotInstalled("Brave Browser")
        assertEquals("AppLauncher", result.failingComponent)
        assertTrue(result.userExplanation.contains("Brave Browser is not installed"))
        assertTrue(result.suggestedAlternative?.contains("Chrome") == true)
        assertTrue(result.technicalError.contains("Package for app \"Brave Browser\" is not installed"))
    }

    @Test
    fun testErrorRecoveryForMissingContact() {
        val result = ErrorRecoveryManager.contactNotFound("Rahul")
        assertEquals("CallManager", result.failingComponent)
        assertTrue(result.userExplanation.contains("could not find \"Rahul\""))
        assertTrue(result.suggestedAlternative?.contains("check your Contacts app") == true)
    }

    @Test
    fun testLockStateBlocked() {
        val result = ErrorRecoveryManager.lockStateBlocked("Place phone call")
        assertEquals("LockStateGuard", result.failingComponent)
        assertTrue(result.userExplanation.contains("smartphone is locked"))
    }

    @Test
    fun testConfirmationSafety() {
        val callRequest = ActionRequest(
            actionType = ActionType.MAKE_CALL,
            target = "Rahul",
            requiresConfirmation = true,
            confirmationPrompt = "Are you sure you want to call Rahul?"
        )
        SessionContextManager.setPendingConfirmation(callRequest.confirmationPrompt, callRequest)
        assertNotNull(SessionContextManager.getPendingConfirmation())

        // Negative response cancels
        assertTrue(SessionContextManager.isNegative("no"))
        assertTrue(SessionContextManager.isNegative("nahi"))
        assertTrue(SessionContextManager.isNegative("cancel"))

        // Affirmative confirms
        assertTrue(SessionContextManager.isAffirmative("yes"))
        assertTrue(SessionContextManager.isAffirmative("haan"))
        assertTrue(SessionContextManager.isAffirmative("sure"))
    }

    @Test
    fun testClarificationResolution() {
        val options = listOf("YouTube", "Spotify", "Brave")
        assertEquals("Brave", SessionContextManager.resolveOption("use brave browser", options))
        assertEquals("YouTube", SessionContextManager.resolveOption("youtube", options))
        assertEquals("Spotify", SessionContextManager.resolveOption("play on spotify", options))
        assertNull(SessionContextManager.resolveOption("netflix", options))
    }
}

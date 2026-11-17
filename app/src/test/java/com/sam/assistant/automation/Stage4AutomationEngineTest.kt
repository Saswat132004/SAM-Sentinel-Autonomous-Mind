package com.sam.assistant.automation

import android.content.Context
import com.sam.assistant.ai.planner.TaskPlan
import com.sam.assistant.ai.planner.TaskStep
import com.sam.assistant.automation.contacts.ContactResolver
import com.sam.assistant.automation.core.*
import com.sam.assistant.automation.executor.ActionRequest
import com.sam.assistant.automation.executor.ActionType
import com.sam.assistant.core.security.LockStateManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Stage4AutomationEngineTest {

    @Before
    fun setUp() {
        ConfirmationManager.clear()
        ExecutionTracker.reset()
        LockStateManager.setLockedState(false)
    }

    // ==========================================
    // 1. Action Registry & Risk Categorization
    // ==========================================

    @Test
    fun testActionRegistryRiskCategorization() {
        // LOW Risk
        assertEquals(RiskLevel.LOW, ActionRegistry.getCapability(ActionType.LAUNCH_APP)?.riskLevel)
        assertEquals(RiskLevel.LOW, ActionRegistry.getCapability(ActionType.SEARCH_WEB)?.riskLevel)
        assertEquals(RiskLevel.LOW, ActionRegistry.getCapability(ActionType.READ_BATTERY)?.riskLevel)
        assertFalse(ActionRegistry.isHighRisk(ActionType.LAUNCH_APP))

        // MEDIUM Risk
        assertEquals(RiskLevel.MEDIUM, ActionRegistry.getCapability(ActionType.TOGGLE_FLASHLIGHT)?.riskLevel)
        assertEquals(RiskLevel.MEDIUM, ActionRegistry.getCapability(ActionType.SET_ALARM)?.riskLevel)
        assertEquals(RiskLevel.MEDIUM, ActionRegistry.getCapability(ActionType.ADJUST_VOLUME)?.riskLevel)
        assertFalse(ActionRegistry.isHighRisk(ActionType.TOGGLE_FLASHLIGHT))

        // HIGH Risk (Requires Confirmation)
        assertEquals(RiskLevel.HIGH, ActionRegistry.getCapability(ActionType.MAKE_CALL)?.riskLevel)
        assertEquals(RiskLevel.HIGH, ActionRegistry.getCapability(ActionType.SEND_SMS)?.riskLevel)
        assertEquals(RiskLevel.HIGH, ActionRegistry.getCapability(ActionType.SEND_WHATSAPP)?.riskLevel)
        assertEquals(RiskLevel.HIGH, ActionRegistry.getCapability(ActionType.DELETE_MEMORY)?.riskLevel)

        assertTrue(ActionRegistry.isHighRisk(ActionType.MAKE_CALL))
        assertTrue(ActionRegistry.isHighRisk(ActionType.SEND_SMS))
        assertTrue(ActionRegistry.isHighRisk(ActionType.SEND_WHATSAPP))
        assertTrue(ActionRegistry.isHighRisk(ActionType.DELETE_MEMORY))
    }

    @Test
    fun testActionRegistryAllowlist() {
        assertTrue(ActionRegistry.isRegistered(ActionType.LAUNCH_APP))
        assertTrue(ActionRegistry.isRegistered(ActionType.SEARCH_WEB))
        assertTrue(ActionRegistry.isRegistered(ActionType.MAKE_CALL))
        assertTrue(ActionRegistry.isRegistered(ActionType.TOGGLE_FLASHLIGHT))
        assertFalse(ActionRegistry.isRegistered(ActionType.UNKNOWN))
    }

    // ==========================================
    // 2. Action Security & Prohibited Pattern Injection
    // ==========================================

    @Test
    fun testDangerousCommandPatternRejection() {
        val dangerousInputs = listOf(
            "su -c rm -rf /data",
            "sh -c kill -9",
            "cat /proc/cpuinfo && busybox",
            "chmod 777 /system/bin",
            "screencap /sdcard/screen.png",
            "tcpdump -i wlan0"
        )

        val validator = ActionValidator(createMockContext())

        for (danger in dangerousInputs) {
            val req = ActionRequest(
                actionType = ActionType.LAUNCH_APP,
                target = "browser",
                payload = danger,
                rawCommand = danger
            )
            val outcome = validator.validate(req, isConfirmed = true)
            assertTrue("Expected rejection for: $danger", outcome is ValidationOutcome.Rejected)
            if (outcome is ValidationOutcome.Rejected) {
                assertTrue("Must be marked as security threat", outcome.securityThreat)
            }
        }
    }

    @Test
    fun testLockStateBlocksAutomation() {
        LockStateManager.setLockedState(true)
        val validator = ActionValidator(createMockContext())

        val req = ActionRequest(actionType = ActionType.LAUNCH_APP, target = "youtube")
        val outcome = validator.validate(req)

        assertTrue(outcome is ValidationOutcome.Rejected)
        if (outcome is ValidationOutcome.Rejected) {
            assertTrue(outcome.reason.contains("Device is locked"))
        }
    }

    @Test
    fun testHighRiskActionEnforcesConfirmation() {
        val validator = ActionValidator(createMockContext())

        val callReq = ActionRequest(
            actionType = ActionType.MAKE_CALL,
            target = "Rahul",
            requiresConfirmation = true,
            confirmationPrompt = "Are you sure you want to call Rahul?"
        )

        // Without confirmation -> NeedsConfirmation
        val unconfirmedOutcome = validator.validate(callReq, isConfirmed = false)
        assertTrue(unconfirmedOutcome is ValidationOutcome.NeedsConfirmation)

        // With confirmation -> Permitted
        val confirmedOutcome = validator.validate(callReq, isConfirmed = true)
        assertTrue(confirmedOutcome is ValidationOutcome.Permitted)
    }

    // ==========================================
    // 3. Confirmation Manager Multilingual Tests
    // ==========================================

    @Test
    fun testConfirmationAffirmativeAndNegative() {
        // English Affirmative
        assertTrue(ConfirmationManager.isAffirmative("yes"))
        assertTrue(ConfirmationManager.isAffirmative("sure"))
        assertTrue(ConfirmationManager.isAffirmative("proceed"))
        assertTrue(ConfirmationManager.isAffirmative("go ahead"))

        // Hindi / Hinglish Affirmative
        assertTrue(ConfirmationManager.isAffirmative("haan"))
        assertTrue(ConfirmationManager.isAffirmative("sahi hai"))
        assertTrue(ConfirmationManager.isAffirmative("kar do"))
        assertTrue(ConfirmationManager.isAffirmative("bilkul"))

        // English Negative
        assertTrue(ConfirmationManager.isNegative("no"))
        assertTrue(ConfirmationManager.isNegative("cancel"))
        assertTrue(ConfirmationManager.isNegative("stop"))
        assertTrue(ConfirmationManager.isNegative("don't"))

        // Hindi / Hinglish Negative
        assertTrue(ConfirmationManager.isNegative("nahi"))
        assertTrue(ConfirmationManager.isNegative("mat karo"))
        assertTrue(ConfirmationManager.isNegative("ruk jao"))
        assertTrue(ConfirmationManager.isNegative("rehne do"))
    }

    @Test
    fun testPendingHighRiskActionRegistration() {
        val req = ActionRequest(actionType = ActionType.MAKE_CALL, target = "Rahul")
        ConfirmationManager.setPendingAction("Call Rahul?", req, RiskLevel.HIGH)

        assertTrue(ConfirmationManager.hasPendingAction())
        val pending = ConfirmationManager.getPendingAction()
        assertNotNull(pending)
        assertEquals("Call Rahul?", pending?.prompt)
        assertEquals(RiskLevel.HIGH, pending?.riskLevel)

        ConfirmationManager.clear()
        assertFalse(ConfirmationManager.hasPendingAction())
    }

    // ==========================================
    // 4. Contact Privacy Masking
    // ==========================================

    @Test
    fun testContactPhoneNumberMasking() {
        val masked1 = ContactResolver.maskPhoneNumber("+919876543210")
        assertEquals("+919*******10", masked1)
        assertFalse("Phone number must not expose full digits in logs", masked1.contains("765432"))

        val masked2 = ContactResolver.maskPhoneNumber("9876543210")
        assertEquals("9876****10", masked2)

        val maskedShort = ContactResolver.maskPhoneNumber("123")
        assertEquals("****", maskedShort)
    }

    // ==========================================
    // 5. Execution Tracker Lifecycle & Cancellation
    // ==========================================

    @Test
    fun testExecutionTrackerLifecycle() {
        ExecutionTracker.startTask("Open Brave and search Python tutorials", 3)
        assertEquals(TaskExecutionStatus.PLANNING, ExecutionTracker.snapshot.value.status)
        assertEquals(3, ExecutionTracker.snapshot.value.totalSteps)

        ExecutionTracker.setStepExecuting(1, "Launch Brave Browser")
        assertEquals(TaskExecutionStatus.EXECUTING_STEP, ExecutionTracker.snapshot.value.status)
        assertEquals(1, ExecutionTracker.snapshot.value.currentStepIndex)

        ExecutionTracker.setStepVerifying(1)
        assertEquals(TaskExecutionStatus.VERIFYING, ExecutionTracker.snapshot.value.status)

        ExecutionTracker.setStepCompleted("Launch Brave Browser")
        assertEquals(TaskExecutionStatus.STEP_COMPLETED, ExecutionTracker.snapshot.value.status)
        assertEquals(1, ExecutionTracker.snapshot.value.completedSteps.size)

        ExecutionTracker.completeTask("All 3 steps executed.")
        assertEquals(TaskExecutionStatus.TASK_COMPLETED, ExecutionTracker.snapshot.value.status)
    }

    @Test
    fun testExecutionTrackerCancellation() {
        ExecutionTracker.startTask("Play Music", 2)
        assertFalse(ExecutionTracker.isCancelled())

        ExecutionTracker.cancelTask()
        assertTrue(ExecutionTracker.isCancelled())
        assertEquals(TaskExecutionStatus.CANCELLED, ExecutionTracker.snapshot.value.status)
        assertTrue(ExecutionTracker.snapshot.value.isCancelled)
    }

    // ==========================================
    // 6. Sequential Multi-Step Execution Simulation
    // ==========================================

    @Test
    fun testSequentialMultiStepPlanConstruction() {
        val plan = TaskPlan(
            originalCommand = "Open Brave, search for Python courses and open the first result.",
            intent = "SEARCH_WEB",
            steps = listOf(
                TaskStep(1, "Verify Brave is installed", ActionRequest(ActionType.VERIFY_APP_INSTALLED, "brave")),
                TaskStep(2, "Launch Brave Browser", ActionRequest(ActionType.LAUNCH_APP, "brave")),
                TaskStep(3, "Open Python search", ActionRequest(ActionType.SEARCH_WEB, "brave", "Python courses"))
            ),
            requiresConfirmation = false
        )

        assertEquals(3, plan.steps.size)
        assertEquals(ActionType.VERIFY_APP_INSTALLED, plan.steps[0].actionRequest.actionType)
        assertEquals(ActionType.LAUNCH_APP, plan.steps[1].actionRequest.actionType)
        assertEquals(ActionType.SEARCH_WEB, plan.steps[2].actionRequest.actionType)
    }

    // ==========================================
    // 7. Result Verification Methods
    // ==========================================

    @Test
    fun testResultVerificationHandoffDistinction() {
        val reportHandoff = VerificationReport(
            isVerified = true,
            method = VerificationMethod.USER_VISUAL_HANDOFF,
            details = "WhatsApp chat draft opened with Rahul. Actual delivery requires user tap.",
            userHandoffOnly = true
        )

        assertTrue(reportHandoff.isVerified)
        assertTrue("Must indicate user handoff rather than false delivery claim", reportHandoff.userHandoffOnly)

        val reportDirect = VerificationReport(
            isVerified = true,
            method = VerificationMethod.SYSTEM_STATE_CHECK,
            details = "SMS payload submitted directly to cellular radio via SmsManager.",
            userHandoffOnly = false
        )
        assertFalse(reportDirect.userHandoffOnly)
    }

    private fun createMockContext(): Context {
        // Minimal mock context stub for JVM unit testing
        return object : android.content.ContextWrapper(null) {}
    }
}

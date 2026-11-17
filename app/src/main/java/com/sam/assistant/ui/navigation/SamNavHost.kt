package com.sam.assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sam.assistant.ui.screens.*

object SamDestinations {
    const val ONBOARDING = "onboarding"
    const val PERMISSIONS = "permissions"
    const val ASSISTANT_CONFIG = "assistant_config"
    const val DASHBOARD = "dashboard"
    const val VOICE = "voice"
    const val HISTORY = "history"
    const val MEMORY = "memory"
    const val PRIVACY = "privacy"
    const val SETTINGS = "settings"
    const val MODELS = "models"
    const val COMMANDS = "commands"
    const val DIAGNOSTICS = "diagnostics"
    const val AUTONOMOUS = "autonomous"
    const val KNOWLEDGE = "knowledge"
    const val STUDY = "study"
    const val CYBERSECURITY = "cybersecurity"
}

@Composable
fun SamNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = SamDestinations.DASHBOARD
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(SamDestinations.ONBOARDING) {
            WelcomeOnboardingScreen(
                onGetStarted = { navController.navigate(SamDestinations.PERMISSIONS) }
            )
        }
        composable(SamDestinations.PERMISSIONS) {
            PermissionSetupScreen(
                onContinue = { navController.navigate(SamDestinations.ASSISTANT_CONFIG) }
            )
        }
        composable(SamDestinations.ASSISTANT_CONFIG) {
            AssistantConfigScreen(
                onDone = {
                    navController.navigate(SamDestinations.DASHBOARD) {
                        popUpTo(SamDestinations.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(SamDestinations.DASHBOARD) {
            MainDashboardScreen(
                onNavigateToVoice = { navController.navigate(SamDestinations.VOICE) },
                onNavigateToPrivacy = { navController.navigate(SamDestinations.PRIVACY) },
                onNavigateToModels = { navController.navigate(SamDestinations.MODELS) },
                onNavigateToDiagnostics = { navController.navigate(SamDestinations.DIAGNOSTICS) },
                onNavigateToHistory = { navController.navigate(SamDestinations.COMMANDS) },
                onNavigateToAutonomous = { navController.navigate(SamDestinations.AUTONOMOUS) },
                onNavigateToStudy = { navController.navigate(SamDestinations.STUDY) },
                onNavigateToKnowledge = { navController.navigate(SamDestinations.KNOWLEDGE) },
                onNavigateToCybersecurity = { navController.navigate(SamDestinations.CYBERSECURITY) }
            )
        }
        composable(SamDestinations.VOICE) {
            VoiceInteractionScreen(
                onClose = { navController.popBackStack() }
            )
        }
        composable(SamDestinations.HISTORY) {
            ConversationHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SamDestinations.MEMORY) {
            MemoryCentreScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SamDestinations.KNOWLEDGE) {
            KnowledgeLibraryScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SamDestinations.STUDY) {
            StudyAssistantScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SamDestinations.PRIVACY) {
            PrivacyDashboardScreen(
                onBack = { navController.popBackStack() },
                onNavigateToMemories = { navController.navigate(SamDestinations.MEMORY) },
                onNavigateToConversations = { navController.navigate(SamDestinations.HISTORY) },
                onNavigateToCommands = { navController.navigate(SamDestinations.COMMANDS) }
            )
        }
        composable(SamDestinations.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SamDestinations.MODELS) {
            ModelManagementScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SamDestinations.COMMANDS) {
            AutomationHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SamDestinations.DIAGNOSTICS) {
            DiagnosticsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SamDestinations.AUTONOMOUS) {
            AutonomousTasksScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SamDestinations.CYBERSECURITY) {
            CybersecurityDashboardScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}


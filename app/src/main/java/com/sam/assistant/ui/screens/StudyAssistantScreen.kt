package com.sam.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.assistant.SamApplication
import com.sam.assistant.knowledge.productivity.ProposedStudyPlan
import com.sam.assistant.knowledge.study.ConceptExplanation
import com.sam.assistant.knowledge.study.ExplanationLevel
import com.sam.assistant.knowledge.study.FlashcardEntity
import com.sam.assistant.knowledge.study.GeneratedQuiz
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.StatusBadge
import com.sam.assistant.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun StudyAssistantScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SamApplication
    val studyEngine = app.knowledgeEngine
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabTitles = listOf("Explainer", "Flashcards", "Quiz", "Planner")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamDarkBackground)
            .padding(horizontal = 20.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SamText)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Study Assistant",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SamText
            )
        }

        // Tabs Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = SamDarkSurface,
            contentColor = SamPrimary,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, fontSize = 13.sp, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> ConceptExplainerTab(studyEngine.conceptExplainer)
            1 -> FlashcardsTab(studyEngine.flashcardManager)
            2 -> QuizTab(studyEngine.quizGenerator)
            3 -> StudyPlannerTab(studyEngine.studyPlanner)
        }
    }
}

@Composable
fun ConceptExplainerTab(explainer: com.sam.assistant.knowledge.study.ConceptExplainer) {
    var conceptQuery by remember { mutableStateOf("") }
    var currentExplanation by remember { mutableStateOf<ConceptExplanation?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            GlassCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Technical Concept Explainer", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SamText)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = conceptQuery,
                        onValueChange = { conceptQuery = it },
                        placeholder = { Text("e.g., QuickSort, Dijkstra, Virtual Memory", color = SamSecondaryText) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SamText,
                            unfocusedTextColor = SamText,
                            focusedBorderColor = SamPrimary,
                            unfocusedBorderColor = SamCardBorder
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (conceptQuery.isNotBlank()) {
                                currentExplanation = explainer.explain(conceptQuery, ExplanationLevel.DETAILED)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SamPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Explain Concept")
                    }
                }
            }
        }

        currentExplanation?.let { exp ->
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(exp.concept, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SamText)
                            StatusBadge(text = exp.level.name, color = Color(0xFF00E676))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(exp.explanationText, fontSize = 14.sp, color = SamText)

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Key Architectural Principles:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SamPrimary)
                        exp.keyPoints.forEach { pt ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• $pt", fontSize = 13.sp, color = SamSecondaryText)
                        }

                        exp.practicalExample?.let { ex ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Practical Application:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SamAccent)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(ex, fontSize = 13.sp, color = SamSecondaryText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlashcardsTab(manager: com.sam.assistant.knowledge.study.FlashcardManager) {
    val scope = rememberCoroutineScope()
    var dueCards by remember { mutableStateOf<List<FlashcardEntity>>(emptyList()) }
    var currentCardIndex by remember { mutableStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Pre-populate demo deck if empty
        val existing = manager.getDueCards()
        if (existing.isEmpty()) {
            manager.createFlashcard("OS", "What are the 4 Coffman deadlock conditions?", "Mutual Exclusion, Hold and Wait, No Preemption, Circular Wait.", "Operating Systems")
            manager.createFlashcard("OS", "What is Thrashing in Virtual Memory?", "When a process spends more time paging than executing instructions.", "Operating Systems")
            manager.createFlashcard("Networks", "What is the TCP 3-Way Handshake sequence?", "SYN -> SYN-ACK -> ACK.", "Networking")
            dueCards = manager.getDueCards()
        } else {
            dueCards = existing
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        if (dueCards.isEmpty()) {
            item {
                GlassCard {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No flashcards due for review right now! Great job.", color = SamSuccess, fontSize = 14.sp)
                    }
                }
            }
        } else {
            val card = dueCards.getOrNull(currentCardIndex) ?: dueCards.first()

            item {
                Text(
                    text = "Card ${currentCardIndex + 1} of ${dueCards.size} (Mastery: ${card.masteryLevel}/5)",
                    fontSize = 14.sp,
                    color = SamSecondaryText
                )
            }

            item {
                GlassCard(
                    modifier = Modifier.clickable { isFlipped = !isFlipped }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StatusBadge(text = if (isFlipped) "ANSWER (Tap to hide)" else "QUESTION (Tap to reveal)", color = if (isFlipped) SamSuccess else SamPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isFlipped) card.answer else card.question,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = SamText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            if (isFlipped) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    manager.recordReview(card.id, false)
                                    isFlipped = false
                                    dueCards = manager.getDueCards()
                                    if (currentCardIndex >= dueCards.size) currentCardIndex = 0
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SamError),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Incorrect")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    manager.recordReview(card.id, true)
                                    isFlipped = false
                                    dueCards = manager.getDueCards()
                                    if (currentCardIndex >= dueCards.size) currentCardIndex = 0
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SamSuccess),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Correct")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuizTab(quizGen: com.sam.assistant.knowledge.study.QuizGenerator) {
    var activeQuiz by remember { mutableStateOf<GeneratedQuiz?>(null) }
    var selectedAnswers by remember { mutableStateOf(mutableMapOf<Int, Int>()) }
    var isSubmitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (activeQuiz == null) {
            activeQuiz = quizGen.generateQuiz("Operating Systems", "INTERMEDIATE", 3)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        activeQuiz?.let { quiz ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Topic: ${quiz.topic}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SamText)
                    Button(
                        onClick = {
                            selectedAnswers = mutableMapOf()
                            isSubmitted = false
                            activeQuiz = quizGen.generateQuiz(quiz.topic, "INTERMEDIATE", 3)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SamDarkSurface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("New Quiz", fontSize = 12.sp, color = SamPrimary)
                    }
                }
            }

            items(quiz.questions.indices.toList()) { qIdx ->
                val q = quiz.questions[qIdx]
                val selectedOpt = selectedAnswers[qIdx]

                GlassCard {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Q${qIdx + 1}: ${q.question}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SamText)
                        Spacer(modifier = Modifier.height(10.dp))

                        q.options.forEachIndexed { optIdx, optText ->
                            val isSelected = selectedOpt == optIdx
                            val isCorrect = q.correctOptionIndex == optIdx

                            val optBg = when {
                                isSubmitted && isCorrect -> SamSuccess.copy(alpha = 0.2f)
                                isSubmitted && isSelected && !isCorrect -> SamError.copy(alpha = 0.2f)
                                isSelected -> SamPrimary.copy(alpha = 0.2f)
                                else -> SamDarkBackground
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable(enabled = !isSubmitted) {
                                        val updated = selectedAnswers.toMutableMap()
                                        updated[qIdx] = optIdx
                                        selectedAnswers = updated
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = optBg
                            ) {
                                Text(
                                    text = optText,
                                    fontSize = 13.sp,
                                    color = SamText,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (isSubmitted) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Explanation: ${q.explanation}",
                                fontSize = 12.sp,
                                color = SamSecondaryText
                            )
                        }
                    }
                }
            }

            if (!isSubmitted && selectedAnswers.size == quiz.questions.size) {
                item {
                    Button(
                        onClick = { isSubmitted = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SamPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Submit Quiz")
                    }
                }
            }
        }
    }
}

@Composable
fun StudyPlannerTab(planner: com.sam.assistant.knowledge.productivity.StudyPlanner) {
    var proposedPlan by remember { mutableStateOf<ProposedStudyPlan?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            GlassCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Autonomous Study Schedule Generator", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SamText)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Generates a balanced 7-day study proposal based on 2 hours/day available time.", fontSize = 13.sp, color = SamSecondaryText)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            proposedPlan = planner.generateProposedWeeklySchedule(
                                listOf("Operating Systems", "Computer Networks", "System Architecture", "Databases"),
                                availableHoursPerDay = 2
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SamPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate 7-Day Study Plan")
                    }
                }
            }
        }

        proposedPlan?.let { plan ->
            item {
                Text(plan.explanation, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SamSuccess)
            }
            items(plan.sessions.take(6)) { session ->
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(session.subject, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SamText)
                            Text("${session.scheduledDate} • ${session.durationMinutes} mins", fontSize = 12.sp, color = SamSecondaryText)
                        }
                        StatusBadge(text = session.topic, color = SamPrimary)
                    }
                }
            }
        }
    }
}

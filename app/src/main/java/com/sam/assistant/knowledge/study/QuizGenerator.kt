package com.sam.assistant.knowledge.study

data class QuizQuestion(
    val id: String,
    val topic: String,
    val difficulty: String,
    val question: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String
)

data class GeneratedQuiz(
    val quizId: String,
    val topic: String,
    val questions: List<QuizQuestion>
)

class QuizGenerator {

    /**
     * Generates a topic-based quiz with questions, options, answer keys and explanations.
     */
    fun generateQuiz(topic: String, difficulty: String = "INTERMEDIATE", count: Int = 3): GeneratedQuiz {
        val lower = topic.lowercase()
        val allQuestions = when {
            lower.contains("operating system") || lower.contains("os") -> getOsQuestions()
            lower.contains("network") || lower.contains("networking") || lower.contains("tcp") -> getNetworkingQuestions()
            lower.contains("data structure") || lower.contains("algorithm") -> getDsaQuestions()
            lower.contains("database") || lower.contains("sql") -> getDatabaseQuestions()
            lower.contains("ares") || lower.contains("architecture") -> getArchitectureQuestions()
            else -> getGeneralCsQuestions(topic)
        }

        val byDifficulty = allQuestions.filter { it.difficulty.equals(difficulty, ignoreCase = true) }
        val filtered = (byDifficulty + allQuestions.filterNot { it.difficulty.equals(difficulty, ignoreCase = true) }).take(count)

        return GeneratedQuiz(
            quizId = "quiz_${System.currentTimeMillis()}",
            topic = topic,
            questions = filtered
        )
    }

    private fun getOsQuestions(): List<QuizQuestion> = listOf(
        QuizQuestion(
            id = "os_1",
            topic = "Operating Systems",
            difficulty = "BEGINNER",
            question = "Which of the following conditions is NOT required for a deadlock to occur according to Coffman's conditions?",
            options = listOf("Mutual Exclusion", "Hold and Wait", "Preemption", "Circular Wait"),
            correctOptionIndex = 2,
            explanation = "Deadlock requires No Preemption. If preemption is allowed, deadlocks cannot persist."
        ),
        QuizQuestion(
            id = "os_2",
            topic = "Operating Systems",
            difficulty = "INTERMEDIATE",
            question = "What is the primary role of the Translation Lookaside Buffer (TLB)?",
            options = listOf(
                "Disk sector caching",
                "Speeding up virtual-to-physical address translation",
                "Instruction pipelining",
                "Network packet scheduling"
            ),
            correctOptionIndex = 1,
            explanation = "A TLB is a high-speed hardware cache used by the MMU to store recent virtual-to-physical page table mappings."
        ),
        QuizQuestion(
            id = "os_3",
            topic = "Operating Systems",
            difficulty = "ADVANCED",
            question = "In virtual memory systems, what is 'Thrashing'?",
            options = listOf(
                "High CPU utilization running user processes",
                "A process spending more time paging in/out than executing instructions",
                "Hard drive fragmentation failure",
                "Network interface queue overflow"
            ),
            correctOptionIndex = 1,
            explanation = "Thrashing occurs when the system lacks sufficient physical memory, causing continuous page faults and page replacement overhead."
        )
    )

    private fun getNetworkingQuestions(): List<QuizQuestion> = listOf(
        QuizQuestion(
            id = "net_1",
            topic = "Computer Networks",
            difficulty = "BEGINNER",
            question = "At which layer of the OSI model does the TCP protocol operate?",
            options = listOf("Network Layer", "Transport Layer", "Data Link Layer", "Session Layer"),
            correctOptionIndex = 1,
            explanation = "TCP (Transmission Control Protocol) is a connection-oriented Transport Layer (Layer 4) protocol."
        ),
        QuizQuestion(
            id = "net_2",
            topic = "Computer Networks",
            difficulty = "INTERMEDIATE",
            question = "What is the purpose of the 3-Way Handshake in TCP?",
            options = listOf(
                "Encrypt payload data",
                "Synchronize sequence numbers and establish a reliable connection",
                "Perform DNS lookup",
                "Compress headers"
            ),
            correctOptionIndex = 1,
            explanation = "TCP uses SYN, SYN-ACK, and ACK to synchronize initial sequence numbers between client and server."
        )
    )

    private fun getDsaQuestions(): List<QuizQuestion> = listOf(
        QuizQuestion(
            id = "dsa_1",
            topic = "Data Structures",
            difficulty = "INTERMEDIATE",
            question = "What is the average time complexity of QuickSort?",
            options = listOf("O(n)", "O(n log n)", "O(n^2)", "O(log n)"),
            correctOptionIndex = 1,
            explanation = "QuickSort operates in O(n log n) average time complexity using divide-and-conquer partitioning."
        )
    )

    private fun getDatabaseQuestions(): List<QuizQuestion> = listOf(
        QuizQuestion(
            id = "db_1",
            topic = "Databases",
            difficulty = "BEGINNER",
            question = "What does the 'I' stand for in ACID database properties?",
            options = listOf("Integrity", "Isolation", "Index", "Immutability"),
            correctOptionIndex = 1,
            explanation = "ACID stands for Atomicity, Consistency, Isolation, and Durability."
        )
    )

    private fun getArchitectureQuestions(): List<QuizQuestion> = listOf(
        QuizQuestion(
            id = "arch_1",
            topic = "System Architecture",
            difficulty = "INTERMEDIATE",
            question = "In microservices architecture, what is the main purpose of an API Gateway?",
            options = listOf(
                "Direct database connection pool",
                "Reverse proxy providing unified entry point, routing, and rate-limiting",
                "Container virtualization hypervisor",
                "Disk file indexing"
            ),
            correctOptionIndex = 1,
            explanation = "An API Gateway acts as a reverse proxy to route requests, terminate SSL, and enforce authentication."
        )
    )

    private fun getGeneralCsQuestions(topic: String): List<QuizQuestion> = listOf(
        QuizQuestion(
            id = "gen_1",
            topic = topic,
            difficulty = "INTERMEDIATE",
            question = "Regarding $topic: What is the primary benefit of modular system design?",
            options = listOf(
                "Increases tight coupling",
                "Enhances maintainability, testability, and separation of concerns",
                "Eliminates the need for testing",
                "Reduces memory bus bandwidth"
            ),
            correctOptionIndex = 1,
            explanation = "Modularity encapsulates functional boundaries, enabling independent maintenance and testing."
        )
    )
}

package com.sam.assistant.knowledge.memory

class MemoryClassifier {

    fun classify(content: String, subject: String = ""): MemoryCategory {
        val combined = "$subject $content".lowercase()

        return when {
            // Study preferences
            combined.contains("study") || combined.contains("studying") || combined.contains("exam") ||
                    combined.contains("revision") || combined.contains("syllabus") || combined.contains("lecture") ||
                    combined.contains("course") || combined.contains("assignment") || combined.contains("flashcard") ->
                MemoryCategory.STUDY_PREFERENCES

            // Project information
            combined.contains("project") || combined.contains("architecture") || combined.contains("database") ||
                    combined.contains("ares") || combined.contains("backend") || combined.contains("frontend") ||
                    combined.contains("api") || combined.contains("framework") || combined.contains("repo") ||
                    combined.contains("microservice") || combined.contains("redis") || combined.contains("postgres") ->
                MemoryCategory.PROJECT_INFORMATION

            // Application preferences
            combined.contains("browser") || combined.contains("brave") || combined.contains("chrome") ||
                    combined.contains("youtube") || combined.contains("whatsapp") || combined.contains("spotify") ||
                    combined.contains("app preference") || combined.contains("default app") ->
                MemoryCategory.APPLICATION_PREFERENCES

            // Routines
            combined.contains("routine") || combined.contains("every morning") || combined.contains("every evening") ||
                    combined.contains("daily") || combined.contains("wake up") || combined.contains("sleep at") ||
                    combined.contains("workout") ->
                MemoryCategory.ROUTINES

            // Workflow preferences
            combined.contains("workflow") || combined.contains("confirm before") || combined.contains("notification") ||
                    combined.contains("silent mode") || combined.contains("vibration") || combined.contains("backup") ->
                MemoryCategory.WORKFLOW_PREFERENCES

            // Learning history
            combined.contains("completed") || combined.contains("learned") || combined.contains("finished chapter") ||
                    combined.contains("passed quiz") || combined.contains("studied") ->
                MemoryCategory.LEARNING_HISTORY

            // Personal preferences
            combined.contains("favourite") || combined.contains("favorite") || combined.contains("prefer") ||
                    combined.contains("like to") || combined.contains("coffee") || combined.contains("tea") ||
                    combined.contains("hobby") ->
                MemoryCategory.PERSONAL_PREFERENCES

            // Default: user approved facts
            else -> MemoryCategory.USER_APPROVED_FACTS
        }
    }
}

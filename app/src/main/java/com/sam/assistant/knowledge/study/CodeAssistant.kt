package com.sam.assistant.knowledge.study

data class CodeAnalysisResult(
    val language: String,
    val explanation: String,
    val potentialIssues: List<String>,
    val timeComplexity: String,
    val spaceComplexity: String,
    val suggestions: List<String>
)

class CodeAssistant {

    /**
     * Statically inspects a code snippet without executing untrusted code.
     */
    fun analyzeCode(codeSnippet: String, languageHint: String = "Kotlin"): CodeAnalysisResult {
        val trimmed = codeSnippet.trim()
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        // Static safety check: untrusted execution prohibited
        suggestions.add("Security note: SAM inspects code statically and strictly prohibits automated execution of untrusted scripts.")

        // Basic static heuristic checks
        if (trimmed.contains("while(true)") || trimmed.contains("while (true)")) {
            issues.add("Potential infinite loop detected: Ensure a guaranteed break condition exists.")
        }
        if (trimmed.contains("Thread.sleep") || trimmed.contains(".get()")) {
            suggestions.add("Avoid blocking calls on the UI or main thread; use coroutines with Dispatchers.IO.")
        }
        if (trimmed.contains("SELECT *") || trimmed.contains("select *")) {
            suggestions.add("Specify required column names in SQL queries to optimize memory and index lookups.")
        }

        // Heuristic language detection
        val language = when {
            trimmed.contains("fun ") || trimmed.contains("val ") || trimmed.contains("var ") -> "Kotlin"
            trimmed.contains("def ") || trimmed.contains("import numpy") -> "Python"
            trimmed.contains("public class") || trimmed.contains("System.out.println") -> "Java"
            trimmed.contains("function") || trimmed.contains("const ") || trimmed.contains("let ") -> "JavaScript/TypeScript"
            else -> languageHint
        }

        // Complexity estimation
        val nestedLoops = trimmed.split(Regex("\\bfor\\b|\\bwhile\\b")).size - 1
        val timeComp = when {
            nestedLoops >= 2 -> "O(n^$nestedLoops)"
            nestedLoops == 1 -> "O(n)"
            trimmed.contains("binarySearch") || trimmed.contains("/ 2") -> "O(log n)"
            else -> "O(1) to O(n)"
        }

        return CodeAnalysisResult(
            language = language,
            explanation = "Static structural analysis of $language snippet (${trimmed.lines().size} lines).",
            potentialIssues = issues.ifEmpty { listOf("No obvious infinite loops or syntax hazards detected.") },
            timeComplexity = timeComp,
            spaceComplexity = "O(1) auxiliary",
            suggestions = suggestions
        )
    }
}

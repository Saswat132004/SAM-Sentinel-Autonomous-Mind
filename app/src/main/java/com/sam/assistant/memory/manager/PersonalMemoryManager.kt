package com.sam.assistant.memory.manager

import com.sam.assistant.memory.model.MemoryEntity
import com.sam.assistant.memory.repository.MemoryRepository
import java.util.regex.Pattern

class PersonalMemoryManager(private val repository: MemoryRepository) {

    suspend fun parseAndProcessMemoryCommand(command: String): MemoryCommandResult {
        val trimmed = command.trim()

        // 1. Check for "Forget ..." or "Delete memory ..."
        val forgetPattern = Pattern.compile("(?i)^(?:forget|delete memory(?: about)?|remove memory(?: about)?)\\s+(.+)$")
        val forgetMatcher = forgetPattern.matcher(trimmed)
        if (forgetMatcher.matches()) {
            val query = forgetMatcher.group(1)?.trim() ?: ""
            val count = repository.deleteMemoryByKey(query)
            return if (count > 0) {
                MemoryCommandResult.Success("I have forgotten the information regarding \"$query\".")
            } else {
                MemoryCommandResult.Success("I couldn't find any memory matching \"$query\" to delete.")
            }
        }

        // 2. Check for "Remember that [key] is [value]" or "Remember [key] is [value]"
        val rememberThatPattern = Pattern.compile("(?i)^remember(?: that)?\\s+(.+?)\\s+is\\s+(.+)$")
        val rememberThatMatcher = rememberThatPattern.matcher(trimmed)
        if (rememberThatMatcher.matches()) {
            val key = rememberThatMatcher.group(1)?.trim() ?: "note"
            val value = rememberThatMatcher.group(2)?.trim() ?: ""
            val category = inferCategory(key)
            repository.saveMemory(key = key, value = value, category = category)
            return MemoryCommandResult.Success("I've remembered that $key is $value.")
        }

        // 3. Check for "Remember [statement]" (general memory)
        val rememberGeneralPattern = Pattern.compile("(?i)^remember(?: that)?\\s+(.+)$")
        val rememberGeneralMatcher = rememberGeneralPattern.matcher(trimmed)
        if (rememberGeneralMatcher.matches()) {
            val statement = rememberGeneralMatcher.group(1)?.trim() ?: ""
            val key = if (statement.length > 30) statement.take(27) + "..." else statement
            repository.saveMemory(key = key, value = statement, category = "Notes")
            return MemoryCommandResult.Success("I've saved this to your personal memory: \"$statement\".")
        }

        // 4. Check for "What do you remember about [query]?" or "What [query] have I saved?"
        val queryPattern = Pattern.compile("(?i)^(?:what do you remember about|what did i tell you about|what(?:\\s+\\w+)? have i saved|what is my|what are my)\\s+(.+)\\??$")
        val queryMatcher = queryPattern.matcher(trimmed)
        if (queryMatcher.matches()) {
            val topic = queryMatcher.group(1)?.trim() ?: ""
            val cleanTopic = topic.replace("?", "").trim()
            val matches = repository.searchMemories(cleanTopic)
            if (matches.isNotEmpty()) {
                val formatted = matches.joinToString(". ") { "${it.key}: ${it.value}" }
                return MemoryCommandResult.Success("Here is what I remember: $formatted.")
            } else {
                return MemoryCommandResult.Success("I don't have any saved memories about \"$cleanTopic\".")
            }
        }

        return MemoryCommandResult.NotAMemoryCommand
    }

    private fun inferCategory(key: String): String {
        val lower = key.lowercase()
        return when {
            lower.contains("project") || lower.contains("work") || lower.contains("code") || lower.contains("task") -> "Projects"
            lower.contains("favourite") || lower.contains("favorite") || lower.contains("preference") || lower.contains("like") -> "Preferences"
            lower.contains("name") || lower.contains("email") || lower.contains("phone") || lower.contains("address") -> "Identity"
            lower.contains("meeting") || lower.contains("deadline") || lower.contains("appointment") -> "Schedule"
            else -> "Personal"
        }
    }
}

sealed class MemoryCommandResult {
    data class Success(val responseMessage: String) : MemoryCommandResult()
    object NotAMemoryCommand : MemoryCommandResult()
}


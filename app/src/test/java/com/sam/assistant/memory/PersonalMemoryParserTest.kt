package com.sam.assistant.memory

import org.junit.Assert.*
import org.junit.Test
import java.util.regex.Pattern

class PersonalMemoryParserTest {

    @Test
    fun testRememberPatternMatching() {
        val pattern = Pattern.compile("(?i)^remember(?: that)?\\s+(.+?)\\s+is\\s+(.+)$")
        
        val matcher1 = pattern.matcher("remember that my favourite programming language is Python")
        assertTrue(matcher1.matches())
        assertEquals("my favourite programming language", matcher1.group(1))
        assertEquals("Python", matcher1.group(2))

        val matcher2 = pattern.matcher("remember my project name is ARES")
        assertTrue(matcher2.matches())
        assertEquals("my project name", matcher2.group(1))
        assertEquals("ARES", matcher2.group(2))
    }

    @Test
    fun testForgetPatternMatching() {
        val pattern = Pattern.compile("(?i)^(?:forget|delete memory(?: about)?|remove memory(?: about)?)\\s+(.+)$")
        
        val matcher = pattern.matcher("forget the old project information")
        assertTrue(matcher.matches())
        assertEquals("the old project information", matcher.group(1))
    }

    @Test
    fun testMemoryQueryPatternMatching() {
        val pattern = Pattern.compile("(?i)^(?:what do you remember about|what did i tell you about|what is my|what are my)\\s+(.+?)\\??$")

        val matcher = pattern.matcher("what do you remember about projects?")
        assertTrue(matcher.matches())
        assertEquals("projects", matcher.group(1)?.replace("?", "")?.trim())
    }
}

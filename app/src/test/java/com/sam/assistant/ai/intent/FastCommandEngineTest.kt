package com.sam.assistant.ai.intent

import com.sam.assistant.automation.executor.ActionType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FastCommandEngineTest {

    private lateinit var engine: FastCommandEngine

    @Before
    fun setUp() {
        engine = FastCommandEngine()
        SessionContextManager.clearAll()
    }

    @Test
    fun testLaunchAppCommand() {
        val request = engine.parseCommand("open youtube")
        assertNotNull(request)
        assertEquals(ActionType.LAUNCH_APP, request?.actionType)
        assertEquals("youtube", request?.target)
    }

    @Test
    fun testFlashlightToggle() {
        val onReq = engine.parseCommand("turn on flashlight")
        assertNotNull(onReq)
        assertEquals(ActionType.TOGGLE_FLASHLIGHT, onReq?.actionType)
        assertEquals("on", onReq?.target)

        val offReq = engine.parseCommand("torch off")
        assertNotNull(offReq)
        assertEquals(ActionType.TOGGLE_FLASHLIGHT, offReq?.actionType)
        assertEquals("off", offReq?.target)
    }

    @Test
    fun testFlashlightHindiHinglish() {
        val onReq = engine.parseCommand("torch jalao")
        assertNotNull(onReq)
        assertEquals(ActionType.TOGGLE_FLASHLIGHT, onReq?.actionType)
        assertEquals("on", onReq?.target)

        val offReq = engine.parseCommand("torch band karo")
        assertNotNull(offReq)
        assertEquals(ActionType.TOGGLE_FLASHLIGHT, offReq?.actionType)
        assertEquals("off", offReq?.target)
    }

    @Test
    fun testBatteryCheck() {
        val request = engine.parseCommand("how much battery is left")
        assertNotNull(request)
        assertEquals(ActionType.READ_BATTERY, request?.actionType)

        val hindiReq = engine.parseCommand("battery kitni hai")
        assertNotNull(hindiReq)
        assertEquals(ActionType.READ_BATTERY, hindiReq?.actionType)
    }

    @Test
    fun testTimeCheck() {
        val request = engine.parseCommand("what time is it")
        assertNotNull(request)
        assertEquals(ActionType.READ_TIME_DATE, request?.actionType)

        val hindiReq = engine.parseCommand("kitne baje hain")
        assertNotNull(hindiReq)
        assertEquals(ActionType.READ_TIME_DATE, hindiReq?.actionType)
    }

    @Test
    fun testCallRequiresConfirmation() {
        val request = engine.parseCommand("call Mom")
        assertNotNull(request)
        assertEquals(ActionType.MAKE_CALL, request?.actionType)
        assertEquals("mom", request?.target)
        assertTrue(request?.requiresConfirmation == true)
    }

    @Test
    fun testCallIndianEnglishAndHinglish() {
        val indReq = engine.parseCommand("Do one thing, call Rahul")
        assertNotNull(indReq)
        assertEquals(ActionType.MAKE_CALL, indReq?.actionType)
        assertEquals("rahul", indReq?.target)
        assertTrue(indReq?.requiresConfirmation == true)

        val hinglishReq = engine.parseCommand("Rahul ko call lagao")
        assertNotNull(hinglishReq)
        assertEquals(ActionType.MAKE_CALL, hinglishReq?.actionType)
        assertEquals("rahul", hinglishReq?.target)
        assertTrue(hinglishReq?.requiresConfirmation == true)
    }

    @Test
    fun testPlayMusicFromBraveBrowser() {
        val request = engine.parseCommand("Play music from Brave Browser")
        assertNotNull(request)
        assertEquals(ActionType.PLAY_MEDIA, request?.actionType)
        assertEquals("brave", request?.target)

        val request2 = engine.parseCommand("Play some music using Brave Browser")
        assertNotNull(request2)
        assertEquals(ActionType.PLAY_MEDIA, request2?.actionType)
        assertEquals("brave", request2?.target)
    }

    @Test
    fun testPlayMusicAmbiguousNeedsClarification() {
        val request = engine.parseCommand("Play music")
        assertNotNull(request)
        assertEquals(ActionType.ASK_CLARIFICATION, request?.actionType)
        assertTrue(request?.payload?.contains("Should I use YouTube, Spotify or Brave?") == true)
    }

    @Test
    fun testOpenBraveAndSearchCybersecurityNews() {
        val request = engine.parseCommand("Open Brave and search for the latest cybersecurity news")
        assertNotNull(request)
        assertEquals(ActionType.SEARCH_WEB, request?.actionType)
        assertEquals("brave", request?.target)
        assertEquals("latest cybersecurity news", request?.payload)
    }

    @Test
    fun testOpenBraveAndSearchMachineLearningCourses() {
        val request = engine.parseCommand("Open Brave and search for machine learning courses")
        assertNotNull(request)
        assertEquals(ActionType.SEARCH_WEB, request?.actionType)
        assertEquals("brave", request?.target)
        assertEquals("machine learning courses", request?.payload)
    }

    @Test
    fun testOpenYouTubeSearchCybersecurityTutorials() {
        val request = engine.parseCommand("Open YouTube and search for cybersecurity tutorials")
        assertNotNull(request)
        assertEquals(ActionType.SEARCH_YOUTUBE, request?.actionType)
        assertEquals("cybersecurity tutorials", request?.payload)
    }

    @Test
    fun testOpenYouTubeSearchPythonTutorialsAndPlay() {
        val request = engine.parseCommand("Open YouTube, search for Python tutorials and play the first suitable result")
        assertNotNull(request)
        assertEquals(ActionType.SEARCH_YOUTUBE, request?.actionType)
        assertEquals("python tutorials", request?.payload)
    }

    @Test
    fun testWhatsAppHinglishPrepareMessage() {
        val request = engine.parseCommand("WhatsApp kholo aur Rahul ko message bhejne ke liye prepare karo")
        assertNotNull(request)
        assertEquals(ActionType.PREPARE_WHATSAPP, request?.actionType)
        assertEquals("rahul", request?.target)
    }

    @Test
    fun testOpenChromeSearchWeatherAndRead() {
        val request = engine.parseCommand("Open Chrome, search for the weather and read the result")
        assertNotNull(request)
        assertEquals(ActionType.SEARCH_WEB, request?.actionType)
        assertEquals("chrome", request?.target)
        assertEquals("weather", request?.payload)
    }

    @Test
    fun testAlarmParsing() {
        val request = engine.parseCommand("set alarm for 6:30 am")
        assertNotNull(request)
        assertEquals(ActionType.SET_ALARM, request?.actionType)
        assertEquals("6", request?.extraParams?.get("hour"))
        assertEquals("30", request?.extraParams?.get("minute"))
    }

    @Test
    fun testReminderParsing() {
        val request = engine.parseCommand("remind me to buy groceries in 15 minutes")
        assertNotNull(request)
        assertEquals(ActionType.CREATE_REMINDER, request?.actionType)
        assertEquals("buy groceries", request?.payload)
        assertEquals("15", request?.extraParams?.get("delayMinutes"))
    }
}

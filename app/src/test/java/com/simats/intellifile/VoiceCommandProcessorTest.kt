package com.simats.intellifile

import com.simats.intellifile.ui.main.BottomNavItem
import com.simats.intellifile.ui.voice.AppDataQueryType
import com.simats.intellifile.ui.voice.VoiceCommandProcessor
import com.simats.intellifile.ui.voice.VoiceCommandType
import com.simats.intellifile.ui.voice.VoiceIntentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandProcessorTest {

    @Test
    fun testUserRequiredVoiceNavigationCommands() {
        // 1. "Go to AI screen" -> Open AI Chat Screen
        val aiVariations = listOf(
            "Go to AI screen",
            "go to ai screen",
            "Navigate to AI screen",
            "Open AI screen",
            "ai screen",
            "Open AI chat",
            "Chat with AI",
            "Go to AI"
        )
        for (cmd in aiVariations) {
            val (type, res) = VoiceCommandProcessor.processCommand(cmd)
            assertEquals("Failed for command: $cmd", VoiceCommandType.NAVIGATION_COMMAND, type)
            assertTrue("Failed for command: $cmd", res is VoiceIntentResult.Navigation && res.targetTab == BottomNavItem.AI)
        }

        // 2. "Go to file screen" -> Open Files Screen
        val filesVariations = listOf(
            "Go to file screen",
            "go to file screen",
            "Go to files",
            "Open files",
            "Open my files",
            "Show my documents",
            "file screen",
            "files screen",
            "Navigate to files",
            "open file screen"
        )
        for (cmd in filesVariations) {
            val (type, res) = VoiceCommandProcessor.processCommand(cmd)
            assertEquals("Failed for command: $cmd", VoiceCommandType.NAVIGATION_COMMAND, type)
            assertTrue("Failed for command: $cmd", res is VoiceIntentResult.Navigation && res.targetTab == BottomNavItem.FILES)
        }

        // 3. "Go to saved screen" -> Open Saved Files Screen
        val savedVariations = listOf(
            "Go to saved screen",
            "go to saved screen",
            "Show saved files",
            "saved screen",
            "Open saved files",
            "go to saved files",
            "show bookmarks",
            "open bookmarks",
            "saved files"
        )
        for (cmd in savedVariations) {
            val (type, res) = VoiceCommandProcessor.processCommand(cmd)
            assertEquals("Failed for command: $cmd", VoiceCommandType.NAVIGATION_COMMAND, type)
            assertTrue("Failed for command: $cmd", res is VoiceIntentResult.Navigation && res.targetTab == BottomNavItem.SAVED)
        }

        // 4. "Go to profile screen" -> Open Profile Screen
        val profileVariations = listOf(
            "Go to profile screen",
            "go to profile screen",
            "Open my profile",
            "open profile",
            "Go to profile",
            "profile screen",
            "Navigate to profile"
        )
        for (cmd in profileVariations) {
            val (type, res) = VoiceCommandProcessor.processCommand(cmd)
            assertEquals("Failed for command: $cmd", VoiceCommandType.NAVIGATION_COMMAND, type)
            assertTrue("Failed for command: $cmd", res is VoiceIntentResult.Navigation && res.targetTab == BottomNavItem.PROFILE)
        }

        // 5. "Go to home screen" -> Open Home Screen
        val homeVariations = listOf(
            "Go to home screen",
            "go to home screen",
            "Go to home",
            "open home",
            "open home screen",
            "home screen",
            "Go home"
        )
        for (cmd in homeVariations) {
            val (type, res) = VoiceCommandProcessor.processCommand(cmd)
            assertEquals("Failed for command: $cmd", VoiceCommandType.NAVIGATION_COMMAND, type)
            assertTrue("Failed for command: $cmd", res is VoiceIntentResult.Navigation && res.targetTab == BottomNavItem.HOME)
        }

        // 6. "Go back" -> Navigate to previous screen
        val backVariations = listOf(
            "Go back",
            "go back",
            "back",
            "previous screen",
            "navigate back",
            "take me back",
            "return"
        )
        for (cmd in backVariations) {
            val (type, res) = VoiceCommandProcessor.processCommand(cmd)
            assertEquals("Failed for command: $cmd", VoiceCommandType.NAVIGATION_COMMAND, type)
            assertTrue("Failed for command: $cmd", res is VoiceIntentResult.Navigation && res.isBack)
        }
    }

    @Test
    fun testFileSearchAndActionCommands() {
        // File Search
        val (type1, res1) = VoiceCommandProcessor.processCommand("Find my assignment PDF")
        assertEquals(VoiceCommandType.FILE_SEARCH_COMMAND, type1)
        assertTrue(res1 is VoiceIntentResult.FileSearch)

        val (type2, res2) = VoiceCommandProcessor.processCommand("Search for Java notes")
        assertEquals(VoiceCommandType.FILE_SEARCH_COMMAND, type2)
        assertTrue(res2 is VoiceIntentResult.FileSearch)

        val (type3, res3) = VoiceCommandProcessor.processCommand("Show all PDF files")
        assertEquals(VoiceCommandType.FILE_SEARCH_COMMAND, type3)
        assertTrue(res3 is VoiceIntentResult.FileSearch && res3.formatFilter == "PDF")

        val (type4, res4) = VoiceCommandProcessor.processCommand("Find files containing the word machine learning")
        assertEquals(VoiceCommandType.FILE_SEARCH_COMMAND, type4)
        assertTrue(res4 is VoiceIntentResult.FileSearch)

        val (type5, res5) = VoiceCommandProcessor.processCommand("Where is my resume?")
        assertEquals(VoiceCommandType.FILE_SEARCH_COMMAND, type5)
        assertTrue(res5 is VoiceIntentResult.FileSearch)

        // Content-based natural language queries
        val (typeNL1, resNL1) = VoiceCommandProcessor.processCommand("Find my college assignment.")
        assertEquals(VoiceCommandType.FILE_SEARCH_COMMAND, typeNL1)
        assertTrue(resNL1 is VoiceIntentResult.FileSearch && (resNL1 as VoiceIntentResult.FileSearch).query == "college assignment")

        val (typeNL2, resNL2) = VoiceCommandProcessor.processCommand("Show the PDF containing machine learning notes.")
        assertEquals(VoiceCommandType.FILE_SEARCH_COMMAND, typeNL2)
        assertTrue(resNL2 is VoiceIntentResult.FileSearch && (resNL2 as VoiceIntentResult.FileSearch).query == "machine learning notes" && (resNL2 as VoiceIntentResult.FileSearch).formatFilter == "PDF")

        // File Action (Open)
        val (type6, res6) = VoiceCommandProcessor.processCommand("Open my project report")
        assertEquals(VoiceCommandType.FILE_ACTION_COMMAND, type6)
        assertTrue(res6 is VoiceIntentResult.FileAction && res6.targetFileName.contains("project report"))
    }

    @Test
    fun testAppDataQueries() {
        // Total files
        val (type1, res1) = VoiceCommandProcessor.processCommand("How many total files do I have?")
        assertEquals(VoiceCommandType.APP_DATA_QUERY, type1)
        assertTrue(res1 is VoiceIntentResult.AppDataQuery && res1.queryType == AppDataQueryType.TOTAL_FILES)

        // PDF count
        val (type2, res2) = VoiceCommandProcessor.processCommand("How many PDF files are available?")
        assertEquals(VoiceCommandType.APP_DATA_QUERY, type2)
        assertTrue(res2 is VoiceIntentResult.AppDataQuery && res2.queryType == AppDataQueryType.FORMAT_COUNT && res2.format == "PDF")

        // Important files
        val (type3, res3) = VoiceCommandProcessor.processCommand("How many important files are saved?")
        assertEquals(VoiceCommandType.APP_DATA_QUERY, type3)
        assertTrue(res3 is VoiceIntentResult.AppDataQuery && res3.queryType == AppDataQueryType.IMPORTANT_COUNT)

        // Bookmarked files
        val (type4, res4) = VoiceCommandProcessor.processCommand("How many bookmarked files do I have?")
        assertEquals(VoiceCommandType.APP_DATA_QUERY, type4)
        assertTrue(res4 is VoiceIntentResult.AppDataQuery && res4.queryType == AppDataQueryType.BOOKMARKED_COUNT)

        // Recent files
        val (type5, res5) = VoiceCommandProcessor.processCommand("Show my recently opened files")
        assertEquals(VoiceCommandType.APP_DATA_QUERY, type5)
        assertTrue(res5 is VoiceIntentResult.AppDataQuery && res5.queryType == AppDataQueryType.RECENT_FILES)
    }

    @Test
    fun testGeneralAiAndDocumentQueries() {
        // General AI
        val (type1, _) = VoiceCommandProcessor.processCommand("What is Artificial Intelligence?")
        assertEquals(VoiceCommandType.GENERAL_AI_QUERY, type1)

        val (type2, _) = VoiceCommandProcessor.processCommand("Explain Python")
        assertEquals(VoiceCommandType.GENERAL_AI_QUERY, type2)

        val (type3, _) = VoiceCommandProcessor.processCommand("What is Machine Learning?")
        assertEquals(VoiceCommandType.GENERAL_AI_QUERY, type3)

        // Document RAG
        val (type5, res5) = VoiceCommandProcessor.processCommand("Summarize my document")
        assertEquals(VoiceCommandType.DOCUMENT_QUERY, type5)
        assertTrue(res5 is VoiceIntentResult.AiQuery && res5.isDocumentQuery)
    }

    @Test
    fun testEmptyOrUnclearCommands() {
        val (type1, _) = VoiceCommandProcessor.processCommand("")
        assertEquals(VoiceCommandType.UNKNOWN_COMMAND, type1)

        val (type2, _) = VoiceCommandProcessor.processCommand(" ")
        assertEquals(VoiceCommandType.UNKNOWN_COMMAND, type2)
    }
}

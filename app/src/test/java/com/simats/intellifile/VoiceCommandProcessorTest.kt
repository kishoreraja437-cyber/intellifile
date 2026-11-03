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
    fun testNavigationCommands() {
        // Profile
        val (type1, res1) = VoiceCommandProcessor.processCommand("Go to Profile")
        assertEquals(VoiceCommandType.NAVIGATION_COMMAND, type1)
        assertTrue(res1 is VoiceIntentResult.Navigation && res1.targetTab == BottomNavItem.PROFILE)

        val (type2, res2) = VoiceCommandProcessor.processCommand("Open My Profile")
        assertEquals(VoiceCommandType.NAVIGATION_COMMAND, type2)
        assertTrue(res2 is VoiceIntentResult.Navigation && res2.targetTab == BottomNavItem.PROFILE)

        // Files
        val (type3, res3) = VoiceCommandProcessor.processCommand("Go to Files")
        assertEquals(VoiceCommandType.NAVIGATION_COMMAND, type3)
        assertTrue(res3 is VoiceIntentResult.Navigation && res3.targetTab == BottomNavItem.FILES)

        val (type4, res4) = VoiceCommandProcessor.processCommand("Show My Documents")
        assertEquals(VoiceCommandType.NAVIGATION_COMMAND, type4)
        assertTrue(res4 is VoiceIntentResult.Navigation && res4.targetTab == BottomNavItem.FILES)

        // Saved / Bookmarks
        val (type5, res5) = VoiceCommandProcessor.processCommand("Open Saved Files")
        assertEquals(VoiceCommandType.NAVIGATION_COMMAND, type5)
        assertTrue(res5 is VoiceIntentResult.Navigation && res5.targetTab == BottomNavItem.SAVED)

        val (type6, res6) = VoiceCommandProcessor.processCommand("Show Bookmarks")
        assertEquals(VoiceCommandType.NAVIGATION_COMMAND, type6)
        assertTrue(res6 is VoiceIntentResult.Navigation && res6.targetTab == BottomNavItem.SAVED)

        // AI Chat
        val (type7, res7) = VoiceCommandProcessor.processCommand("Open AI Chat")
        assertEquals(VoiceCommandType.NAVIGATION_COMMAND, type7)
        assertTrue(res7 is VoiceIntentResult.Navigation && res7.targetTab == BottomNavItem.AI)

        // Home
        val (type8, res8) = VoiceCommandProcessor.processCommand("Go to Home")
        assertEquals(VoiceCommandType.NAVIGATION_COMMAND, type8)
        assertTrue(res8 is VoiceIntentResult.Navigation && res8.targetTab == BottomNavItem.HOME)

        // Go Back
        val (type9, res9) = VoiceCommandProcessor.processCommand("Go back")
        assertEquals(VoiceCommandType.NAVIGATION_COMMAND, type9)
        assertTrue(res9 is VoiceIntentResult.Navigation && res9.isBack)
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

        // Content-based and natural language voice queries
        val (typeNL1, resNL1) = VoiceCommandProcessor.processCommand("Find my college assignment.")
        assertEquals(VoiceCommandType.FILE_SEARCH_COMMAND, typeNL1)
        assertTrue(resNL1 is VoiceIntentResult.FileSearch && (resNL1 as VoiceIntentResult.FileSearch).query == "college assignment")

        val (typeNL2, resNL2) = VoiceCommandProcessor.processCommand("Show the PDF containing machine learning notes.")
        assertEquals(VoiceCommandType.FILE_SEARCH_COMMAND, typeNL2)
        assertTrue(resNL2 is VoiceIntentResult.FileSearch && (resNL2 as VoiceIntentResult.FileSearch).query == "machine learning notes" && (resNL2 as VoiceIntentResult.FileSearch).formatFilter == "PDF")

        val (typeNL3, resNL3) = VoiceCommandProcessor.processCommand("Find the document where I wrote about Python.")
        assertEquals(VoiceCommandType.FILE_SEARCH_COMMAND, typeNL3)
        assertTrue(resNL3 is VoiceIntentResult.FileSearch && (resNL3 as VoiceIntentResult.FileSearch).query == "Python")

        val (typeNL4, resNL4) = VoiceCommandProcessor.processCommand("I forgot the filename, but it contains information about cloud computing.")
        assertEquals(VoiceCommandType.FILE_SEARCH_COMMAND, typeNL4)
        assertTrue(resNL4 is VoiceIntentResult.FileSearch && (resNL4 as VoiceIntentResult.FileSearch).query == "cloud computing")

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

        val (type4, _) = VoiceCommandProcessor.processCommand("Give me an example of cloud computing")
        assertEquals(VoiceCommandType.GENERAL_AI_QUERY, type4)

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


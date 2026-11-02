package com.simats.intellifile

import com.simats.intellifile.ui.ai.AiChatIntentDetector
import com.simats.intellifile.ui.ai.AiUserIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatIntentDetectorTest {

    @Test
    fun testFileSearch_showMyJavaNotes() {
        val intent = AiChatIntentDetector.detect("Show my Java notes")
        assertTrue("Expected FileSearch, got: $intent", intent is AiUserIntent.FileSearch)
        val search = intent as AiUserIntent.FileSearch
        assertEquals("Java notes", search.query)
    }

    @Test
    fun testFileSearch_findAssignmentPdf() {
        val intent = AiChatIntentDetector.detect("Find assignment.pdf")
        assertTrue("Expected FileSearch, got: $intent", intent is AiUserIntent.FileSearch)
        val search = intent as AiUserIntent.FileSearch
        assertEquals("assignment.pdf", search.query)
        assertEquals("PDF", search.format)
    }

    @Test
    fun testFileSearch_searchForProjectReport() {
        val intent = AiChatIntentDetector.detect("Search for project report")
        assertTrue("Expected FileSearch, got: $intent", intent is AiUserIntent.FileSearch)
        val search = intent as AiUserIntent.FileSearch
        assertEquals("project report", search.query)
    }

    @Test
    fun testFileSearch_findMyResume() {
        val intent = AiChatIntentDetector.detect("Find my resume")
        assertTrue("Expected FileSearch, got: $intent", intent is AiUserIntent.FileSearch)
        val search = intent as AiUserIntent.FileSearch
        assertEquals("resume", search.query)
    }

    @Test
    fun testFileTypeFilter_showAllPdfFiles() {
        val intent = AiChatIntentDetector.detect("Show all PDF files")
        assertTrue("Expected FileTypeFilter, got: $intent", intent is AiUserIntent.FileTypeFilter)
        val filter = intent as AiUserIntent.FileTypeFilter
        assertEquals("PDF", filter.format)
    }

    @Test
    fun testFileTypeFilter_findWordDocuments() {
        val intent = AiChatIntentDetector.detect("Show word documents")
        assertTrue("Expected FileTypeFilter, got: $intent", intent is AiUserIntent.FileTypeFilter)
        val filter = intent as AiUserIntent.FileTypeFilter
        assertEquals("DOCX", filter.format)
    }

    @Test
    fun testStandaloneFileName_assignmentPdf() {
        val intent = AiChatIntentDetector.detect("assignment.pdf")
        assertTrue("Expected FileSearch, got: $intent", intent is AiUserIntent.FileSearch)
        val search = intent as AiUserIntent.FileSearch
        assertEquals("assignment.pdf", search.query)
        assertEquals("PDF", search.format)
    }

    @Test
    fun testFileOpen_openAssignmentPdf() {
        val intent = AiChatIntentDetector.detect("Open assignment.pdf")
        assertTrue("Expected FileOpen, got: $intent", intent is AiUserIntent.FileOpen)
        val open = intent as AiUserIntent.FileOpen
        assertEquals("assignment.pdf", open.targetName)
    }

    @Test
    fun testGeneralQuery_whatIsArtificialIntelligence() {
        val intent = AiChatIntentDetector.detect("What is Artificial Intelligence?")
        assertTrue("Expected GeneralQuery, got: $intent", intent is AiUserIntent.GeneralQuery)
    }

    @Test
    fun testAppDataQuery_howManyTotalFiles() {
        val intent = AiChatIntentDetector.detect("How many total files are stored in my IntelliFile?")
        assertTrue("Expected AppDataQuery, got: $intent", intent is AiUserIntent.AppDataQuery)
    }

    @Test
    fun testDocumentQuery_summarizeMyPdf() {
        val intent = AiChatIntentDetector.detect("Summarize my PDF")
        assertTrue("Expected DocumentQuery, got: $intent", intent is AiUserIntent.DocumentQuery)
    }
}


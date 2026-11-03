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
    fun testFileSearch_findMyCollegeAssignment() {
        val intent = AiChatIntentDetector.detect("Find my college assignment.")
        assertTrue("Expected FileSearch, got: $intent", intent is AiUserIntent.FileSearch)
        val search = intent as AiUserIntent.FileSearch
        assertEquals("college assignment", search.query)
    }

    @Test
    fun testFileSearch_showThePdfContainingMachineLearningNotes() {
        val intent = AiChatIntentDetector.detect("Show the PDF containing machine learning notes.")
        assertTrue("Expected FileSearch, got: $intent", intent is AiUserIntent.FileSearch)
        val search = intent as AiUserIntent.FileSearch
        assertEquals("machine learning notes", search.query)
        assertEquals("PDF", search.format)
    }

    @Test
    fun testFileSearch_findDocumentWhereIWroteAboutPython() {
        val intent = AiChatIntentDetector.detect("Find the document where I wrote about Python.")
        assertTrue("Expected FileSearch, got: $intent", intent is AiUserIntent.FileSearch)
        val search = intent as AiUserIntent.FileSearch
        assertEquals("Python", search.query)
    }

    @Test
    fun testFileSearch_forgotFilenameContainsCloudComputing() {
        val intent = AiChatIntentDetector.detect("I forgot the filename, but it contains information about cloud computing.")
        assertTrue("Expected FileSearch, got: $intent", intent is AiUserIntent.FileSearch)
        val search = intent as AiUserIntent.FileSearch
        assertEquals("cloud computing", search.query)
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

    @Test
    fun testFileSearch_standaloneAssignment() {
        val intent = AiChatIntentDetector.detect("assignment")
        assertTrue("Expected FileSearch, got: $intent", intent is AiUserIntent.FileSearch)
        val search = intent as AiUserIntent.FileSearch
        assertEquals("assignment", search.query)
    }

    @Test
    fun testFileSearch_personNameRameshKumar() {
        val intent = AiChatIntentDetector.detect("Ramesh Kumar")
        assertTrue("Expected FileSearch, got: $intent", intent is AiUserIntent.FileSearch)
        val search = intent as AiUserIntent.FileSearch
        assertEquals("Ramesh Kumar", search.query)
    }

    @Test
    fun testFileSearch_personNameJohnDoe() {
        val intent = AiChatIntentDetector.detect("John Doe")
        assertTrue("Expected FileSearch, got: $intent", intent is AiUserIntent.FileSearch)
        val search = intent as AiUserIntent.FileSearch
        assertEquals("John Doe", search.query)
    }

    @Test
    fun testFileSearch_cloudComputingNotes() {
        val intent = AiChatIntentDetector.detect("cloud computing notes")
        assertTrue("Expected FileSearch, got: $intent", intent is AiUserIntent.FileSearch)
        val search = intent as AiUserIntent.FileSearch
        assertEquals("cloud computing notes", search.query)
    }
}

package com.simats.intellifile.ui.ai

sealed class AiUserIntent {
    data class FileSearch(val query: String, val format: String? = null) : AiUserIntent()
    data class FileOpen(val targetName: String) : AiUserIntent()
    data class FileTypeFilter(val format: String) : AiUserIntent()
    data class AppDataQuery(val rawPrompt: String) : AiUserIntent()
    data class DocumentQuery(val rawPrompt: String) : AiUserIntent()
    data class GeneralQuery(val rawPrompt: String) : AiUserIntent()
}

object AiChatIntentDetector {

    /**
     * Classifies the user's prompt into one of the core AI Chat intents:
     * - FILE_SEARCH: Search actual files and display file cards.
     * - FILE_OPEN: Open the requested file directly.
     * - FILE_TYPE_FILTER: Search files by specific format/type.
     * - APP_DATA_QUERY: Live queries about user's IntelliFile data, counts, priorities.
     * - DOCUMENT_QUERY: Document analysis, summaries, or questions answered via RAG.
     * - GENERAL_QUERY: General knowledge / coding questions answered via SLM.
     */
    fun detect(prompt: String): AiUserIntent {
        val clean = prompt.trim()
        val lower = clean.lowercase()

        // 1. Check for document summary / explanation / analysis -> DOCUMENT_QUERY
        if (containsAny(
                lower,
                "summar",
                "explain",
                "key point",
                "compare",
                "analysis",
                "takeaway",
                "what is chapter",
                "second chapter",
                "first chapter",
                "read this document"
            )
        ) {
            return AiUserIntent.DocumentQuery(clean)
        }

        // 2. Priority commands or Priority explanations -> APP_DATA_QUERY
        if (lower.startsWith("set priority") ||
            lower.startsWith("change priority") ||
            lower.startsWith("mark priority") ||
            lower.startsWith("clear priority") ||
            lower.startsWith("remove priority") ||
            lower.startsWith("what is the priority") ||
            lower.contains("how to set priority") ||
            lower.contains("how can i set priority") ||
            lower.contains("how do i set priority") ||
            lower.contains("can you set the priority")
        ) {
            return AiUserIntent.AppDataQuery(clean)
        }

        // 3. IntelliFile App Statistics / Counts / Categories -> APP_DATA_QUERY
        if (containsAny(
                lower,
                "how many",
                "total count",
                "count of",
                "stored in my intellifile",
                "in my intellifile",
                "my total document count",
                "which file type do i use most",
                "files were uploaded today",
                "recently uploaded files",
                "bookmarked files",
                "saved files count",
                "important files are saved"
            )
        ) {
            return AiUserIntent.AppDataQuery(clean)
        }

        // 4. FILE_OPEN: e.g. "open assignment.pdf", "open my resume", "launch report.docx", "view Java notes"
        val openRegex = Regex(
            "^(?:open|view|launch|read)\\s+(?:the\\s+file\\s+|the\\s+document\\s+|my\\s+|the\\s+)?(.+?)[.?!]?$",
            RegexOption.IGNORE_CASE
        )
        val openMatch = openRegex.find(clean)
        if (openMatch != null) {
            val target = openMatch.groupValues[1].trim(' ', '\'', '"')
            if (target.isNotBlank()) {
                return AiUserIntent.FileOpen(target)
            }
        }

        // 5. FILE_TYPE_FILTER: e.g. "show all PDF files", "find all docx files", "show my PPT files", "display excel files"
        val filterRegex = Regex(
            "^(?:show|find|list|display|get)\\s+(?:all\\s+)?(?:my\\s+)?(pdf|docx?|word|pptx?|powerpoint|txt|text|csv|xlsx?|excel)\\s*(?:files?|documents?|\\s*$)",
            RegexOption.IGNORE_CASE
        )
        val filterMatch = filterRegex.find(clean)
        if (filterMatch != null) {
            val rawFmt = filterMatch.groupValues[1].uppercase()
            val normalizedFmt = when (rawFmt) {
                "DOC", "WORD" -> "DOCX"
                "PPT", "POWERPOINT" -> "PPT"
                "XLS", "EXCEL" -> "XLSX"
                "TEXT" -> "TXT"
                else -> rawFmt
            }
            return AiUserIntent.FileTypeFilter(normalizedFmt)
        }

        // 6. FILE_SEARCH: e.g. "Show my Java notes", "Find assignment.pdf", "Search for project report", "Find my resume", "Where is my salary slip"
        val searchPatterns = listOf(
            Regex(
                "^(?:find|search(?:\\s+for)?|where\\s+is|where\\s+are|look\\s+for|show\\s+me|show|locate|get)\\s+(?:files?\\s+(?:containing|with)\\s+)?(?:my\\s+|the\\s+)?(.+?)[.?!]?$",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "^(?:do\\s+i\\s+have|is\\s+there)\\s+(?:any\\s+)?(?:files?\\s+(?:on|for|about)\\s+|my\\s+|the\\s+)?(.+?)[?]?$",
                RegexOption.IGNORE_CASE
            )
        )
        for (pattern in searchPatterns) {
            val m = pattern.find(clean)
            if (m != null) {
                var target = m.groupValues[1].trim(' ', '\'', '"')
                target = target.replace(
                    Regex(
                        "^(the\\s+file\\s+|the\\s+document\\s+|file\\s+|document\\s+)",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )
                target = target.replace(
                    Regex(
                        "(\\s+file|\\s+document|\\s+files|\\s+documents)$",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )
                target = target.trim()

                // Check if target contains a file format
                val fmtRegex = Regex("\\b(pdf|docx?|pptx?|txt|csv|xlsx?)\\b", RegexOption.IGNORE_CASE)
                val fmt = fmtRegex.find(target)?.groupValues?.get(1)?.uppercase()

                if (target.isNotBlank()) {
                    return AiUserIntent.FileSearch(query = target, format = fmt)
                }
            }
        }

        // 7. Standalone file name (e.g. "assignment.pdf", "Java notes.txt", "project_report.docx")
        val standaloneDocRegex = Regex(
            "^([a-zA-Z0-9_\\-\\s()]+\\.(pdf|docx?|pptx?|xlsx?|txt|csv))[.?!]?$",
            RegexOption.IGNORE_CASE
        )
        val standaloneMatch = standaloneDocRegex.find(clean)
        if (standaloneMatch != null) {
            val fname = standaloneMatch.groupValues[1].trim()
            val ext = standaloneMatch.groupValues[2].uppercase()
            return AiUserIntent.FileSearch(query = fname, format = ext)
        }

        // 8. GENERAL_QUERY: General questions not related to user documents or IntelliFile
        if (lower.startsWith("what is") ||
            lower.startsWith("what are") ||
            lower.startsWith("how does") ||
            lower.startsWith("why is") ||
            lower.startsWith("tell me about") ||
            lower.startsWith("write a") ||
            lower.startsWith("who is") ||
            lower.startsWith("explain artificial intelligence") ||
            lower.startsWith("explain machine learning") ||
            lower.startsWith("what is python") ||
            lower.startsWith("how does cloud")
        ) {
            return AiUserIntent.GeneralQuery(clean)
        }

        // Default to Document Query (RAG pipeline)
        return AiUserIntent.DocumentQuery(clean)
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        for (kw in keywords) {
            if (text.contains(kw, ignoreCase = true)) return true
        }
        return false
    }
}

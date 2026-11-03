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
     * Extracts clean topic and format filter from natural language queries.
     * E.g.
     * "Show the PDF containing machine learning notes" -> ("machine learning notes", "PDF")
     * "Find the document where I wrote about Python" -> ("Python", null)
     * "I forgot the filename, but it contains information about cloud computing" -> ("cloud computing", null)
     * "Find my college assignment." -> ("college assignment", null)
     * "Show my Java notes" -> ("Java notes", null)
     */
    fun extractSearchTopic(clean: String): Pair<String, String?> {
        val lower = clean.lowercase().trimEnd('?', '.', '!', ',')

        // 1. Detect file format filter if mentioned
        var format: String? = null
        if (Regex("\\b(pdf|pdfs)\\b").containsMatchIn(lower)) {
            format = "PDF"
        } else if (Regex("\\b(docx?|word)\\b").containsMatchIn(lower)) {
            format = "DOCX"
        } else if (Regex("\\b(pptx?|powerpoint|slides?)\\b").containsMatchIn(lower)) {
            format = "PPTX"
        } else if (Regex("\\b(xlsx?|excel|sheets?)\\b").containsMatchIn(lower)) {
            format = "XLSX"
        } else if (Regex("\\b(txt|text)\\b").containsMatchIn(lower)) {
            format = "TXT"
        } else if (Regex("\\b(csv)\\b").containsMatchIn(lower)) {
            format = "CSV"
        } else if (Regex("\\b(json)\\b").containsMatchIn(lower)) {
            format = "JSON"
        }

        var target = clean

        // 2. Strip leading conversational preambles
        val prefixPatterns = listOf(
            Regex("^(?:i\\s+(?:forgot|don'?t\\s+remember)\\s+the\\s+filename[,\\s]+(?:but\\s+)?it\\s+contains(?:\\s+information\\s+about|\\s+notes\\s+on|\\s+info\\s+about)?)\\s*", RegexOption.IGNORE_CASE),
            Regex("^(?:i\\s+(?:forgot|don'?t\\s+remember)[,\\s]+(?:but\\s+)?it\\s+contains(?:\\s+information\\s+about|\\s+notes\\s+on|\\s+info\\s+about)?)\\s*", RegexOption.IGNORE_CASE),
            Regex("^(?:find|search(?:\\s+for)?|where\\s+is|where\\s+are|look\\s+for|show\\s+me|show|locate|get)\\s+(?:the\\s+file\\s+|the\\s+document\\s+|the\\s+pdf\\s+|the\\s+word\\s+doc\\s+|my\\s+|the\\s+)?", RegexOption.IGNORE_CASE),
            Regex("^(?:do\\s+i\\s+have|is\\s+there)\\s+(?:any\\s+)?(?:files?\\s+(?:on|for|about)\\s+|my\\s+|the\\s+)?", RegexOption.IGNORE_CASE),
            Regex("^(?:open|view|read|launch)\\s+(?:the\\s+file\\s+|the\\s+document\\s+|my\\s+|the\\s+)?", RegexOption.IGNORE_CASE)
        )

        for (pattern in prefixPatterns) {
            target = target.replace(pattern, "")
        }

        // 3. Strip connectors
        val connectorPatterns = listOf(
            Regex("\\b(?:where\\s+i\\s+(?:wrote|talked|discussed|mentioned|wrote\\s+about|talked\\s+about)\\s+about)\\s*", RegexOption.IGNORE_CASE),
            Regex("\\b(?:where\\s+i\\s+(?:wrote|talked|discussed|mentioned))\\s*", RegexOption.IGNORE_CASE),
            Regex("\\b(?:containing\\s+(?:information\\s+about|info\\s+about|notes\\s+on|notes\\s+about)?)\\s*", RegexOption.IGNORE_CASE),
            Regex("\\b(?:contains\\s+(?:information\\s+about|info\\s+about|notes\\s+on|notes\\s+about)?)\\s*", RegexOption.IGNORE_CASE),
            Regex("\\b(?:information\\s+about|info\\s+about|notes\\s+on|notes\\s+about|related\\s+to)\\s*", RegexOption.IGNORE_CASE),
            Regex("\\b(?:document\\s+about|file\\s+about|pdf\\s+about)\\s*", RegexOption.IGNORE_CASE),
            Regex("^(?:the\\s+pdf|the\\s+file|the\\s+document|my\\s+document|my\\s+file|my\\s+pdf)\\s*", RegexOption.IGNORE_CASE)
        )

        for (pattern in connectorPatterns) {
            target = target.replace(pattern, "")
        }

        // 4. Strip trailing "file / document" suffix
        target = target.replace(Regex("(\\s+file|\\s+document|\\s+files|\\s+documents)$", RegexOption.IGNORE_CASE), "")
        target = target.trim(' ', '\t', '\n', '\r', '\'', '"', '.', ',', '?', '!')

        return Pair(target, format)
    }

    /**
     * Classifies the user's prompt into one of the core AI Chat intents:
     * - FILE_SEARCH: Search actual files across mobile & AI vault and display file cards.
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

        // 6. FILE_SEARCH: Natural Language & Content File Search (Mobile & AI Vault)
        // e.g. "Find assignment.pdf", "Find my college assignment", "Show the PDF containing machine learning notes",
        // "Find the document where I wrote about Python", "I forgot the filename, but it contains information about cloud computing"
        val isSearchIntent = lower.startsWith("find") ||
                lower.startsWith("search") ||
                lower.startsWith("where is") ||
                lower.startsWith("where are") ||
                lower.startsWith("look for") ||
                lower.startsWith("show me") ||
                lower.startsWith("show") ||
                lower.startsWith("locate") ||
                lower.startsWith("get") ||
                lower.startsWith("do i have") ||
                lower.startsWith("is there") ||
                lower.startsWith("i forgot") ||
                lower.startsWith("i don't remember") ||
                lower.startsWith("i dont remember") ||
                lower.startsWith("which file") ||
                lower.startsWith("which document") ||
                lower.contains("containing") ||
                lower.contains("contains") ||
                lower.contains("wrote about") ||
                lower.contains("information about") ||
                lower.contains("notes on")

        if (isSearchIntent) {
            val (topic, fmt) = extractSearchTopic(clean)
            if (topic.isNotBlank()) {
                return AiUserIntent.FileSearch(query = topic, format = fmt)
            }
        }

        // 7. Standalone file name without search verb (e.g. "assignment.pdf", "Java notes.txt", "project_report.docx")
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

        // 8. Standalone topic, filename, or candidate keywords without question markers (1 to 5 words)
        // e.g. "Ramesh Kumar", "assignment", "cloud computing notes", "John Doe", "doctor prescription"
        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isNotEmpty() && words.size <= 5) {
            val firstWord = words[0].lowercase().trimEnd('?', ':', ',')
            val questionWords = setOf(
                "what", "why", "how", "when", "who", "where", "which",
                "can", "could", "would", "should", "is", "are", "was",
                "were", "do", "does", "did", "summarize", "explain", "compare", "tell"
            )
            if (!questionWords.contains(firstWord) && !clean.endsWith("?")) {
                val (topic, fmt) = extractSearchTopic(clean)
                val finalTopic = if (topic.isNotBlank()) topic else clean
                return AiUserIntent.FileSearch(query = finalTopic, format = fmt)
            }
        }

        // 9. GENERAL_QUERY: General questions not related to user documents or IntelliFile
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

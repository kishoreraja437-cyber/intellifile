package com.simats.intellifile.ui.voice

import com.simats.intellifile.ui.home.PriorityLevel
import com.simats.intellifile.ui.main.BottomNavItem

enum class VoiceCommandType {
    NAVIGATION_COMMAND,
    FILE_SEARCH_COMMAND,
    FILE_ACTION_COMMAND,
    APP_DATA_QUERY,
    GENERAL_AI_QUERY,
    DOCUMENT_QUERY,
    UNKNOWN_COMMAND
}

enum class AppDataQueryType {
    TOTAL_FILES,
    FORMAT_COUNT,
    IMPORTANT_COUNT,
    BOOKMARKED_COUNT,
    RECENT_FILES
}

sealed class VoiceIntentResult {
    data class Navigation(
        val targetTab: BottomNavItem? = null,
        val overlayKey: String? = null,
        val isBack: Boolean = false,
        val spokenConfirmation: String
    ) : VoiceIntentResult()

    data class FileAction(
        val targetFileName: String,
        val isActionOpen: Boolean = true
    ) : VoiceIntentResult()

    data class FileSearch(
        val query: String,
        val formatFilter: String? = null,
        val priorityFilter: PriorityLevel? = null
    ) : VoiceIntentResult()

    data class AppDataQuery(
        val queryType: AppDataQueryType,
        val format: String? = null
    ) : VoiceIntentResult()

    data class AiQuery(
        val prompt: String,
        val isDocumentQuery: Boolean = false
    ) : VoiceIntentResult()

    data class Clarify(
        val message: String
    ) : VoiceIntentResult()
}

object VoiceCommandProcessor {

    fun processCommand(rawInput: String): Pair<VoiceCommandType, VoiceIntentResult> {
        val trimmed = rawInput.trim()
        if (trimmed.length < 2) {
            return Pair(
                VoiceCommandType.UNKNOWN_COMMAND,
                VoiceIntentResult.Clarify("I didn't quite catch that. Please speak again.")
            )
        }

        // Clean punctuation, extra whitespace, convert to lower case
        val clean = trimmed.lowercase()
            .replace(Regex("[?!.,:;'\"\\-_]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val words = clean.split(" ")

        // -------------------------------------------------------------
        // 0. APP_DATA_QUERIES (Check counts and statistics first)
        // -------------------------------------------------------------
        if (clean.contains("how many") || clean.contains("number of") || clean.contains("total files")) {
            if (clean.contains("bookmark")) {
                return Pair(
                    VoiceCommandType.APP_DATA_QUERY,
                    VoiceIntentResult.AppDataQuery(AppDataQueryType.BOOKMARKED_COUNT)
                )
            }
            if (clean.contains("important")) {
                return Pair(
                    VoiceCommandType.APP_DATA_QUERY,
                    VoiceIntentResult.AppDataQuery(AppDataQueryType.IMPORTANT_COUNT)
                )
            }
            if (clean.contains("pdf") || clean.contains("pdfs")) {
                return Pair(
                    VoiceCommandType.APP_DATA_QUERY,
                    VoiceIntentResult.AppDataQuery(AppDataQueryType.FORMAT_COUNT, "PDF")
                )
            }
            if (clean.contains("doc") || clean.contains("word")) {
                return Pair(
                    VoiceCommandType.APP_DATA_QUERY,
                    VoiceIntentResult.AppDataQuery(AppDataQueryType.FORMAT_COUNT, "DOCX")
                )
            }
            if (clean.contains("total") || clean.contains("files do i have") || clean.contains("total files")) {
                return Pair(
                    VoiceCommandType.APP_DATA_QUERY,
                    VoiceIntentResult.AppDataQuery(AppDataQueryType.TOTAL_FILES)
                )
            }
        }

        if (clean.contains("show my recently opened files") || clean.contains("recently modified files") ||
            clean.contains("what are my recent files") || clean.contains("files uploaded today")
        ) {
            return Pair(
                VoiceCommandType.APP_DATA_QUERY,
                VoiceIntentResult.AppDataQuery(AppDataQueryType.RECENT_FILES)
            )
        }

        // Format-specific search commands (e.g. "show all pdf files", "search pdf files")
        if ((clean.contains("show all ") || clean.contains("search ") || clean.contains("find ")) &&
            (clean.contains("pdf") || clean.contains("docx") || clean.contains("doc ") || clean.contains("word") || clean.contains("pptx") || clean.contains("ppt "))) {
            val detectedFmt = when {
                clean.contains("pdf") -> "PDF"
                clean.contains("docx") || clean.contains("doc ") || clean.contains("word") -> "DOCX"
                clean.contains("pptx") || clean.contains("ppt ") -> "PPTX"
                else -> null
            }
            val searchQuery = clean.replace(Regex("^(show all|search for|search|find all|find|get)\\s+"), "")
                .replace(Regex("\\b(pdf|docx|doc|word|pptx|ppt|files|file)\\b"), "")
                .trim()
            return Pair(
                VoiceCommandType.FILE_SEARCH_COMMAND,
                VoiceIntentResult.FileSearch(
                    query = if (searchQuery.isBlank()) (detectedFmt ?: "document") else searchQuery,
                    formatFilter = detectedFmt
                )
            )
        }

        // -------------------------------------------------------------
        // 1. NAVIGATION_COMMANDS (Complete voice-controlled screen navigation)
        // -------------------------------------------------------------

        // Command 6: Go Back (Checked first for immediate back navigation)
        val navBackExact = listOf(
            "go back", "navigate back", "back", "previous screen",
            "go to previous screen", "take me back", "return", "previous", "go back please"
        )
        if (navBackExact.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    isBack = true,
                    spokenConfirmation = "Navigating back."
                )
            )
        }

        // Command 1: Go to AI screen -> Open AI Chat Screen
        val navAiExact = listOf(
            "go to ai screen", "navigate to ai screen", "open ai screen", "ai screen",
            "go to ai chat screen", "navigate to ai chat screen", "open ai chat screen",
            "go to ai chat", "navigate to ai chat", "open ai chat", "ai chat",
            "go to ai", "navigate to ai", "open ai", "show ai", "launch ai",
            "chat with ai", "talk to ai", "ask ai", "start ai chat", "ai assistant",
            "ai assistant screen", "chat screen", "open chat", "go to chat", "ai"
        )
        if (navAiExact.any { clean == it || clean.startsWith("$it ") } ||
            (("ai" in words || "chat" in words) && ("screen" in words || "open" in words || clean.startsWith("go to") || clean.startsWith("navigate") || clean.startsWith("show")))) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    targetTab = BottomNavItem.AI,
                    spokenConfirmation = "Opening AI Chat Screen."
                )
            )
        }

        // Command 3: Go to saved screen -> Open Saved Files Screen (Checked BEFORE files so "saved files" goes to Saved)
        val navSavedExact = listOf(
            "go to saved screen", "navigate to saved screen", "open saved screen", "saved screen",
            "go to saved files screen", "navigate to saved files screen", "open saved files screen", "saved files screen",
            "go to saved files", "go to saved", "navigate to saved files", "navigate to saved",
            "open saved files", "open saved", "show saved files", "show saved", "saved files",
            "show bookmarks", "open bookmarks", "my bookmarks", "bookmarked files", "bookmarks screen", "bookmarks", "saved"
        )
        if (navSavedExact.any { clean == it || clean.startsWith("$it ") } ||
            (("saved" in words || "bookmarks" in words || "bookmarked" in words) && ("screen" in words || "open" in words || clean.startsWith("go to") || clean.startsWith("navigate") || clean.startsWith("show") || "files" in words))) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    targetTab = BottomNavItem.SAVED,
                    spokenConfirmation = "Opening Saved Files Screen."
                )
            )
        }

        // Command 2: Go to file screen -> Open Files Screen
        val navFilesExact = listOf(
            "go to file screen", "go to files screen", "navigate to file screen", "navigate to files screen",
            "open file screen", "open files screen", "file screen", "files screen",
            "go to files", "go to file", "navigate to files", "navigate to file",
            "open files", "open file", "open my files", "my files", "show files", "all files",
            "browse files", "file manager", "files",
            "go to documents", "show my documents", "open my documents", "documents", "document screen", "my documents", "documents screen"
        )
        if (navFilesExact.any { clean == it || clean.startsWith("$it ") } ||
            (("file" in words || "files" in words || "documents" in words) && ("screen" in words || "open" in words || clean.startsWith("go to") || clean.startsWith("navigate") || clean.startsWith("show")) && "saved" !in words)) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    targetTab = BottomNavItem.FILES,
                    spokenConfirmation = "Opening Files Screen."
                )
            )
        }

        // Command 4: Go to profile screen -> Open Profile Screen
        val navProfileExact = listOf(
            "go to profile screen", "navigate to profile screen", "open profile screen", "profile screen",
            "go to profile", "navigate to profile", "open profile", "open my profile", "my profile",
            "show profile", "view profile", "user profile", "my account", "account screen", "open account", "profile"
        )
        if (navProfileExact.any { clean == it || clean.startsWith("$it ") } ||
            (("profile" in words || "account" in words) && ("screen" in words || "open" in words || clean.startsWith("go to") || clean.startsWith("navigate") || clean.startsWith("show")))) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    targetTab = BottomNavItem.PROFILE,
                    spokenConfirmation = "Opening Profile Screen."
                )
            )
        }

        // Command 5: Go to home screen -> Open Home Screen
        val navHomeExact = listOf(
            "go to home screen", "navigate to home screen", "open home screen", "home screen",
            "go to home", "navigate to home", "open home", "go home", "home",
            "main screen", "dashboard", "go to dashboard", "open dashboard"
        )
        if (navHomeExact.any { clean == it || clean.startsWith("$it ") } ||
            ("home" in words && ("screen" in words || "open" in words || clean.startsWith("go to") || clean.startsWith("navigate") || clean.startsWith("show")))) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    targetTab = BottomNavItem.HOME,
                    spokenConfirmation = "Opening Home Screen."
                )
            )
        }

        // Overlays: Recent Files
        val navRecentExact = listOf(
            "recent files", "show recent files", "open recent files", "recent files screen", "recently opened"
        )
        if (navRecentExact.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    overlayKey = "recent_files",
                    spokenConfirmation = "Opening Recent Files."
                )
            )
        }

        // Overlays: Notifications
        val navNotifExact = listOf(
            "notifications", "show notifications", "open notifications", "notifications screen"
        )
        if (navNotifExact.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    overlayKey = "notifications",
                    spokenConfirmation = "Opening Notifications."
                )
            )
        }

        // Overlays: Theme Settings
        val navThemeExact = listOf(
            "theme settings", "open settings", "settings", "theme", "dark mode", "light mode", "settings screen"
        )
        if (navThemeExact.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    overlayKey = "theme",
                    spokenConfirmation = "Opening Theme Settings."
                )
            )
        }

        // Overlays: Help / Support
        val navHelpExact = listOf(
            "help", "support", "help and support", "contact support", "help screen"
        )
        if (navHelpExact.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    overlayKey = "help",
                    spokenConfirmation = "Opening Help & Support."
                )
            )
        }

        // Overlays: AI History
        val navHistoryExact = listOf(
            "ai history", "chat history", "search history", "view history"
        )
        if (navHistoryExact.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    overlayKey = "ai_history",
                    spokenConfirmation = "Opening AI Chat History."
                )
            )
        }

        // Unrecognized navigation command attempt
        if (clean.startsWith("go to ") || clean.startsWith("navigate to ") || clean.endsWith(" screen") || clean.startsWith("open screen")) {
            return Pair(
                VoiceCommandType.UNKNOWN_COMMAND,
                VoiceIntentResult.Clarify("Screen not found. You can say 'Go to AI screen', 'Go to file screen', 'Go to saved screen', 'Go to profile screen', 'Go to home screen', or 'Go back'.")
            )
        }

        // -------------------------------------------------------------
        // 2. APP_DATA_QUERY
        // -------------------------------------------------------------
        if (clean.contains("how many total files") || clean.contains("how many files do i have") ||
            clean.contains("total files stored") || clean.contains("total file count") || clean.contains("total files")
        ) {
            return Pair(
                VoiceCommandType.APP_DATA_QUERY,
                VoiceIntentResult.AppDataQuery(AppDataQueryType.TOTAL_FILES)
            )
        }

        if (clean.contains("how many pdf") || clean.contains("how many pdfs") || clean.contains("number of pdf")) {
            return Pair(
                VoiceCommandType.APP_DATA_QUERY,
                VoiceIntentResult.AppDataQuery(AppDataQueryType.FORMAT_COUNT, "PDF")
            )
        }

        if (clean.contains("how many doc") || clean.contains("how many word")) {
            return Pair(
                VoiceCommandType.APP_DATA_QUERY,
                VoiceIntentResult.AppDataQuery(AppDataQueryType.FORMAT_COUNT, "DOCX")
            )
        }

        if (clean.contains("how many important") || clean.contains("important files saved") || clean.contains("important files count")) {
            return Pair(
                VoiceCommandType.APP_DATA_QUERY,
                VoiceIntentResult.AppDataQuery(AppDataQueryType.IMPORTANT_COUNT)
            )
        }

        if (clean.contains("how many bookmark") || clean.contains("bookmarked files do i have") || clean.contains("bookmarked count")) {
            return Pair(
                VoiceCommandType.APP_DATA_QUERY,
                VoiceIntentResult.AppDataQuery(AppDataQueryType.BOOKMARKED_COUNT)
            )
        }

        if (clean.contains("show my recently opened files") || clean.contains("recently modified files") ||
            clean.contains("what are my recent files") || clean.contains("files uploaded today")
        ) {
            return Pair(
                VoiceCommandType.APP_DATA_QUERY,
                VoiceIntentResult.AppDataQuery(AppDataQueryType.RECENT_FILES)
            )
        }

        // -------------------------------------------------------------
        // 3. FILE_ACTION_COMMAND (Open / View specific document)
        // -------------------------------------------------------------
        val openPrefixes = listOf("open my ", "open the ", "open ", "view my ", "view the ", "view ", "read ", "launch ")
        for (prefix in openPrefixes) {
            if (clean.startsWith(prefix)) {
                val targetName = clean.removePrefix(prefix).trim()
                if (targetName.isNotEmpty()) {
                    return Pair(
                        VoiceCommandType.FILE_ACTION_COMMAND,
                        VoiceIntentResult.FileAction(targetFileName = targetName)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 4. FILE_SEARCH_COMMAND (Natural Language & Content Search)
        // -------------------------------------------------------------
        val searchPrefixes = listOf(
            "find files containing the word ",
            "find files containing ",
            "find files with ",
            "find my ",
            "find ",
            "search for my ",
            "search for ",
            "search my ",
            "search ",
            "where is my ",
            "where is the ",
            "where is ",
            "where are my ",
            "where are ",
            "look for ",
            "show all pdf files",
            "show all word files",
            "show all documents"
        )

        var isSearch = false
        for (prefix in searchPrefixes) {
            if (clean.startsWith(prefix)) {
                isSearch = true
                break
            }
        }

        val isVoiceSearch = isSearch ||
                clean.startsWith("find") ||
                clean.startsWith("search") ||
                clean.startsWith("where is") ||
                clean.startsWith("where are") ||
                clean.startsWith("look for") ||
                clean.startsWith("show me") ||
                clean.startsWith("show") ||
                clean.startsWith("locate") ||
                clean.startsWith("get") ||
                clean.startsWith("do i have") ||
                clean.startsWith("is there") ||
                clean.startsWith("i forgot") ||
                clean.startsWith("i don't remember") ||
                clean.startsWith("i dont remember") ||
                clean.startsWith("which file") ||
                clean.startsWith("which document") ||
                clean.contains("files containing") ||
                clean.contains("containing") ||
                clean.contains("contains") ||
                clean.contains("wrote about") ||
                clean.contains("information about") ||
                clean.contains("notes on") ||
                clean.contains("pdf files") ||
                clean.contains("word documents")

        if (isVoiceSearch) {
            val (topic, fmt) = com.simats.intellifile.ui.ai.AiChatIntentDetector.extractSearchTopic(trimmed)
            if (topic.isNotBlank() || fmt != null) {
                return Pair(
                    VoiceCommandType.FILE_SEARCH_COMMAND,
                    VoiceIntentResult.FileSearch(
                        query = topic,
                        formatFilter = fmt
                    )
                )
            }
        }

        // -------------------------------------------------------------
        // 5. DOCUMENT_QUERY (RAG)
        // -------------------------------------------------------------
        val docQueryKeywords = listOf(
            "summarize", "summary", "explain this document", "explain the second chapter",
            "key points", "what does the file say", "compare my documents", "important points in"
        )
        if (docQueryKeywords.any { clean.contains(it) }) {
            return Pair(
                VoiceCommandType.DOCUMENT_QUERY,
                VoiceIntentResult.AiQuery(prompt = trimmed, isDocumentQuery = true)
            )
        }

        // -------------------------------------------------------------
        // 6. GENERAL_AI_QUERY
        // -------------------------------------------------------------
        val generalStarts = listOf(
            "what is ", "what are ", "explain ", "how does ", "why is ", "tell me about ",
            "give me an example", "who is ", "write a ", "how to "
        )
        if (generalStarts.any { clean.startsWith(it) }) {
            return Pair(
                VoiceCommandType.GENERAL_AI_QUERY,
                VoiceIntentResult.AiQuery(prompt = trimmed, isDocumentQuery = false)
            )
        }

        // Default: If it's a long or question-like query, treat as AI query; else search
        return if (clean.contains("?") || clean.split(" ").size >= 4) {
            Pair(
                VoiceCommandType.GENERAL_AI_QUERY,
                VoiceIntentResult.AiQuery(prompt = trimmed, isDocumentQuery = false)
            )
        } else {
            Pair(
                VoiceCommandType.FILE_SEARCH_COMMAND,
                VoiceIntentResult.FileSearch(query = trimmed)
            )
        }
    }
}


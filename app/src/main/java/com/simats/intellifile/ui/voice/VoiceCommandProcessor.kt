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

        val clean = trimmed.lowercase().trimEnd('?', '.', '!', ',')

        // -------------------------------------------------------------
        // 1. NAVIGATION_COMMAND
        // -------------------------------------------------------------

        // Profile
        val navProfilePatterns = listOf(
            "go to profile", "open profile", "open my profile", "my profile",
            "show profile", "view profile", "profile screen"
        )
        if (navProfilePatterns.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    targetTab = BottomNavItem.PROFILE,
                    spokenConfirmation = "Opening your Profile."
                )
            )
        }

        // Files
        val navFilesPatterns = listOf(
            "go to files", "open files", "show my documents", "open my documents",
            "my files", "show files", "all files", "browse files", "files screen"
        )
        if (navFilesPatterns.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    targetTab = BottomNavItem.FILES,
                    spokenConfirmation = "Opening your Files."
                )
            )
        }

        // Saved / Bookmarks
        val navSavedPatterns = listOf(
            "open saved files", "show bookmarks", "open saved", "show saved",
            "show saved files", "saved files", "bookmarked files", "open bookmarks",
            "my bookmarks", "saved screen", "show bookmarked files"
        )
        if (navSavedPatterns.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    targetTab = BottomNavItem.SAVED,
                    spokenConfirmation = "Opening your Saved files."
                )
            )
        }

        // AI Chat
        val navAiPatterns = listOf(
            "open ai chat", "go to ai", "go to ai chat", "open ai",
            "chat with ai", "ai screen", "ai assistant", "start ai chat", "ask ai"
        )
        if (navAiPatterns.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    targetTab = BottomNavItem.AI,
                    spokenConfirmation = "Opening AI Chat."
                )
            )
        }

        // Home
        val navHomePatterns = listOf(
            "go to home", "open home", "home screen", "go home", "main screen", "open home screen"
        )
        if (navHomePatterns.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    targetTab = BottomNavItem.HOME,
                    spokenConfirmation = "Going to Home."
                )
            )
        }

        // Go Back
        val navBackPatterns = listOf("go back", "back", "previous screen", "navigate back")
        if (navBackPatterns.any { clean == it }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    isBack = true,
                    spokenConfirmation = "Going back."
                )
            )
        }

        // Recent Files Overlay
        val navRecentPatterns = listOf("recent files", "show recent files", "open recent files", "recently opened")
        if (navRecentPatterns.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    overlayKey = "recent_files",
                    spokenConfirmation = "Opening Recent Files."
                )
            )
        }

        // Notifications Overlay
        val navNotifPatterns = listOf("notifications", "show notifications", "open notifications")
        if (navNotifPatterns.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    overlayKey = "notifications",
                    spokenConfirmation = "Opening Notifications."
                )
            )
        }

        // Theme / Settings Overlay
        val navThemePatterns = listOf("theme settings", "open settings", "theme", "dark mode", "light mode")
        if (navThemePatterns.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    overlayKey = "theme",
                    spokenConfirmation = "Opening Theme Settings."
                )
            )
        }

        // Help / Support
        val navHelpPatterns = listOf("help", "support", "help and support", "contact support")
        if (navHelpPatterns.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    overlayKey = "help",
                    spokenConfirmation = "Opening Help & Support."
                )
            )
        }

        // AI History
        val navHistoryPatterns = listOf("ai history", "chat history", "search history", "view history")
        if (navHistoryPatterns.any { clean == it || clean.startsWith("$it ") }) {
            return Pair(
                VoiceCommandType.NAVIGATION_COMMAND,
                VoiceIntentResult.Navigation(
                    overlayKey = "ai_history",
                    spokenConfirmation = "Opening AI Chat History."
                )
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


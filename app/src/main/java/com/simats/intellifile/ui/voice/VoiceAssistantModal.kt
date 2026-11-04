package com.simats.intellifile.ui.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.simats.intellifile.R
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.data.repository.AiRepository
import com.simats.intellifile.ui.ai.AiFileResult
import com.simats.intellifile.ui.ai.AiFluidOrbAvatar
import com.simats.intellifile.ui.detail.FileDetailData
import com.simats.intellifile.ui.home.PriorityLevel
import com.simats.intellifile.ui.home.PriorityTag
import com.simats.intellifile.ui.main.BottomNavItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Locale

enum class AssistantVoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantModal(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onNavigateToTab: (BottomNavItem) -> Unit = {},
    onNavigateToOverlay: (String) -> Unit = {},
    onGoBack: () -> Unit = {},
    onOpenInAiChat: (Int?) -> Unit = {},
    onSearchHome: (String) -> Unit = {},
    onOpenFile: (FileDetailData) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val aiRepository = remember { AiRepository(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var voiceState by remember { mutableStateOf(AssistantVoiceState.IDLE) }
    var soundLevelRms by remember { mutableFloatStateOf(0f) }
    var userSpeechText by remember { mutableStateOf("") }
    var manualInputText by remember { mutableStateOf("") }
    var assistantReplyText by remember { mutableStateOf("") }
    var foundDeviceFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var attachedAiFiles by remember { mutableStateOf<List<AiFileResult>>(emptyList()) }
    var currentConversationId by remember { mutableStateOf<Int?>(null) }
    var statusMessage by remember { mutableStateOf("Tap the microphone or type below...") }
    var isMuted by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var hasNavigated by remember { mutableStateOf(false) }

    // TTS Setup
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var textToSpeech: TextToSpeech? = null
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                isTtsReady = true
            }
        }
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                voiceState = AssistantVoiceState.SPEAKING
            }

            override fun onDone(utteranceId: String?) {
                if (voiceState == AssistantVoiceState.SPEAKING) {
                    voiceState = AssistantVoiceState.IDLE
                }
            }

            override fun onError(utteranceId: String?) {
                if (voiceState == AssistantVoiceState.SPEAKING) {
                    voiceState = AssistantVoiceState.IDLE
                }
            }
        })
        ttsInstance = textToSpeech

        onDispose {
            try {
                textToSpeech.stop()
                textToSpeech.shutdown()
            } catch (_: Exception) {}
        }
    }

    fun cleanTextForSpeech(raw: String): String {
        val clean = raw.replace(Regex("[*#_`~>•]"), "")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        // If response is very long, speak the first 2-3 sentences to keep voice interaction natural
        val sentences = clean.split(Regex("(?<=[.!?])\\s+"))
        return if (sentences.size > 3) {
            sentences.take(3).joinToString(" ")
        } else {
            clean
        }
    }

    fun speakAnswer(text: String) {
        if (isMuted || !isTtsReady || text.isBlank()) return
        ttsInstance?.let { tts ->
            tts.stop()
            val clean = cleanTextForSpeech(text)
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_INTELLIFILE_AI")
            voiceState = AssistantVoiceState.SPEAKING
        }
    }

    fun stopSpeaking() {
        ttsInstance?.stop()
        if (voiceState == AssistantVoiceState.SPEAKING) {
            voiceState = AssistantVoiceState.IDLE
        }
    }

    // SpeechRecognizer Setup
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to IntelliFile AI...")
        }
    }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognized = matches?.firstOrNull()?.trim()
            if (!recognized.isNullOrBlank()) {
                userSpeechText = recognized
                // Process through VoiceCommandProcessor
            } else {
                voiceState = AssistantVoiceState.IDLE
            }
        } else {
            voiceState = AssistantVoiceState.IDLE
        }
    }

    fun executeCommand(rawSpokenQuery: String) {
        val query = rawSpokenQuery.trim()
        if (query.isBlank() || isBusy) return

        stopSpeaking()
        isBusy = true
        userSpeechText = query
        voiceState = AssistantVoiceState.PROCESSING
        statusMessage = "Processing command..."

        coroutineScope.launch {
            try {
                val (commandType, intentResult) = VoiceCommandProcessor.processCommand(query)

                when (intentResult) {
                    // -------------------------------------------------------------
                    // 1. NAVIGATION_COMMAND
                    // -------------------------------------------------------------
                    is VoiceIntentResult.Navigation -> {
                        if (hasNavigated) return@launch
                        hasNavigated = true
                        assistantReplyText = intentResult.spokenConfirmation
                        statusMessage = "Navigating..."
                        try {
                            android.widget.Toast.makeText(context, intentResult.spokenConfirmation, android.widget.Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {}
                        stopSpeaking()
                        onDismiss()

                        when {
                            intentResult.isBack -> onGoBack()
                            intentResult.targetTab != null -> onNavigateToTab(intentResult.targetTab)
                            intentResult.overlayKey != null -> onNavigateToOverlay(intentResult.overlayKey)
                        }
                    }

                    // -------------------------------------------------------------
                    // 2. FILE_ACTION_COMMAND (Open / View specific file)
                    // -------------------------------------------------------------
                    is VoiceIntentResult.FileAction -> {
                        val allFiles = LocalFileManager.getDeviceFiles(context)
                        val term = intentResult.targetFileName.lowercase()

                        val matches = allFiles.filter {
                            it.title.lowercase().contains(term) ||
                                    it.title.substringBeforeLast('.').lowercase().contains(term)
                        }

                        if (matches.size == 1) {
                            val targetFile = matches[0]
                            val confirm = "Opening ${targetFile.title} for you."
                            assistantReplyText = confirm
                            statusMessage = "Opening file..."
                            speakAnswer(confirm)
                            delay(700L)
                            stopSpeaking()
                            onDismiss()
                            onOpenFile(mapLocalToFileDetail(targetFile))
                        } else if (matches.size > 1) {
                            foundDeviceFiles = matches
                            val reply = "I found ${matches.size} files matching '${intentResult.targetFileName}'. Tap one to open."
                            assistantReplyText = reply
                            statusMessage = "Multiple files found"
                            voiceState = AssistantVoiceState.SPEAKING
                            speakAnswer(reply)
                        } else {
                            foundDeviceFiles = emptyList()
                            val notFound = "I could not find any file named '${intentResult.targetFileName}' on your device."
                            assistantReplyText = notFound
                            statusMessage = "File not found"
                            voiceState = AssistantVoiceState.IDLE
                            speakAnswer(notFound)
                        }
                    }

                    // -------------------------------------------------------------
                    // 3. FILE_SEARCH_COMMAND
                    // -------------------------------------------------------------
                    is VoiceIntentResult.FileSearch -> {
                        val term = intentResult.query.lowercase()
                        val fmt = intentResult.formatFilter

                        // 1. Search local device files in parallel
                        val localDeferred = async(Dispatchers.IO) {
                            try {
                                val allFiles = LocalFileManager.getDeviceFiles(context)
                                val searchTokens = term.split(Regex("[\\s_\\-,.]+"))
                                    .filter { it.length >= 2 && !setOf("the", "my", "all", "file", "files", "document", "documents", "notes", "for", "with", "about").contains(it) }

                                allFiles.filter { file ->
                                    val matchFormat = fmt == null || file.format.equals(fmt, ignoreCase = true)
                                    if (!matchFormat) return@filter false
                                    if (term.isBlank()) return@filter true

                                    val titleLower = file.title.lowercase()
                                    val pathLower = file.path.lowercase()
                                    val baseName = file.title.substringBeforeLast('.').lowercase()

                                    if (titleLower.contains(term) || baseName.contains(term) || pathLower.contains(term)) {
                                        return@filter true
                                    }

                                    if (searchTokens.isNotEmpty()) {
                                        val overlap = searchTokens.count { titleLower.contains(it) || pathLower.contains(it) }
                                        if (overlap == searchTokens.size || (searchTokens.size >= 2 && overlap >= searchTokens.size - 1)) {
                                            return@filter true
                                        }
                                    }

                                    if (file.sizeBytes in 1..500_000L && setOf("TXT", "CSV", "JSON", "MD").contains(file.format.uppercase())) {
                                        try {
                                            val content = LocalFileManager.readTextContent(context, file.uri.toString(), file.path, file.format, 10000)
                                            if (content.contains(term, ignoreCase = true)) return@filter true
                                            if (searchTokens.isNotEmpty() && searchTokens.all { content.contains(it, ignoreCase = true) }) return@filter true
                                        } catch (_: Exception) {}
                                    }

                                    false
                                }
                            } catch (_: Exception) {
                                emptyList<LocalFileItem>()
                            }
                        }

                        // 2. Search AI Vault via backend API in parallel
                        val vaultDeferred = async(Dispatchers.IO) {
                            try {
                                aiRepository.sendChatMessage(
                                    message = query,
                                    conversationId = currentConversationId
                                )
                            } catch (_: Exception) {
                                null
                            }
                        }

                        val filteredLocal = localDeferred.await()
                        val vaultResult = vaultDeferred.await()

                        val vaultFiles = mutableListOf<AiFileResult>()
                        vaultResult?.onSuccess { response ->
                            currentConversationId = response.conversation_id
                            val rawFiles = response.message?.files
                                ?: response.message?.related_files
                                ?: response.related_files
                                ?: emptyList()

                            val mapped = rawFiles.map { rf ->
                                val prio = when (rf.priority?.uppercase()) {
                                    "IMPORTANT" -> PriorityLevel.IMPORTANT
                                    "LOW" -> PriorityLevel.LOW
                                    "NORMAL" -> PriorityLevel.NORMAL
                                    else -> null
                                }
                                val icon = when (rf.format?.uppercase()) {
                                    "DOCX", "DOC" -> R.drawable.doc
                                    "PPTX", "PPT" -> R.drawable.ppt
                                    else -> R.drawable.pdf
                                }
                                AiFileResult(
                                    id = rf.id,
                                    title = rf.title,
                                    format = rf.format ?: "PDF",
                                    size = rf.size ?: "1.0 MB",
                                    dateText = rf.dateText ?: "Recent",
                                    priority = prio,
                                    iconResId = icon,
                                    page = rf.page,
                                    snippet = rf.snippet,
                                    source = rf.source ?: "AI Vault",
                                    uriString = rf.uri_string,
                                    filePath = rf.file_path,
                                    fileUrl = rf.file_url,
                                    category = rf.category
                                )
                            }
                            vaultFiles.addAll(mapped)
                        }

                        val dedupedVaultFiles = vaultFiles.filter { vf ->
                            filteredLocal.none { it.title.equals(vf.title, ignoreCase = true) }
                        }

                        foundDeviceFiles = filteredLocal
                        attachedAiFiles = dedupedVaultFiles
                        val totalCount = filteredLocal.size + dedupedVaultFiles.size

                        if (totalCount > 0) {
                            val reply = if (fmt != null && term.isNotBlank()) {
                                "Found $totalCount $fmt file(s) matching '$term' across Device Files and AI Vault."
                            } else if (fmt != null) {
                                "Found $totalCount $fmt file(s) across Device Files and AI Vault."
                            } else {
                                "I found $totalCount file(s) matching '$term' across Device Files and AI Vault."
                            }
                            assistantReplyText = reply
                            statusMessage = "Search results"
                            voiceState = AssistantVoiceState.SPEAKING
                            speakAnswer(reply)

                            if (term.isNotBlank()) {
                                onSearchHome(term)
                            }
                        } else {
                            val noMatch = "No matching files found for '${intentResult.query}'. Try another filename or keyword."
                            assistantReplyText = noMatch
                            statusMessage = "No files found"
                            voiceState = AssistantVoiceState.IDLE
                            speakAnswer(noMatch)
                        }
                    }

                    // -------------------------------------------------------------
                    // 4. APP_DATA_QUERY (Live File Statistics & Counts)
                    // -------------------------------------------------------------
                    is VoiceIntentResult.AppDataQuery -> {
                        val allFiles = LocalFileManager.getDeviceFiles(context)
                        val (reply, filesToShow) = when (intentResult.queryType) {
                            AppDataQueryType.TOTAL_FILES -> {
                                val count = allFiles.size
                                Pair("You currently have a total of $count file" + (if (count != 1) "s" else "") + " stored on your device.", allFiles.take(4))
                            }
                            AppDataQueryType.FORMAT_COUNT -> {
                                val fmt = intentResult.format ?: "PDF"
                                val count = allFiles.count { it.format.equals(fmt, ignoreCase = true) }
                                val matching = allFiles.filter { it.format.equals(fmt, ignoreCase = true) }
                                Pair("You have $count $fmt file" + (if (count != 1) "s" else "") + " available.", matching.take(4))
                            }
                            AppDataQueryType.IMPORTANT_COUNT -> {
                                val count = allFiles.count { it.priority == PriorityLevel.IMPORTANT }
                                val matching = allFiles.filter { it.priority == PriorityLevel.IMPORTANT }
                                Pair("You have $count file" + (if (count != 1) "s" else "") + " marked as important.", matching.take(4))
                            }
                            AppDataQueryType.BOOKMARKED_COUNT -> {
                                val count = allFiles.count { it.isBookmarked }
                                val matching = allFiles.filter { it.isBookmarked }
                                Pair("You have $count bookmarked file" + (if (count != 1) "s" else "") + ".", matching.take(4))
                            }
                            AppDataQueryType.RECENT_FILES -> {
                                val count = allFiles.size
                                val recent = allFiles.take(5)
                                Pair("Here are your ${recent.size} most recently updated files.", recent)
                            }
                        }

                        foundDeviceFiles = filesToShow
                        assistantReplyText = reply
                        statusMessage = "Information retrieved"
                        voiceState = AssistantVoiceState.SPEAKING
                        speakAnswer(reply)
                    }

                    // -------------------------------------------------------------
                    // 5. GENERAL_AI_QUERY & DOCUMENT_QUERY (SLM & RAG)
                    // -------------------------------------------------------------
                    is VoiceIntentResult.AiQuery -> {
                        foundDeviceFiles = emptyList()
                        val result = aiRepository.sendChatMessage(
                            message = intentResult.prompt,
                            conversationId = currentConversationId
                        )

                        result.onSuccess { response ->
                            currentConversationId = response.conversation_id
                            val reply = response.message?.text ?: response.reply ?: "I analyzed your files."
                            assistantReplyText = reply

                            val rawFiles = response.message?.files
                                ?: response.message?.related_files
                                ?: response.related_files
                                ?: emptyList()

                            attachedAiFiles = rawFiles.map { rf ->
                                val prio = when (rf.priority?.uppercase()) {
                                    "IMPORTANT" -> PriorityLevel.IMPORTANT
                                    "NORMAL" -> PriorityLevel.NORMAL
                                    "LOW" -> PriorityLevel.LOW
                                    else -> null
                                }
                                val icon = when (rf.format?.uppercase()) {
                                    "DOCX", "DOC" -> R.drawable.doc
                                    else -> R.drawable.pdf
                                }
                                AiFileResult(
                                    id = rf.id,
                                    title = rf.title,
                                    format = rf.format ?: "PDF",
                                    size = rf.size ?: "1.0 MB",
                                    dateText = rf.dateText ?: "Recent",
                                    priority = prio,
                                    iconResId = icon,
                                    page = rf.page,
                                    snippet = rf.snippet
                                )
                            }

                            statusMessage = if (intentResult.isDocumentQuery) "Document analysis complete" else "AI Answer"
                            voiceState = AssistantVoiceState.SPEAKING
                            speakAnswer(reply)
                        }.onFailure { err ->
                            voiceState = AssistantVoiceState.ERROR
                            val msg = "I could not connect to the AI service right now. Please ensure the backend is running."
                            assistantReplyText = msg
                            statusMessage = "Service unavailable"
                            speakAnswer(msg)
                        }
                    }

                    // -------------------------------------------------------------
                    // 6. UNKNOWN_COMMAND / Clarify
                    // -------------------------------------------------------------
                    is VoiceIntentResult.Clarify -> {
                        assistantReplyText = intentResult.message
                        statusMessage = "Could not understand"
                        voiceState = AssistantVoiceState.IDLE
                        speakAnswer(intentResult.message)
                    }
                }
            } catch (e: Exception) {
                voiceState = AssistantVoiceState.ERROR
                assistantReplyText = "An unexpected error occurred while processing your request. Please try again."
                statusMessage = "Error occurred"
            } finally {
                isBusy = false
            }
        }
    }

    fun startListening() {
        stopSpeaking()
        if (!hasAudioPermission || isBusy) return

        if (speechRecognizer != null) {
            try {
                speechRecognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        voiceState = AssistantVoiceState.LISTENING
                        statusMessage = "Listening... Speak now"
                    }

                    override fun onBeginningOfSpeech() {
                        statusMessage = "Listening to your voice..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        soundLevelRms = rmsdB
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        voiceState = AssistantVoiceState.PROCESSING
                        statusMessage = "Processing your voice..."
                    }

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording issue"
                            SpeechRecognizer.ERROR_CLIENT -> "Client speech error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network issue with voice recognition"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Tap mic to retry."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                            SpeechRecognizer.ERROR_SERVER -> "Recognition service error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap mic to speak."
                            else -> "Tap the microphone to speak"
                        }
                        statusMessage = message
                        voiceState = AssistantVoiceState.IDLE
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            // Find any navigation command match first
                            val navMatch = matches.firstOrNull { candidate ->
                                val (cType, _) = VoiceCommandProcessor.processCommand(candidate)
                                cType == VoiceCommandType.NAVIGATION_COMMAND
                            }
                            val textToRun = navMatch ?: matches.first().trim()
                            if (textToRun.isNotBlank()) {
                                executeCommand(textToRun)
                            } else {
                                voiceState = AssistantVoiceState.IDLE
                                statusMessage = "Didn't catch that. Tap mic or type below."
                            }
                        } else {
                            voiceState = AssistantVoiceState.IDLE
                            statusMessage = "Didn't catch that. Tap mic or type below."
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = partialMatches?.firstOrNull()?.trim()
                        if (!partial.isNullOrBlank()) {
                            userSpeechText = partial
                            // If a complete navigation command is detected in partial stream, navigate immediately
                            val (cType, _) = VoiceCommandProcessor.processCommand(partial)
                            if (cType == VoiceCommandType.NAVIGATION_COMMAND && !isBusy && !hasNavigated) {
                                try {
                                    speechRecognizer?.stopListening()
                                } catch (_: Exception) {}
                                executeCommand(partial)
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer.startListening(speechIntent)
                voiceState = AssistantVoiceState.LISTENING
                statusMessage = "Listening... Speak now"
            } catch (e: Exception) {
                try {
                    systemSpeechLauncher.launch(speechIntent)
                } catch (_: Exception) {
                    voiceState = AssistantVoiceState.ERROR
                    statusMessage = "Speech recognition unavailable on this device."
                }
            }
        } else {
            try {
                systemSpeechLauncher.launch(speechIntent)
            } catch (e: Exception) {
                voiceState = AssistantVoiceState.ERROR
                statusMessage = "Speech recognition unavailable on this device."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            startListening()
        } else {
            statusMessage = "Microphone permission is required."
        }
    }

    // Auto-start listening on first open if permission is granted
    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission && voiceState == AssistantVoiceState.IDLE && userSpeechText.isEmpty()) {
            delay(300L)
            startListening()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            stopSpeaking()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(id = R.drawable.voice_amoeba_orb), contentDescription = "Liquid AI", modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "IntelliFile Voice Assistant",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Navigation · File Search · AI Chat",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            if (isMuted) stopSpeaking()
                        }
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = if (isMuted) Color.Gray else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            stopSpeaking()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content Area
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Permission Card
                if (!hasAudioPermission) {
                    item {
                        PermissionRequiredCard(
                            onRequestPermission = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                    }
                } else {
                    // Central Voice Visualizer Orb
                    item {
                        VoiceWaveOrb(
                            state = voiceState,
                            soundRms = soundLevelRms,
                            onClick = {
                                if (voiceState == AssistantVoiceState.LISTENING) {
                                    speechRecognizer?.stopListening()
                                } else {
                                    startListening()
                                }
                            }
                        )
                    }

                    // Status Indicator with Smooth Animated Content
                    item {
                        AnimatedContent(
                            targetState = statusMessage,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(260)) + slideInVertically(animationSpec = tween(260)) { -it / 3 })
                                    .togetherWith(fadeOut(animationSpec = tween(180)) + slideOutVertically(animationSpec = tween(180)) { it / 3 })
                            },
                            label = "statusTransition"
                        ) { msg ->
                            Text(
                                text = msg,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = when (voiceState) {
                                    AssistantVoiceState.LISTENING -> MaterialTheme.colorScheme.primary
                                    AssistantVoiceState.PROCESSING -> Color(0xFFE6A700)
                                    AssistantVoiceState.SPEAKING -> Color(0xFF00C2A8)
                                    AssistantVoiceState.ERROR -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // User Spoken Transcript with Smooth Entry
                    if (userSpeechText.isNotBlank()) {
                        item {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(300)) + expandVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                            ) {
                                UserSpeechCard(text = userSpeechText)
                            }
                        }
                    }

                    // Assistant Answer Card with Smooth Entry
                    if (assistantReplyText.isNotBlank()) {
                        item {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(320)) + expandVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                            ) {
                                AssistantAnswerCard(
                                    replyText = assistantReplyText,
                                    isSpeaking = voiceState == AssistantVoiceState.SPEAKING,
                                    onReplaySpeech = { speakAnswer(assistantReplyText) },
                                    onStopSpeech = { stopSpeaking() }
                                )
                            }
                        }
                    }

                    // Local Device Matching Files
                    if (foundDeviceFiles.isNotEmpty()) {
                        item {
                            Text(
                                text = "Found Files (${foundDeviceFiles.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }

                        items(foundDeviceFiles) { item ->
                            LocalDeviceFileResultCard(
                                item = item,
                                onOpen = {
                                    stopSpeaking()
                                    onDismiss()
                                    onOpenFile(mapLocalToFileDetail(item))
                                }
                            )
                        }
                    }

                    // AI Grounded Document References
                    if (attachedAiFiles.isNotEmpty()) {
                        item {
                            Text(
                                text = "AI Vault Files (${attachedAiFiles.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }

                        items(attachedAiFiles) { file ->
                            VoiceAssistantFileCard(
                                file = file,
                                onOpen = {
                                    stopSpeaking()
                                    val localMatch = LocalFileManager.getDeviceFiles(context).firstOrNull {
                                        it.title.equals(file.title, ignoreCase = true) ||
                                                it.title.equals(file.title.substringBeforeLast('.'), ignoreCase = true) ||
                                                (!file.uriString.isNullOrBlank() && it.uri.toString() == file.uriString) ||
                                                (!file.filePath.isNullOrBlank() && it.path == file.filePath)
                                    }
                                    val finalUri = if (!file.uriString.isNullOrBlank()) file.uriString else (localMatch?.uri?.toString() ?: "")
                                    val finalPath = if (!file.filePath.isNullOrBlank()) file.filePath else (localMatch?.path ?: "")

                                    onDismiss()
                                    onOpenFile(
                                        FileDetailData(
                                            fileName = file.title,
                                            fileSize = file.size,
                                            fileType = "${file.format} Document",
                                            createdDate = file.dateText,
                                            modifiedDate = file.dateText,
                                            priority = file.priority,
                                            iconResId = file.iconResId,
                                            uriString = finalUri,
                                            path = finalPath
                                        )
                                    )
                                }
                            )
                        }
                    }

                    // Quick Suggested Voice Actions (when IDLE)
                    if (userSpeechText.isBlank() && voiceState == AssistantVoiceState.IDLE) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Suggested voice commands:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val samplePrompts = listOf(
                                    "Go to AI screen",
                                    "Go to file screen",
                                    "Go to saved screen",
                                    "Go to profile screen",
                                    "Go to home screen",
                                    "Go back",
                                    "How many total files do I have?",
                                    "Find my assignment PDF"
                                )

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(samplePrompts) { prompt ->
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.clickable {
                                                userSpeechText = prompt
                                                executeCommand(prompt)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = prompt,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Manual Text Input Fallback Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (manualInputText.isEmpty()) {
                        Text(
                            text = "Type command or question...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = manualInputText,
                        onValueChange = { manualInputText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (manualInputText.isNotBlank()) {
                            val txt = manualInputText.trim()
                            manualInputText = ""
                            executeCommand(txt)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Command",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentConversationId != null || assistantReplyText.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            stopSpeaking()
                            onOpenInAiChat(currentConversationId)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Chat",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Open in AI Chat", fontSize = 13.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Primary Mic Action Button with Pulsing Halo
                val bottomMicTransition = rememberInfiniteTransition(label = "bottomMicPulse")
                val bottomMicHaloScale by bottomMicTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.28f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bottomMicHaloScale"
                )
                val bottomMicHaloAlpha by bottomMicTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 0.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bottomMicHaloAlpha"
                )

                Box(
                    contentAlignment = Alignment.Center
                ) {
                    if (voiceState == AssistantVoiceState.LISTENING) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer(
                                    scaleX = bottomMicHaloScale,
                                    scaleY = bottomMicHaloScale,
                                    alpha = bottomMicHaloAlpha
                                )
                                .clip(CircleShape)
                                .background(Color(0xFF26E07F))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = if (voiceState == AssistantVoiceState.LISTENING) {
                                        listOf(Color(0xFF26E07F), Color(0xFF00C2A8))
                                    } else {
                                        listOf(MaterialTheme.colorScheme.primary, Color(0xFF00C2A8))
                                    }
                                )
                            )
                            .clickable {
                                if (!hasAudioPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else if (voiceState == AssistantVoiceState.LISTENING) {
                                    speechRecognizer?.stopListening()
                                } else {
                                    startListening()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (voiceState == AssistantVoiceState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Microphone",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun mapLocalToFileDetail(item: LocalFileItem): FileDetailData {
    return FileDetailData(
        fileName = item.title,
        fileSize = item.sizeFormatted,
        fileType = item.format,
        createdDate = item.dateText,
        modifiedDate = item.dateText,
        priority = item.priority,
        iconResId = item.iconResId,
        uriString = item.uri.toString(),
        path = item.path
    )
}

@Composable
fun LocalDeviceFileResultCard(
    item: LocalFileItem,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = item.iconResId),
            contentDescription = "File Icon",
            modifier = Modifier.size(36.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF26E07F).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Device Files",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1B8A4E),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${item.format} • ${item.sizeFormatted} • ${item.dateText}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onOpen,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(text = "Open", fontSize = 12.sp, color = Color.White)
        }
    }
}

@Composable
fun VoiceWaveOrb(
    state: AssistantVoiceState,
    soundRms: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voiceWaveAnims")

    // Gentle hovering levitation
    val hoverOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hoverOffset"
    )

    // Smooth continuous wave phase for sinusoidal ripples
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Concentric acoustic wave ripple scale & fade
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleScale"
    )
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha"
    )

    // Radiant specular shimmer sweep across the orb glass surface
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    // Processing neural rotation
    val processingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing)
        ),
        label = "processingRotation"
    )

    // Dynamic voice amplitude surge from live mic input RMS
    val voiceAudioSurge = if (state == AssistantVoiceState.LISTENING) {
        (soundRms.coerceIn(0f, 10f) / 10f) * 0.32f
    } else if (state == AssistantVoiceState.SPEAKING) {
        0.20f
    } else {
        0f
    }

    val animatedVoiceSurge by animateFloatAsState(
        targetValue = voiceAudioSurge,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "animatedVoiceSurge"
    )

    val targetAuraColor = when (state) {
        AssistantVoiceState.LISTENING -> Color(0xFF26E07F)
        AssistantVoiceState.PROCESSING -> Color(0xFF00E5FF)
        AssistantVoiceState.SPEAKING -> Color(0xFF00C2A8)
        AssistantVoiceState.ERROR -> Color(0xFFEF4444)
        else -> Color(0xFF55A9AF)
    }
    val auraColor by animateColorAsState(
        targetValue = targetAuraColor,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "auraColor"
    )

    val targetOrbScale = when (state) {
        AssistantVoiceState.LISTENING -> 1.05f
        AssistantVoiceState.SPEAKING -> 1.03f
        AssistantVoiceState.PROCESSING -> 0.97f
        else -> 1.0f
    }
    val orbScale by animateFloatAsState(
        targetValue = targetOrbScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "orbScale"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Main Interactive Voice Visualizer (compact 130.dp size with smooth scale)
        Box(
            modifier = Modifier
                .size(130.dp)
                .graphicsLayer(scaleX = orbScale, scaleY = orbScale)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // 1. Acoustic Ripple Glow Pulse (active when Listening or Speaking)
            if (state == AssistantVoiceState.LISTENING || state == AssistantVoiceState.SPEAKING) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = rippleScale + animatedVoiceSurge * 0.5f,
                            scaleY = rippleScale + animatedVoiceSurge * 0.5f,
                            alpha = rippleAlpha
                        )
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                auraColor.copy(alpha = 0.45f),
                                Color(0xFF00E5FF).copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
                }
            }

            // 2. Concentric Sinusoidal Harmonic Wave Rings (Wavey Effect)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        translationY = hoverOffset,
                        rotationZ = if (state == AssistantVoiceState.PROCESSING) processingRotation else 0f
                    )
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val baseRadius = size.width * 0.39f

                // Outer Sinusoidal Wave Ring
                val outerPath = Path()
                val numPoints = 72
                for (i in 0..numPoints) {
                    val theta = (i.toFloat() / numPoints) * 2f * PI.toFloat()
                    val wavePerturbation = sin(5f * theta + wavePhase) * (2.5f + animatedVoiceSurge * 10f)
                    val r = (baseRadius * 1.15f + wavePerturbation + animatedVoiceSurge * 12f)
                    val px = cx + r * cos(theta)
                    val py = cy + r * sin(theta)
                    if (i == 0) outerPath.moveTo(px, py) else outerPath.lineTo(px, py)
                }
                outerPath.close()

                drawPath(
                    path = outerPath,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = if (state == AssistantVoiceState.LISTENING) 0.65f else 0.35f),
                            auraColor.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = size.width * 0.5f
                    ),
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )

                // Inner Counter-Undulating Harmonic Wave Ring
                val innerPath = Path()
                for (i in 0..numPoints) {
                    val theta = (i.toFloat() / numPoints) * 2f * PI.toFloat()
                    val wavePerturbation = sin(6f * theta - wavePhase * 1.2f) * (2f + animatedVoiceSurge * 7f)
                    val r = (baseRadius * 1.05f + wavePerturbation + animatedVoiceSurge * 6f)
                    val px = cx + r * cos(theta)
                    val py = cy + r * sin(theta)
                    if (i == 0) innerPath.moveTo(px, py) else innerPath.lineTo(px, py)
                }
                innerPath.close()

                drawPath(
                    path = innerPath,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            auraColor.copy(alpha = if (state == AssistantVoiceState.LISTENING) 0.75f else 0.40f),
                            Color(0xFF00C2A8).copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = size.width * 0.46f
                    ),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 3. Compact 3D Liquid AI Energy Orb (Perfect Sphere, 100.dp - no ameba morphing)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer(
                        translationY = hoverOffset,
                        scaleX = 1f + animatedVoiceSurge * 0.15f,
                        scaleY = 1f + animatedVoiceSurge * 0.15f,
                        rotationZ = if (state == AssistantVoiceState.PROCESSING) processingRotation else 0f
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.voice_amoeba_orb),
                    contentDescription = "Liquid AI Voice Assistant",
                    modifier = Modifier.fillMaxSize()
                )

                // Dynamic Specular Shimmer Light Sweep Layer
                if (state == AssistantVoiceState.LISTENING || state == AssistantVoiceState.SPEAKING) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize(0.92f)
                            .clip(CircleShape)
                    ) {
                        val shimmerBandWidth = size.width * 0.65f
                        val startX = (size.width + shimmerBandWidth) * shimmerOffset - shimmerBandWidth
                        val startY = 0f
                        val endX = startX + shimmerBandWidth
                        val endY = size.height

                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF00E5FF).copy(alpha = 0.15f),
                                    Color.White.copy(alpha = 0.55f),
                                    Color(0xFF26E07F).copy(alpha = 0.30f),
                                    Color.Transparent
                                ),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY)
                            )
                        )
                    }
                }
            }
        }

        // 4. Fluid Sine-Wave Audio Ribbon Visualizer (Wavey Effect)
        FluidVoiceWaveform(
            state = state,
            soundRms = soundRms,
            auraColor = auraColor,
            wavePhase = wavePhase
        )
    }
}

@Composable
fun FluidVoiceWaveform(
    state: AssistantVoiceState,
    soundRms: Float,
    auraColor: Color,
    wavePhase: Float,
    modifier: Modifier = Modifier
) {
    // Dynamic amplitude based on voice state and mic volume
    val voiceSurge = when (state) {
        AssistantVoiceState.LISTENING -> (0.28f + (soundRms.coerceIn(0f, 10f) / 10f) * 0.72f)
        AssistantVoiceState.SPEAKING -> 0.75f
        AssistantVoiceState.PROCESSING -> 0.45f
        else -> 0.18f
    }

    val animatedAmp by animateFloatAsState(
        targetValue = voiceSurge,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "animatedAmp"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth(0.65f)
            .height(28.dp)
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val maxWaveAmp = (height * 0.42f) * animatedAmp

        // 3 Layered Fluid Sine Waves with smooth tapering envelope
        val waves = listOf(
            Triple(2.0f, wavePhase, auraColor.copy(alpha = 0.85f)),
            Triple(3.2f, -wavePhase * 0.9f + 1.2f, Color(0xFF00E5FF).copy(alpha = 0.60f)),
            Triple(1.6f, wavePhase * 1.3f + 2.4f, Color(0xFF00C2A8).copy(alpha = 0.40f))
        )

        val steps = 60
        for ((freq, phase, color) in waves) {
            val path = Path()
            for (i in 0..steps) {
                val progress = i.toFloat() / steps
                val x = progress * width
                // Windowing envelope so wave tapers smoothly to 0 at edges
                val envelope = sin(progress * PI.toFloat()).coerceAtLeast(0f)
                val y = midY + sin(progress * 2f * PI.toFloat() * freq + phase) * maxWaveAmp * envelope
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun UserSpeechCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.FormatQuote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AssistantAnswerCard(
    replyText: String,
    isSpeaking: Boolean,
    onReplaySpeech: () -> Unit,
    onStopSpeech: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                RoundedCornerShape(18.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "IntelliFile Answer",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = {
                        if (isSpeaking) onStopSpeech() else onReplaySpeech()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isSpeaking) "Stop Speaking" else "Speak Again",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = replyText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun VoiceAssistantFileCard(
    file: AiFileResult,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = file.iconResId),
            contentDescription = "File Icon",
            modifier = Modifier.size(36.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (file.source == "Device Files") Color(0xFF26E07F).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = file.source,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (file.source == "Device Files") Color(0xFF1B8A4E) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${file.format} • ${file.size}" + (if (file.page != null) " • Page ${file.page}" else ""),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onOpen,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(text = "Open", fontSize = 12.sp, color = Color.White)
        }
    }
}

@Composable
fun PermissionRequiredCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Microphone",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Microphone Access Required",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "IntelliFile needs microphone permission to listen to your voice commands, search documents, and give spoken answers.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(text = "Grant Microphone Access", color = Color.White)
            }
        }
    }
}

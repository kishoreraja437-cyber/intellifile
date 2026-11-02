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
import androidx.compose.animation.core.*
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
                        assistantReplyText = intentResult.spokenConfirmation
                        statusMessage = "Navigating..."
                        speakAnswer(intentResult.spokenConfirmation)
                        delay(650L)
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
                        val allFiles = LocalFileManager.getDeviceFiles(context)
                        val term = intentResult.query.lowercase()
                        val fmt = intentResult.formatFilter

                        val filtered = allFiles.filter { file ->
                            val matchName = term.isBlank() || file.title.lowercase().contains(term)
                            val matchFormat = fmt == null || file.format.equals(fmt, ignoreCase = true)
                            matchName && matchFormat
                        }

                        foundDeviceFiles = filtered
                        if (filtered.isNotEmpty()) {
                            val reply = if (fmt != null && term.isNotBlank()) {
                                "Found ${filtered.size} $fmt file(s) matching '$term'."
                            } else if (fmt != null) {
                                "You have ${filtered.size} $fmt file(s)."
                            } else {
                                "I found ${filtered.size} file(s) matching '$term'."
                            }
                            assistantReplyText = reply
                            statusMessage = "Search results"
                            voiceState = AssistantVoiceState.SPEAKING
                            speakAnswer(reply)

                            // Also sync home screen search query if term is present
                            if (term.isNotBlank()) {
                                onSearchHome(term)
                            }
                        } else {
                            val noMatch = "No files found matching '${intentResult.query}'."
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
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            executeCommand(text)
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
                    AiFluidOrbAvatar(size = 36.dp, iconSize = 18.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "IntelliFile Voice Assistant",
                            fontSize = 17.sp,
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

            Spacer(modifier = Modifier.height(12.dp))

            // Main Content Area
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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

                    // Status Indicator
                    item {
                        Text(
                            text = statusMessage,
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

                    // User Spoken Transcript
                    if (userSpeechText.isNotBlank()) {
                        item {
                            UserSpeechCard(text = userSpeechText)
                        }
                    }

                    // Assistant Answer Card
                    if (assistantReplyText.isNotBlank()) {
                        item {
                            AssistantAnswerCard(
                                replyText = assistantReplyText,
                                isSpeaking = voiceState == AssistantVoiceState.SPEAKING,
                                onReplaySpeech = { speakAnswer(assistantReplyText) },
                                onStopSpeech = { stopSpeaking() }
                            )
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
                                text = "Referenced Documents (${attachedAiFiles.size})",
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
                                                it.title.equals(file.title.substringBeforeLast('.'), ignoreCase = true)
                                    }
                                    onDismiss()
                                    onOpenFile(
                                        FileDetailData(
                                            fileName = file.title,
                                            fileSize = file.size,
                                            fileType = file.format,
                                            createdDate = file.dateText,
                                            modifiedDate = file.dateText,
                                            priority = file.priority,
                                            iconResId = file.iconResId,
                                            uriString = localMatch?.uri?.toString() ?: "",
                                            path = localMatch?.path ?: ""
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
                                    "Go to Profile",
                                    "Show my Documents",
                                    "Open Saved Files",
                                    "How many total files do I have?",
                                    "Find my assignment PDF",
                                    "What is Artificial Intelligence?"
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

            Spacer(modifier = Modifier.height(14.dp))

            // Manual Text Input Fallback Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
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

            Spacer(modifier = Modifier.height(14.dp))

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

                // Primary Mic Action Button
                Box(
                    modifier = Modifier
                        .size(56.dp)
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
                        modifier = Modifier.size(28.dp)
                    )
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
            Text(
                text = "${item.format} · ${item.sizeFormatted} · ${item.dateText}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val infiniteTransition = rememberInfiniteTransition(label = "voiceOrb")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    // Dynamic scale driven by actual voice RMS amplitude
    val liveScale = if (state == AssistantVoiceState.LISTENING) {
        (1.0f + (soundRms.coerceIn(0f, 10f) / 18f))
    } else {
        pulseScale
    }

    Box(
        modifier = modifier
            .size(100.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = liveScale,
                    scaleY = liveScale,
                    rotationZ = if (state == AssistantVoiceState.PROCESSING) rotationAngle else 0f
                )
        ) {
            val auraColor = when (state) {
                AssistantVoiceState.LISTENING -> Color(0xFF26E07F)
                AssistantVoiceState.PROCESSING -> Color(0xFF00C2A8)
                AssistantVoiceState.SPEAKING -> Color(0xFF55A9AF)
                else -> Color(0xFF00C2A8)
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        auraColor.copy(alpha = 0.7f),
                        auraColor.copy(alpha = 0.35f),
                        Color.Transparent
                    )
                )
            )
        }

        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF00C2A8),
                            Color(0xFF55A9AF)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (state) {
                    AssistantVoiceState.LISTENING -> Icons.Default.Mic
                    AssistantVoiceState.PROCESSING -> Icons.Default.AutoAwesome
                    AssistantVoiceState.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
                    else -> Icons.Default.SmartToy
                },
                contentDescription = "Voice State",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
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
            Text(
                text = "${file.format} · ${file.size}" + (if (file.page != null) " · Page ${file.page}" else ""),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

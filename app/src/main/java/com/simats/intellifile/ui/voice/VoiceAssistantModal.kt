package com.simats.intellifile.ui.voice

import android.Manifest
import android.app.Activity
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.simats.intellifile.R
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.repository.AiRepository
import com.simats.intellifile.ui.ai.AiFileResult
import com.simats.intellifile.ui.ai.AiFluidOrbAvatar
import com.simats.intellifile.ui.detail.FileDetailData
import com.simats.intellifile.ui.home.PriorityLevel
import com.simats.intellifile.ui.home.PriorityTag
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
    var assistantReplyText by remember { mutableStateOf("") }
    var attachedFiles by remember { mutableStateOf<List<AiFileResult>>(emptyList()) }
    var currentConversationId by remember { mutableStateOf<Int?>(null) }
    var statusMessage by remember { mutableStateOf("Tap the microphone and speak...") }
    var isMuted by remember { mutableStateOf(false) }

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
        return raw.replace(Regex("[*#_`~>•]"), "")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
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
                // Proceed with query
            } else {
                voiceState = AssistantVoiceState.IDLE
            }
        } else {
            voiceState = AssistantVoiceState.IDLE
        }
    }

    fun executeQuery(spokenQuery: String) {
        val query = spokenQuery.trim()
        if (query.isBlank()) return

        stopSpeaking()
        userSpeechText = query
        voiceState = AssistantVoiceState.PROCESSING
        statusMessage = "Analyzing with IntelliFile AI..."

        coroutineScope.launch {
            val lower = query.lowercase()

            // 1. Direct Voice Action: "open <document>"
            if (lower.startsWith("open ") || lower.startsWith("view ")) {
                val docName = query.substringAfter(" ").trim()
                val localFiles = LocalFileManager.getDeviceFiles(context)
                val directMatch = localFiles.firstOrNull {
                    it.title.contains(docName, ignoreCase = true) ||
                            it.title.substringBeforeLast('.').contains(docName, ignoreCase = true)
                }

                if (directMatch != null) {
                    val reply = "Opening ${directMatch.title} for you."
                    assistantReplyText = reply
                    statusMessage = "Opening file..."
                    voiceState = AssistantVoiceState.SPEAKING
                    speakAnswer(reply)
                    delay(800L)
                    onOpenFile(
                        FileDetailData(
                            fileName = directMatch.title,
                            fileSize = directMatch.sizeFormatted,
                            fileType = directMatch.format,
                            createdDate = directMatch.dateText,
                            modifiedDate = directMatch.dateText,
                            priority = directMatch.priority,
                            iconResId = directMatch.iconResId,
                            uriString = directMatch.uri.toString(),
                            path = directMatch.path
                        )
                    )
                    return@launch
                }
            }

            // 2. Direct Voice Action: "search <query>"
            if (lower.startsWith("search ") || lower.startsWith("find ")) {
                val searchTerm = query.substringAfter(" ").trim()
                onSearchHome(searchTerm)
            }

            // 3. Hybrid AI Query (SLM + RAG + App Statistics)
            val result = aiRepository.sendChatMessage(
                message = query,
                conversationId = currentConversationId
            )

            result.onSuccess { response ->
                currentConversationId = response.conversation_id
                val reply = response.message?.text ?: response.reply ?: "Here is the information you requested."
                assistantReplyText = reply

                val rawFiles = response.message?.files
                    ?: response.message?.related_files
                    ?: response.related_files
                    ?: emptyList()

                attachedFiles = rawFiles.map { rf ->
                    val prio = when (rf.priority?.uppercase()) {
                        "IMPORTANT" -> PriorityLevel.IMPORTANT
                        "LOW" -> PriorityLevel.LOW
                        else -> PriorityLevel.NORMAL
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

                statusMessage = "AI answered:"
                voiceState = AssistantVoiceState.SPEAKING
                speakAnswer(reply)
            }.onFailure { err ->
                voiceState = AssistantVoiceState.ERROR
                val errMsg = "Could not reach AI service: ${err.localizedMessage ?: "Unknown error"}"
                assistantReplyText = errMsg
                statusMessage = "An error occurred"
                speakAnswer(errMsg)
            }
        }
    }

    fun startListening() {
        stopSpeaking()
        if (!hasAudioPermission) return

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
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client speech error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission missing"
                            SpeechRecognizer.ERROR_NETWORK -> "Network connection issue"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Tap mic to retry."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                            SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap mic to talk."
                            else -> "Tap the microphone to speak"
                        }
                        statusMessage = message
                        voiceState = AssistantVoiceState.IDLE
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            executeQuery(text)
                        } else {
                            voiceState = AssistantVoiceState.IDLE
                            statusMessage = "Didn't catch that. Tap mic to speak."
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
                // Fallback to system speech launcher
                try {
                    systemSpeechLauncher.launch(speechIntent)
                } catch (_: Exception) {
                    voiceState = AssistantVoiceState.ERROR
                    statusMessage = "Speech recognition unavailable on this device."
                }
            }
        } else {
            // SpeechRecognizer unavailable directly, launch system speech activity
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
                .padding(bottom = 28.dp),
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
                            text = "Powered by Hybrid RAG & SLM",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mute / Speaker toggle
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

            Spacer(modifier = Modifier.height(14.dp))

            // Main Content Area
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // If permission is missing
                if (!hasAudioPermission) {
                    item {
                        PermissionRequiredCard(
                            onRequestPermission = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                    }
                } else {
                    // Central Voice Orb Visualizer
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

                    // Status Indicator Text
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

                    // User Spoken Transcript Card
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

                    // Attached Files Cards
                    if (attachedFiles.isNotEmpty()) {
                        item {
                            Text(
                                text = "Referenced Files (${attachedFiles.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }

                        items(attachedFiles) { file ->
                            VoiceAssistantFileCard(
                                file = file,
                                onOpen = {
                                    stopSpeaking()
                                    val localMatch = LocalFileManager.getDeviceFiles(context).firstOrNull {
                                        it.title.equals(file.title, ignoreCase = true) ||
                                                it.title.equals(file.title.substringBeforeLast('.'), ignoreCase = true)
                                    }
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

                    // Suggested Prompts Chips (when IDLE and no conversation yet)
                    if (userSpeechText.isBlank() && voiceState == AssistantVoiceState.IDLE) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Try asking:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val samplePrompts = listOf(
                                    "How many total files do I have?",
                                    "Summarize my uploaded PDF",
                                    "What is Machine Learning?",
                                    "Find important files"
                                )

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(samplePrompts) { prompt ->
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.clickable {
                                                userSpeechText = prompt
                                                executeQuery(prompt)
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

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary Action: Open in AI Chat if there's an ongoing answer
                if (assistantReplyText.isNotBlank()) {
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
        // Glowing Aura Canvas
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

        // Inner Gradient Orb
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
                text = "IntelliFile needs microphone permission to listen to your questions, search files, and give spoken answers.",
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

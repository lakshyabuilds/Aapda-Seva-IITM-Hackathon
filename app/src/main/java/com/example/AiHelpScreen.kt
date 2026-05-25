package com.example

import android.Manifest
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AiHelpScreen(location: Location?) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val recordAudioPermissionState = rememberPermissionState(
        Manifest.permission.RECORD_AUDIO
    )

    var isRecording by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("Hold the microphone button and speak your query.") }
    var isLoading by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val textToSpeech = TextToSpeech(context) { _ -> }
        tts = textToSpeech
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    val fallbackError = stringResource(id = R.string.ai_fallback_error)
    val fallbackEmpty = stringResource(id = R.string.ai_fallback_empty)

    val speechRecognizer = remember {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        if (recognizer == null) {
            null
        } else {
            recognizer.apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isRecording = false
                    }
                    override fun onError(error: Int) {
                        isRecording = false
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech heard"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                            else -> "Failed to recognize speech"
                        }
                        if (recognizedText.isBlank()) {
                            responseText = errorMsg
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            recognizedText = text
                            responseText = "Thinking..."
                            isLoading = true
                            
                            coroutineScope.launch {
                                val locText = location?.let { 
                                    "Lat: ${"%.4f".format(it.latitude)}, Lon: ${"%.4f".format(it.longitude)}" 
                                } ?: "Unknown"
                                val aiResponse = generateAiHelpResponse(text, locText, fallbackError, fallbackEmpty)
                                val cleanResponse = aiResponse.replace("*", "").replace("#", "").replace("`", "").trim()
                                responseText = cleanResponse
                                isLoading = false
                                try {
                                    val ttsText = cleanResponse.replace("⚠️", "").take(3900)
                                    tts?.speak(ttsText, TextToSpeech.QUEUE_FLUSH, null, null)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            recognizedText = matches[0]
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    LaunchedEffect(Unit) {
        if (!recordAudioPermissionState.status.isGranted) {
            recordAudioPermissionState.launchPermissionRequest()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "AI Voice Assistant",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        Text(
            text = "Ask for emergency advice",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Mic Button
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(
                    if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
                .semantics {
                    contentDescription = "Voice Assist Microphone"
                    role = Role.Button
                    stateDescription = if (isRecording) "Recording" else "Not recording"
                    onClick(label = "Hold to record voice query", action = null)
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (recordAudioPermissionState.status.isGranted) {
                                isRecording = true
                                recognizedText = "Listening..."
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                }
                                try {
                                    speechRecognizer?.startListening(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                
                                tryAwaitRelease()
                                
                                isRecording = false
                                try {
                                    speechRecognizer?.stopListening()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else {
                                recordAudioPermissionState.launchPermissionRequest()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Microphone",
                tint = Color.White,
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Transcription or status
        Text(
            text = recognizedText.ifBlank { "..." },
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // AI Response Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Text(
                    text = responseText,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Offline Quick Actions
        Text(
            text = "Quick Offline Guides",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val quickActions = listOf(
                Pair(R.string.guide_bleeding_title, R.string.guide_bleeding_desc),
                Pair(R.string.guide_choking_title, R.string.guide_choking_desc),
                Pair(R.string.guide_burn_title, R.string.guide_burn_desc),
                Pair(R.string.guide_fracture_title, R.string.guide_fracture_desc),
                Pair(R.string.guide_heart_attack_title, R.string.guide_heart_attack_desc)
            )
            items(quickActions) { (titleRes, guideRes) ->
                val title = stringResource(id = titleRes)
                val guide = stringResource(id = guideRes)
                
                AssistChip(
                    onClick = { 
                        recognizedText = title
                        responseText = guide 
                        try {
                            tts?.speak(guide.take(3900), TextToSpeech.QUEUE_FLUSH, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    label = { Text(title) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Warning",
                            Modifier.size(AssistChipDefaults.IconSize)
                        )
                    }
                )
            }
        }
    }
}

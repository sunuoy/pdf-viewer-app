package com.pdfsuny.app.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class TtsState {
    IDLE,
    SPEAKING,
    PAUSED
}

class TtsService(context: Context) : TextToSpeech.OnInitListener {

    private companion object {
        const val TAG = "TtsService"
        const val UTTERANCE_ID_PREFIX = "pdf_viewer_tts_"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    data class TtsSentence(
        val text: String,
        val startCharOffset: Int
    )

    private val _state = MutableStateFlow(TtsState.IDLE)
    val state: StateFlow<TtsState> = _state

    private val _isMale = MutableStateFlow(false)
    val isMale: StateFlow<Boolean> = _isMale

    private val _currentWordRange = MutableStateFlow<Pair<Int, Int>?>(null)
    val currentWordRange: StateFlow<Pair<Int, Int>?> = _currentWordRange

    private var currentSpeed = 1.0f
    private var currentPitch = 1.0f
    private var currentLanguage = Locale.getDefault().toLanguageTag()
    
    private var fullText: String = ""
    private var sentences = listOf<TtsSentence>()
    private var currentSentenceIndex = 0

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setSpeechRate(currentSpeed)
            tts?.setPitch(currentPitch)
            setLanguage(currentLanguage)
            applyVoice()
            setupUtteranceListener()
        } else {
            Log.e(TAG, "Initialization of TextToSpeech failed")
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = TtsState.SPEAKING
            }

            override fun onDone(utteranceId: String?) {
                _currentWordRange.value = null
                if (_state.value == TtsState.SPEAKING) {
                    currentSentenceIndex++
                    speakNextSentence()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS Error: $utteranceId")
                _state.value = TtsState.IDLE
                _currentWordRange.value = null
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS Error: $utteranceId, error code: $errorCode")
                _state.value = TtsState.IDLE
                _currentWordRange.value = null
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                _currentWordRange.value = null
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId != null && utteranceId.startsWith(UTTERANCE_ID_PREFIX)) {
                    val sentIndex = utteranceId.substring(UTTERANCE_ID_PREFIX.length).toIntOrNull() ?: return
                    if (sentIndex in sentences.indices) {
                        val sentence = sentences[sentIndex]
                        _currentWordRange.value = Pair(sentence.startCharOffset + start, sentence.startCharOffset + end)
                    }
                }
            }
        })
    }

    /**
     * Start reading text.
     */
    fun startSpeaking(text: String) {
        if (!isInitialized || text.isBlank()) return
        stop()

        fullText = text
        val rawSentences = text.split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val list = mutableListOf<TtsSentence>()
        var searchStart = 0
        for (raw in rawSentences) {
            val offset = text.indexOf(raw, searchStart)
            if (offset != -1) {
                list.add(TtsSentence(raw, offset))
                searchStart = offset + raw.length
            }
        }
        sentences = list
        
        currentSentenceIndex = 0
        if (sentences.isNotEmpty()) {
            _state.value = TtsState.SPEAKING
            speakNextSentence()
        }
    }

    private fun speakNextSentence() {
        if (currentSentenceIndex < sentences.size) {
            val sentence = sentences[currentSentenceIndex]
            val utteranceId = "$UTTERANCE_ID_PREFIX$currentSentenceIndex"
            tts?.speak(sentence.text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            // Finished reading everything
            _state.value = TtsState.IDLE
            _currentWordRange.value = null
            currentSentenceIndex = 0
        }
    }

    /**
     * Pause reading. Stops active TTS engine, saving the current progress index.
     */
    fun pause() {
        if (_state.value == TtsState.SPEAKING) {
            _state.value = TtsState.PAUSED
            tts?.stop()
        }
    }

    /**
     * Resume reading from the last paused sentence index.
     */
    fun resume() {
        if (_state.value == TtsState.PAUSED && currentSentenceIndex < sentences.size) {
            _state.value = TtsState.SPEAKING
            speakNextSentence()
        }
    }

    /**
     * Stop reading entirely.
     */
    fun stop() {
        _state.value = TtsState.IDLE
        _currentWordRange.value = null
        tts?.stop()
        currentSentenceIndex = 0
        sentences = emptyList()
        fullText = ""
    }

    /**
     * Set speed of TTS speaking voice.
     */
    fun setSpeed(speed: Float) {
        currentSpeed = speed
        if (isInitialized) {
            tts?.setSpeechRate(speed)
        }
    }

    /**
     * Set pitch of TTS speaking voice.
     */
    fun setPitch(pitch: Float) {
        currentPitch = pitch
        if (isInitialized) {
            tts?.setPitch(pitch)
        }
    }

    /**
     * Set language locale of TTS speaking voice.
     */
    fun setLanguage(languageCode: String) {
        currentLanguage = languageCode
        if (isInitialized) {
            val locale = Locale.forLanguageTag(languageCode)
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language $languageCode is not supported or missing data")
            }
            applyVoice()
        }
    }

    /**
     * Set voice gender preference (Male vs Female).
     */
    fun setVoiceGender(isMale: Boolean) {
        _isMale.value = isMale
        if (isInitialized) {
            applyVoice()
        }
    }

    private fun applyVoice() {
        val currentLocale = Locale.forLanguageTag(currentLanguage)
        val allVoices = tts?.voices ?: return
        val isMalePreferred = _isMale.value

        // Log available voices for diagnostics
        Log.d(TAG, "Total voices available: ${allVoices.size}")
        allVoices.forEach { voice ->
            Log.d(TAG, "Voice: ${voice.name}, Locale: ${voice.locale}")
        }

        // Adjust pitch to simulate gender frequency shifts
        if (isMalePreferred) {
            tts?.setPitch(1.0f) // Simulated male voice pitch
        } else {
            tts?.setPitch(0.78f) // Simulated female voice pitch
        }

        // Filter voices matching language first
        val localeVoices = allVoices.filter { voice ->
            voice.locale.language.equals(currentLocale.language, ignoreCase = true)
        }

        if (localeVoices.isEmpty()) return

        // Search for voice by gender pattern matching
        val matchedVoice = localeVoices.firstOrNull { voice ->
            val name = voice.name.lowercase(Locale.ROOT)
            if (isMalePreferred) {
                name.contains("male") || name.contains("m-otc") || name.contains("en-us-x-i-local") || name.contains("en-us-x-sfg-local") || name.contains("en-us-x-nhg-local")
            } else {
                name.contains("female") || name.contains("f-otc") || name.contains("en-us-x-a-local") || name.contains("en-us-x-sfd-local") || name.contains("en-us-x-sfg-local")
            }
        } ?: localeVoices.firstOrNull()

        if (matchedVoice != null) {
            tts?.voice = matchedVoice
            Log.d(TAG, "Selected voice: ${matchedVoice.name}")
        }
    }

    /**
     * Shut down the engine.
     */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

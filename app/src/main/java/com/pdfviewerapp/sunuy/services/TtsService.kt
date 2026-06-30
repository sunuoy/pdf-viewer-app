package com.pdfviewerapp.sunuy.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

    private val _state = MutableStateFlow(TtsState.IDLE)
    val state: StateFlow<TtsState> = _state

    private var currentSpeed = 1.0f
    private var currentPitch = 1.0f
    private var currentLanguage = Locale.getDefault().toLanguageTag()
    
    private var fullText: String = ""
    private var sentences = listOf<String>()
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
                if (_state.value == TtsState.SPEAKING) {
                    currentSentenceIndex++
                    speakNextSentence()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS Error: $utteranceId")
                _state.value = TtsState.IDLE
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS Error: $utteranceId, error code: $errorCode")
                _state.value = TtsState.IDLE
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                // Interrupted can mean paused or stopped
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
        // Split text into sentences using simple regex punctuation bounds
        sentences = text.split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
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
            tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            // Finished reading everything
            _state.value = TtsState.IDLE
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

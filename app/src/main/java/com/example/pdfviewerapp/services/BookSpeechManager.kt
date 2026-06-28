package com.pdfviewerapp.sunuy.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class BookSpeechManager(context: Context) : TextToSpeech.OnInitListener {

    private var ttsEngine: TextToSpeech? = TextToSpeech(context, this)
    private var isEngineReady = false
    private var onSentenceCompleteListener: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = ttsEngine?.setLanguage(Locale.getDefault())
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("BookSpeechManager", "Target language pack not installed or supported on this phone.")
                isEngineReady = false
            } else {
                isEngineReady = true
                setupUtteranceListener()
            }
        } else {
            Log.e("BookSpeechManager", "Initialization of native TTS Engine failed.")
        }
    }

    private fun setupUtteranceListener() {
        ttsEngine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Utterance started
            }

            override fun onDone(utteranceId: String?) {
                onSentenceCompleteListener?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("BookSpeechManager", "Error during oral rendering code: $errorCode")
            }
        })
    }

    fun speakSentence(text: String, utteranceId: String) {
        if (!isEngineReady) return
        ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun setSpeed(speedMultiplier: Float) {
        ttsEngine?.setSpeechRate(speedMultiplier)
    }

    fun setPitch(pitch: Float) {
        ttsEngine?.setPitch(pitch)
    }

    fun pauseOrStop() {
        if (ttsEngine?.isSpeaking == true) {
            ttsEngine?.stop()
        }
    }

    fun registerCompletionCallback(callback: () -> Unit) {
        this.onSentenceCompleteListener = callback
    }

    fun release() {
        ttsEngine?.stop()
        ttsEngine?.shutdown()
        ttsEngine = null
    }
}

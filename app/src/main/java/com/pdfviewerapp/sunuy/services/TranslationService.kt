package com.pdfviewerapp.sunuy.services

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LanguageItem(val code: String, val name: String)

class TranslationService {

    val supportedLanguages = listOf(
        LanguageItem(TranslateLanguage.ENGLISH, "English"),
        LanguageItem(TranslateLanguage.SPANISH, "Spanish"),
        LanguageItem(TranslateLanguage.FRENCH, "French"),
        LanguageItem(TranslateLanguage.GERMAN, "German"),
        LanguageItem(TranslateLanguage.HINDI, "Hindi"),
        LanguageItem(TranslateLanguage.CHINESE, "Chinese"),
        LanguageItem(TranslateLanguage.ARABIC, "Arabic"),
        LanguageItem(TranslateLanguage.PORTUGUESE, "Portuguese"),
        LanguageItem(TranslateLanguage.ITALIAN, "Italian"),
        LanguageItem(TranslateLanguage.JAPANESE, "Japanese"),
        LanguageItem(TranslateLanguage.KOREAN, "Korean"),
        LanguageItem(TranslateLanguage.RUSSIAN, "Russian"),
        LanguageItem(TranslateLanguage.TELUGU, "Telugu")
    )

    private val modelManager = RemoteModelManager.getInstance()
    private var activeTranslator: Translator? = null
    private var activeSourceLang: String? = null
    private var activeTargetLang: String? = null

    /**
     * Extension to convert ML Kit Tasks into suspend functions.
     */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
    }

    /**
     * Check if a translation language model is downloaded.
     */
    suspend fun isModelDownloaded(langCode: String): Boolean = withContext(Dispatchers.IO) {
        val model = TranslateRemoteModel.Builder(langCode).build()
        return@withContext try {
            modelManager.isModelDownloaded(model).await()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Download a translation language model.
     */
    suspend fun downloadModel(langCode: String): Boolean = withContext(Dispatchers.IO) {
        val model = TranslateRemoteModel.Builder(langCode).build()
        val conditions = DownloadConditions.Builder().build()
        return@withContext try {
            modelManager.download(model, conditions).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete a downloaded model.
     */
    suspend fun deleteModel(langCode: String): Boolean = withContext(Dispatchers.IO) {
        val model = TranslateRemoteModel.Builder(langCode).build()
        return@withContext try {
            modelManager.deleteDownloadedModel(model).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Translate text from a source language to a target language.
     * Automatically downloads the model if it is not already available.
     */
    suspend fun translate(
        text: String,
        sourceLangCode: String = TranslateLanguage.ENGLISH,
        targetLangCode: String
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""

        // Re-use active translator if source and target languages match
        if (activeTranslator != null && activeSourceLang == sourceLangCode && activeTargetLang == targetLangCode) {
            return@withContext try {
                activeTranslator!!.translate(text).await()
            } catch (e: Exception) {
                "Translation failed: ${e.localizedMessage}"
            }
        }

        // Close old translator to save resources
        closeActiveTranslator()

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLangCode)
            .setTargetLanguage(targetLangCode)
            .build()

        val translator = Translation.getClient(options)
        activeTranslator = translator
        activeSourceLang = sourceLangCode
        activeTargetLang = targetLangCode

        return@withContext try {
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()
            translator.translate(text).await()
        } catch (e: Exception) {
            "Translation failed: ${e.localizedMessage}"
        }
    }

    fun closeActiveTranslator() {
        activeTranslator?.close()
        activeTranslator = null
        activeSourceLang = null
        activeTargetLang = null
    }
}

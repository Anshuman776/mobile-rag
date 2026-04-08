package com.ml.Anshuman776.docqa.domain.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File

@Single
class GemmaOfflineEngine : LLMInferenceAPI() {
    private var llmInference: LlmInference? = null
    private val mutex = Mutex()
    
    val isLoaded: Boolean
        get() = llmInference != null

    suspend fun loadModel(context: Context): Boolean = mutex.withLock {
        if (llmInference != null) return@withLock true
        
        return@withLock withContext(Dispatchers.IO) {
            try {
                val modelFile = GemmaModelDownloader.getModelFile(context)
                if (!modelFile.exists()) {
                    Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
                    return@withContext false
                }

                // Clear ONLY the MediaPipe internal cache to fix "Cannot reserve space..." crash
                // Do not clear the entire filesDir as it contains the model and other data.
                clearInferenceCache(context)

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(2048)
                    .build()

                Log.d(TAG, "Initializing MediaPipe LlmInference...")
                llmInference = LlmInference.createFromOptions(context, options)
                Log.d(TAG, "Gemma model loaded successfully")
                true
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to load Gemma model", exception)
                false
            }
        }
    }

    private fun clearInferenceCache(context: Context) {
        try {
            // MediaPipe and XNNPack store temporary cache files in the cache directory.
            // These files often cause SIGABRT crashes if they become corrupted or are from a different version.
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.contains("xnnpack", ignoreCase = true) || 
                    file.name.contains("llm_inference", ignoreCase = true)) {
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                    Log.d(TAG, "Cleared cache item: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }

    override suspend fun getResponse(prompt: String): String? = withContext(Dispatchers.Default) {
        val engine = llmInference ?: return@withContext null
        Log.d(TAG, "Generating offline response...")
        try {
            engine.generateResponse(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Error during generation", e)
            null
        }
    }

    fun release() {
        llmInference?.close()
        llmInference = null
    }

    companion object {
        private const val TAG = "GemmaOfflineEngine"
    }
}

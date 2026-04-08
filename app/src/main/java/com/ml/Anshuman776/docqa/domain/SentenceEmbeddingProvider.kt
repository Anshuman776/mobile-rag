package com.ml.Anshuman776.docqa.domain

import android.content.Context
import android.util.Log
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File

@Single
class SentenceEmbeddingProvider(
    private val context: Context,
) {
    private val sentenceEmbedding = SentenceEmbedding()
    private val mutex = Mutex()
    private var isInitialized = false

    suspend fun initialize() = mutex.withLock {
        if (isInitialized) return@withLock
        withContext(Dispatchers.IO) {
            try {
                val onnxLocalFile = copyToLocalStorage("all-MiniLM-L6-V2.onnx")
                val tokenizerLocalFile = copyToLocalStorage("tokenizer.json")
                val tokenizerBytes = tokenizerLocalFile.readBytes()
                sentenceEmbedding.init(
                    onnxLocalFile.absolutePath,
                    tokenizerBytes,
                    useTokenTypeIds = false,
                    outputTensorName = "last_hidden_state",
                    normalizeEmbeddings = false,
                )
                isInitialized = true
                Log.d("SentenceEmbedding", "Initialized successfully")
            } catch (e: Exception) {
                Log.e("SentenceEmbedding", "Initialization failed", e)
            }
        }
    }

    suspend fun encodeText(text: String): FloatArray {
        if (!isInitialized) {
            initialize()
        }
        return withContext(Dispatchers.Default) {
            return@withContext sentenceEmbedding.encode(text)
        }
    }

    private fun copyToLocalStorage(filename: String): File {
        val storageFile = File(context.filesDir, filename)
        if (!storageFile.exists()) {
            context.assets.open(filename).use { inputStream ->
                val bytes = inputStream.readBytes()
                storageFile.writeBytes(bytes)
            }
        }
        return storageFile
    }
}

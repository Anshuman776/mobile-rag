package com.ml.Anshuman776.docqa.domain.llm

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object GemmaModelDownloader {
    private const val TAG = "GemmaModelDownloader"
    private const val MODEL_FILE_NAME = "gemma3-1b-it-int4.task"
    
    // Gemma 3 1B IT int4 task file is ~800MB. 
    // Setting 500MB as a safety threshold for "downloaded" status.
    private const val MIN_VALID_BYTES = 500_000_000L

    private const val MODEL_URL =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"

    fun getModelFile(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val dir = File(baseDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, MODEL_FILE_NAME)
    }

    fun isDownloaded(context: Context): Boolean {
        val file = getModelFile(context)
        return file.exists() && file.length() > MIN_VALID_BYTES
    }

    fun download(
        context: Context,
        hfToken: String? = null,
        onProgress: (percent: Int, mbDone: Int, mbTotal: Int) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        try {
            val availableStorage = getAvailableStorage(context)
            if (availableStorage < 1_500_000_000L) {
                onError("Not enough storage. Please free up at least 1.5 GB.")
                return
            }

            val finalFile = getModelFile(context)
            val tempFile = File(finalFile.parentFile, "temp_$MODEL_FILE_NAME")
            if (tempFile.exists()) {
                tempFile.delete()
            }

            // Clear the cache directory before starting a new download to ensure a clean state
            clearInferenceCache(context)

            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0")
                if (!hfToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $hfToken")
                }
            }

            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val errorMessage = when (connection.responseCode) {
                    401 -> "Unauthorized. Check your HuggingFace access token."
                    403 -> "Forbidden. Accept the model license on HuggingFace first."
                    404 -> "Model file not found at the configured URL."
                    else -> "Server error: ${connection.responseCode}"
                }
                connection.disconnect()
                onError(errorMessage)
                return
            }

            val totalBytes = connection.contentLengthLong
            val totalMB = if (totalBytes > 0) (totalBytes / 1_048_576).toInt() else 0

            connection.inputStream.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastUpdate = 0L

                    while (true) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) {
                            break
                        }
                        outputStream.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 1_000L) {
                            val percent = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                            val mbDone = (downloaded / 1_048_576).toInt()
                            onProgress(percent, mbDone, totalMB)
                            lastUpdate = now
                        }
                    }
                    outputStream.flush()

                    if (downloaded < MIN_VALID_BYTES) {
                        tempFile.delete()
                        onError("Download incomplete or corrupted.")
                        return
                    }
                }
            }

            connection.disconnect()

            if (finalFile.exists()) {
                finalFile.delete()
            }
            if (!tempFile.renameTo(finalFile)) {
                tempFile.delete()
                onError("Failed to save model file.")
                return
            }

            onSuccess()
        } catch (exception: Exception) {
            Log.e(TAG, "Download error", exception)
            onError("Network error: ${exception.localizedMessage}")
        }
    }

    fun clearInferenceCache(context: Context) {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.contains("xnnpack_cache") || file.name.contains("llm_inference")) {
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                    Log.d(TAG, "Deleted cache item: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
        }
    }

    private fun getAvailableStorage(context: Context): Long {
        val dir = getModelFile(context).parentFile ?: File("/data")
        val statFs = StatFs(dir.path)
        return statFs.availableBlocksLong * statFs.blockSizeLong
    }
}

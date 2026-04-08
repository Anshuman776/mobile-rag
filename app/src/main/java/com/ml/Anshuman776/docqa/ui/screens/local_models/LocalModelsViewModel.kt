package com.ml.Anshuman776.docqa.ui.screens.local_models

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.Anshuman776.docqa.data.HFAccessToken
import com.ml.Anshuman776.docqa.data.LocalModel
import com.ml.Anshuman776.docqa.domain.llm.GemmaModelDownloader
import com.ml.Anshuman776.docqa.domain.llm.GemmaOfflineEngine
import com.ml.Anshuman776.docqa.ui.components.createAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

sealed class LocalModelsUIEvent {
    data class OnModelDownloadClick(
        val model: LocalModel,
    ) : LocalModelsUIEvent()

    data class OnUseModelClick(
        val model: LocalModel,
    ) : LocalModelsUIEvent()

    data object RefreshModelsList : LocalModelsUIEvent()
}

data class LocalModelsUIState(
    val models: List<LocalModel> = emptyList(),
    val downloadModelDialogState: DownloadModelDialogUIState = DownloadModelDialogUIState(),
    val isModelLoading: Boolean = false,
)

data class DownloadModelDialogUIState(
    val isDialogVisible: Boolean = false,
    val showProgress: Boolean = false,
    val progress: Int = 0,
)

@KoinViewModel
class LocalModelsViewModel(
    private val context: Context,
    private val gemmaOfflineEngine: GemmaOfflineEngine,
    private val hfAccessToken: HFAccessToken,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            LocalModelsUIState(
                models =
                    listOf(
                        LocalModel(
                            name = "Gemma3 1B IT",
                            description = "Gemma 3 1B Instruction-Tuned local model",
                            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
                        ),
                    ),
            ),
        )
    val uiState: StateFlow<LocalModelsUIState> = _uiState.asStateFlow()

    fun onEvent(event: LocalModelsUIEvent) {
        when (event) {
            is LocalModelsUIEvent.OnModelDownloadClick -> {
                viewModelScope.launch(Dispatchers.IO) {
                    downloadModel(event.model)
                }
            }

            is LocalModelsUIEvent.OnUseModelClick -> {
                viewModelScope.launch(Dispatchers.IO) {
                    _uiState.update { it.copy(isModelLoading = true) }
                    loadModel(event.model)
                    _uiState.update { it.copy(isModelLoading = false) }
                    onEvent(LocalModelsUIEvent.RefreshModelsList)
                }
            }

            is LocalModelsUIEvent.RefreshModelsList -> {
                _uiState.update { state ->
                    state.copy(
                        models =
                            state.models.map { model ->
                                model.copy(
                                    isDownloaded = GemmaModelDownloader.isDownloaded(context),
                                    isLoaded = gemmaOfflineEngine.isLoaded,
                                )
                            },
                    )
                }
            }
        }
    }

    private suspend fun loadModel(model: LocalModel) =
        withContext(Dispatchers.IO) {
            try {
                val isLoaded = gemmaOfflineEngine.loadModel(context)
                if (!isLoaded) {
                    Log.e("APP", "Failed to load Gemma model")
                    withContext(Dispatchers.Main) {
                        createAlertDialog(
                            dialogTitle = "Error",
                            dialogText = "Failed to load Gemma model. Check if the file is corrupted or if your device is compatible.",
                            dialogPositiveButtonText = "Ok",
                            onPositiveButtonClick = {},
                            dialogNegativeButtonText = null,
                            onNegativeButtonClick = null,
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Model loaded successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("APP", "Exception loading model", e)
                withContext(Dispatchers.Main) {
                    createAlertDialog(
                        dialogTitle = "Error",
                        dialogText = "An error occurred: ${e.message}",
                        dialogPositiveButtonText = "Ok",
                        onPositiveButtonClick = {},
                        dialogNegativeButtonText = null,
                        onNegativeButtonClick = null,
                    )
                }
            }
        }

    private suspend fun downloadModel(model: LocalModel) {
        _uiState.update {
            it.copy(
                downloadModelDialogState =
                    it.downloadModelDialogState.copy(
                        isDialogVisible = true,
                        showProgress = true,
                        progress = 0,
                    ),
            )
        }
        GemmaModelDownloader.download(
            context = context,
            hfToken = hfAccessToken.getToken(),
            onProgress = { percent, _, _ ->
                _uiState.update {
                    it.copy(
                        downloadModelDialogState =
                            it.downloadModelDialogState.copy(
                                isDialogVisible = true,
                                showProgress = true,
                                progress = percent,
                            ),
                    )
                }
            },
            onSuccess = {
                _uiState.update {
                    it.copy(
                        downloadModelDialogState =
                            it.downloadModelDialogState.copy(
                                isDialogVisible = false,
                                showProgress = false,
                            ),
                    )
                }
                viewModelScope.launch(Dispatchers.IO) {
                    loadModel(model)
                    onEvent(LocalModelsUIEvent.RefreshModelsList)
                    withContext(Dispatchers.Main) {
                        Toast
                            .makeText(
                                context,
                                "Model downloaded successfully",
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                }
            },
            onError = { errorMessage ->
                Log.e("APP", "Gemma download failed: $errorMessage")
                _uiState.update {
                    it.copy(
                        downloadModelDialogState =
                            it.downloadModelDialogState.copy(
                                isDialogVisible = false,
                                showProgress = false,
                            ),
                    )
                }
                viewModelScope.launch(Dispatchers.Main) {
                    createAlertDialog(
                        dialogTitle = "Error",
                        dialogText = errorMessage,
                        dialogPositiveButtonText = "Ok",
                        onPositiveButtonClick = {},
                        dialogNegativeButtonText = null,
                        onNegativeButtonClick = null,
                    )
                }
            },
        )
    }
}

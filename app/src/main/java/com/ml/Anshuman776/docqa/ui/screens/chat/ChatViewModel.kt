package com.ml.Anshuman776.docqa.ui.screens.chat

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.Anshuman776.docqa.data.ChunksDB
import com.ml.Anshuman776.docqa.data.DocumentsDB
import com.ml.Anshuman776.docqa.data.RetrievedContext
import com.ml.Anshuman776.docqa.domain.SentenceEmbeddingProvider
import com.ml.Anshuman776.docqa.domain.llm.GemmaOfflineEngine
import com.ml.Anshuman776.docqa.domain.llm.LLMInferenceAPI
import com.ml.Anshuman776.docqa.ui.components.createAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

sealed interface ChatScreenUIEvent {
    data object OnEditCredentialsClick : ChatScreenUIEvent

    data object OnOpenDocsClick : ChatScreenUIEvent

    data object OnLocalModelsClick : ChatScreenUIEvent

    sealed class ResponseGeneration {
        data class Start(
            val query: String,
            val prompt: String,
        ) : ChatScreenUIEvent

        data class StopWithSuccess(
            val response: String,
            val retrievedContextList: List<RetrievedContext>,
        ) : ChatScreenUIEvent

        data class StopWithError(
            val errorMessage: String,
        ) : ChatScreenUIEvent
    }
}

sealed interface ChatNavEvent {
    data object None : ChatNavEvent

    data object ToEditAPIKeyScreen : ChatNavEvent

    data object ToDocsScreen : ChatNavEvent

    data object ToLocalModelsScreen : ChatNavEvent
}

data class ChatScreenUIState(
    val question: String = "",
    val response: String = "",
    val isGeneratingResponse: Boolean = false,
    val retrievedContextList: List<RetrievedContext> = emptyList(),
)

@KoinViewModel
class ChatViewModel(
    private val context: Context,
    private val documentsDB: DocumentsDB,
    private val chunksDB: ChunksDB,
    private val sentenceEncoder: SentenceEmbeddingProvider,
    private val gemmaOfflineEngine: GemmaOfflineEngine,
) : ViewModel() {
    private val _chatScreenUIState = MutableStateFlow(ChatScreenUIState())
    val chatScreenUIState: StateFlow<ChatScreenUIState> = _chatScreenUIState

    private val _navEventChannel = Channel<ChatNavEvent>()
    val navEventChannel = _navEventChannel.receiveAsFlow()

    fun onChatScreenEvent(event: ChatScreenUIEvent) {
        when (event) {
            is ChatScreenUIEvent.ResponseGeneration.Start -> {
                if (!checkNumDocuments()) {
                    Toast
                        .makeText(
                            context,
                            "Add documents to execute queries",
                            Toast.LENGTH_LONG,
                        ).show()
                    return
                }
                if (!checkLocalModelLoaded()) {
                    createAlertDialog(
                        dialogTitle = "No offline model loaded",
                        dialogText = "Download and load a local model from Manage Local Models before asking questions.",
                        dialogPositiveButtonText = "Open Local Models",
                        onPositiveButtonClick = {
                            onChatScreenEvent(ChatScreenUIEvent.OnLocalModelsClick)
                        },
                        dialogNegativeButtonText = null,
                        onNegativeButtonClick = null,
                    )
                    return
                }
                if (event.query.trim().isEmpty()) {
                    Toast
                        .makeText(context, "Enter a query to execute", Toast.LENGTH_LONG)
                        .show()
                    return
                }
                _chatScreenUIState.value =
                    _chatScreenUIState.value.copy(isGeneratingResponse = true)
                _chatScreenUIState.value =
                    _chatScreenUIState.value.copy(question = event.query)

                Toast.makeText(context, "Using offline on-device model...", Toast.LENGTH_LONG).show()
                getAnswer(gemmaOfflineEngine, event.query, event.prompt)
            }

            is ChatScreenUIEvent.ResponseGeneration.StopWithSuccess -> {
                _chatScreenUIState.value =
                    _chatScreenUIState.value.copy(isGeneratingResponse = false)
                _chatScreenUIState.value = _chatScreenUIState.value.copy(response = event.response)
                _chatScreenUIState.value =
                    _chatScreenUIState.value.copy(retrievedContextList = event.retrievedContextList)
            }

            is ChatScreenUIEvent.ResponseGeneration.StopWithError -> {
                _chatScreenUIState.value =
                    _chatScreenUIState.value.copy(isGeneratingResponse = false)
                _chatScreenUIState.value = _chatScreenUIState.value.copy(question = "")
            }

            is ChatScreenUIEvent.OnOpenDocsClick -> {
                viewModelScope.launch {
                    _navEventChannel.send(ChatNavEvent.ToDocsScreen)
                }
            }

            is ChatScreenUIEvent.OnEditCredentialsClick -> {
                viewModelScope.launch {
                    _navEventChannel.send(ChatNavEvent.ToEditAPIKeyScreen)
                }
            }

            is ChatScreenUIEvent.OnLocalModelsClick -> {
                viewModelScope.launch {
                    _navEventChannel.send(ChatNavEvent.ToLocalModelsScreen)
                }
            }
        }
    }

    private fun getAnswer(
        llm: LLMInferenceAPI,
        query: String,
        prompt: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var jointContext = ""
                val retrievedContextList = ArrayList<RetrievedContext>()
                
                // Encode text is now a suspend function
                val queryEmbedding = sentenceEncoder.encodeText(query)
                
                chunksDB.getSimilarChunks(queryEmbedding, n = 5).forEach {
                    jointContext += " " + it.second.chunkData
                    retrievedContextList.add(
                        RetrievedContext(
                            it.second.docFileName,
                            it.second.chunkData,
                        ),
                    )
                }
                val inputPrompt = prompt.replace("\$CONTEXT", jointContext).replace("\$QUERY", query)
                
                val llmResponse = llm.getResponse(inputPrompt)
                if (llmResponse != null) {
                    onChatScreenEvent(
                        ChatScreenUIEvent.ResponseGeneration.StopWithSuccess(
                            llmResponse,
                            retrievedContextList,
                        ),
                    )
                } else {
                    onChatScreenEvent(ChatScreenUIEvent.ResponseGeneration.StopWithError("Empty response from model"))
                }
            } catch (e: Exception) {
                onChatScreenEvent(ChatScreenUIEvent.ResponseGeneration.StopWithError(e.message ?: "Unknown error"))
            }
        }
    }

    fun checkNumDocuments(): Boolean = documentsDB.getDocsCount() > 0

    fun checkLocalModelLoaded(): Boolean = gemmaOfflineEngine.isLoaded
}

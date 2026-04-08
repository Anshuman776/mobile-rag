package com.ml.Anshuman776.docqa.domain.llm

abstract class LLMInferenceAPI {
    abstract suspend fun getResponse(prompt: String): String?
}

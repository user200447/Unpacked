package com.jassemsendi.unpacked.ui.ask

import com.jassemsendi.unpacked.agent.AgentUnavailable
import com.jassemsendi.unpacked.agent.AskAgent
import com.jassemsendi.unpacked.agent.AskError
import com.jassemsendi.unpacked.agent.AskOutcome
import com.jassemsendi.unpacked.agent.ModelInfo
import com.jassemsendi.unpacked.agent.ModelLocality
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.CompletableDeferred

/** An agent whose availability and answers a test scripts; it keeps the questions it was asked. */
class FakeAskAgent(
    var availability: Result<ModelInfo, AgentUnavailable> = Result.Success(ModelInfo("qwen3:8b", ModelLocality.Local)),
    private val answer: suspend (String) -> Result<AskOutcome, AskError> = { error("no answer scripted") },
) : AskAgent {
    val questions = mutableListOf<String>()

    /** When set, a question waits on it, so a test can see the asking state. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun available(): Result<ModelInfo, AgentUnavailable> = availability

    override suspend fun ask(question: String): Result<AskOutcome, AskError> {
        questions += question
        gate?.await()
        return answer(question)
    }
}

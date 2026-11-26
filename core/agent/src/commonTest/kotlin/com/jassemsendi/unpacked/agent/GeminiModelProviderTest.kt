package com.jassemsendi.unpacked.agent

import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.model.PromptExecutor
import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The cloud binding (spec story 39): the key comes from secure storage, and every refusal is typed. */
class GeminiModelProviderTest {
    private val keys = InMemorySecureKeyStore()
    private val model = ScriptedModel(says("cloud"))
    private val probes = mutableListOf<String>()

    private fun provider(probe: suspend (String) -> PromptExecutor = { model }) =
        GeminiModelProvider(keys) { key ->
            probes += key
            probe(key)
        }

    @Test
    fun withoutAKeyTheCloudIsOffAndNothingIsCalled() =
        runTest {
            assertEquals(Result.Error(AgentUnavailable.NoKey), provider().connect())
            assertEquals(emptyList(), probes)
        }

    @Test
    fun withAKeyTheModelIsGeminiFlashMarkedCloud() =
        runTest {
            keys.secrets[AgentSettings.GEMINI_KEY] = "AIza-example"

            val connected = provider().connect().orFail()

            assertEquals(ModelInfo(GeminiModelProvider.MODEL.id, ModelLocality.Cloud), connected.info)
            assertEquals(GeminiModelProvider.MODEL, connected.model)
            assertEquals(listOf("AIza-example"), probes)
        }

    @Test
    fun aKeyGoogleRejectsAndAnExhaustedQuotaAreSaidApart() =
        runTest {
            keys.secrets[AgentSettings.GEMINI_KEY] = "AIza-example"

            val rejected =
                provider { throw LLMClientException("Google", "Error", httpFailure(403)) }
            val exhausted =
                provider { throw LLMClientException("Google", "Error", httpFailure(429)) }

            assertEquals(Result.Error(AgentUnavailable.KeyRejected), rejected.connect())
            assertEquals(Result.Error(AgentUnavailable.QuotaExceeded), exhausted.connect())
        }

    @Test
    fun aCloudThatCannotBeReachedSaysSo() =
        runTest {
            keys.secrets[AgentSettings.GEMINI_KEY] = "AIza-example"

            val offline = provider { throw LLMClientException("Google", "Error from client: no route to host", null) }

            val reason = assertIs<Result.Error<AgentUnavailable>>(offline.connect()).error
            assertTrue(assertIs<AgentUnavailable.CloudUnreachable>(reason).message.contains("no route to host"))
        }

    @Test
    fun aKeyStoreThatCannotBeReadIsItsOwnReason() =
        runTest {
            keys.failure = KeyStoreError.NOT_AVAILABLE

            assertEquals(Result.Error(AgentUnavailable.KeyUnreadable), provider().connect())
        }
}

private fun httpFailure(status: Int) = KoogHttpClientException("g", status, "", "", null)

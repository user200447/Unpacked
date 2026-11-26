package com.jassemsendi.unpacked.agent

import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The cloud binding (spec story 39): the key comes from secure storage, and every refusal is typed. */
class GeminiModelProviderTest {
    private val keys = InMemorySecureKeyStore()
    private val model = ScriptedModel(says("cloud"))
    private val probes = mutableListOf<String>()

    private fun provider(
        probe: suspend (String) -> ConnectedCloud = { ConnectedCloud(GeminiModelProvider.MODEL, model) },
    ) = GeminiModelProvider(keys) { key ->
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
    fun theNewestFlashModelGoogleListsIsTheOneUsed() {
        val offered =
            listOf(
                "models/gemini-2.5-flash",
                "models/gemini-3.8-flash",
                "models/gemini-3.9-flash-lite",
                "models/gemini-3.1-pro-preview",
                "models/gemini-3.5-flash",
                "models/gemini-3.8-flash-preview",
                "models/text-embedding-004",
            )

        assertEquals("gemini-3.8-flash", chooseFlash(offered)?.id)
        assertEquals(GeminiModelProvider.MODEL.capabilities, chooseFlash(offered)?.capabilities)
        assertEquals("gemini-3.9-flash-lite", chooseFlash(listOf("gemini-3.9-flash-lite", "gemini-3-flash-lite"))?.id)
        assertNull(chooseFlash(listOf("gemini-3.1-pro", "text-embedding-004")))
        assertNull(chooseFlash(emptyList()))
    }

    @Test
    fun aKeyGoogleServesNoFlashModelToIsItsOwnReason() =
        runTest {
            keys.secrets[AgentSettings.GEMINI_KEY] = "AIza-example"

            val none = provider { throw NoFlashModelException(listOf("gemini-3.1-pro")) }
            val gone = provider { throw LLMClientException("Google", "Error", httpFailure(404)) }

            val reason = assertIs<Result.Error<AgentUnavailable>>(none.connect()).error
            assertTrue(assertIs<AgentUnavailable.NoCloudModel>(reason).detail.contains("gemini-3.1-pro"))
            assertIs<AgentUnavailable.NoCloudModel>(assertIs<Result.Error<AgentUnavailable>>(gone.connect()).error)
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

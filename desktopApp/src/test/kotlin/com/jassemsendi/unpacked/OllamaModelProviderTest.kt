package com.jassemsendi.unpacked

import com.jassemsendi.unpacked.agent.AgentUnavailable
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The desktop binding against a port nothing listens on: the HTTP stack resolves and the refusal is typed, not thrown. */
class OllamaModelProviderTest {
    @Test
    fun nothingListeningIsReportedAsOllamaNotRunning() =
        runTest {
            val provider = OllamaModelProvider(baseUrl = "http://127.0.0.1:1", requested = null)

            assertEquals(Result.Error(AgentUnavailable.OllamaNotRunning("http://127.0.0.1:1")), provider.connect())
        }
}

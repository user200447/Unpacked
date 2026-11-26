package com.jassemsendi.unpacked.agent

import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** What a cloud model's refusal means, read from the HTTP status wherever it sits in the cause chain. */
class CloudFailureTest {
    private fun http(status: Int?) = KoogHttpClientException("google", status, "body", "message", null)

    @Test
    fun tooManyRequestsIsTheQuota() {
        assertEquals(AgentUnavailable.QuotaExceeded, cloudFailure(http(429)))
    }

    @Test
    fun aRejectedKeyIsEveryClientSideRefusal() {
        for (status in listOf(400, 401, 403)) {
            assertEquals(AgentUnavailable.KeyRejected, cloudFailure(http(status)), "status $status")
        }
    }

    @Test
    fun theStatusIsFoundBehindTheClientsOwnWrapper() {
        val wrapped = LLMClientException("Google", "Error from client", http(429))

        assertEquals(AgentUnavailable.QuotaExceeded, cloudFailure(wrapped))
    }

    @Test
    fun notFoundIsTheModelGone() {
        assertIs<AgentUnavailable.NoCloudModel>(cloudFailure(http(404)))
    }

    @Test
    fun anythingElseIsNotARefusalOfTheKeyOrTheQuota() {
        assertNull(cloudFailure(http(500)))
        assertNull(cloudFailure(http(null)))
        assertNull(cloudFailure(IllegalStateException("connection reset")))
    }
}

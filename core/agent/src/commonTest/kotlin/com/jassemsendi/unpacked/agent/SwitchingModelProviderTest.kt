package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.model.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The provider the agent sees follows the Settings choice, asked anew at every connection. */
class SwitchingModelProviderTest {
    private val preferences = InMemoryPreferenceStore()
    private val keys = InMemorySecureKeyStore()
    private val local = FakeModelProvider(ScriptedModel(says("local")))
    private val cloud = FakeModelProvider(ScriptedModel(says("cloud")), locality = ModelLocality.Cloud)

    private fun provider(localOffered: Boolean = true): SwitchingModelProvider {
        val settings = AgentSettings(preferences, keys, localOffered)
        return SwitchingModelProvider(settings, if (localOffered) local else NoModelProvider, cloud)
    }

    @Test
    fun localGoesToThePlatformsOwnModel() =
        runTest {
            val connected = provider().connect().orFail()

            assertEquals(ModelLocality.Local, connected.info.locality)
            assertEquals(1, local.connections)
            assertEquals(0, cloud.connections)
        }

    @Test
    fun cloudGoesToGemini() =
        runTest {
            val provider = provider()
            AgentSettings(preferences, keys, localOffered = true).choose(ProviderChoice.Cloud)

            val connected = provider.connect().orFail()

            assertEquals(ModelLocality.Cloud, connected.info.locality)
            assertEquals(1, cloud.connections)
        }

    @Test
    fun offAnswersNobodyAndAsksNobody() =
        runTest {
            val provider = provider()
            AgentSettings(preferences, keys, localOffered = true).choose(ProviderChoice.Off)

            assertEquals(Result.Error(AgentUnavailable.Off), provider.connect())
            assertEquals(0, local.connections + cloud.connections)
        }

    @Test
    fun aPhoneWithNoKeyIsOffWithWhyAPhoneIsOff() =
        runTest {
            assertEquals(Result.Error(AgentUnavailable.Off), provider(localOffered = false).connect())
        }

    @Test
    fun theChoiceIsReadAgainForEveryConnection() =
        runTest {
            val provider = provider()
            val settings = AgentSettings(preferences, keys, localOffered = true)

            provider.connect().orFail()
            settings.choose(ProviderChoice.Cloud)
            provider.connect().orFail()

            assertEquals(1, local.connections)
            assertEquals(1, cloud.connections)
        }
}

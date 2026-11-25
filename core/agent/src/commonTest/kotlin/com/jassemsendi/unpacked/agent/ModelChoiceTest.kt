package com.jassemsendi.unpacked.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelChoiceTest {
    private val installed =
        listOf(
            InstalledModel("nomic-embed-text:latest", callsTools = false),
            InstalledModel("llama3.2:3b", callsTools = true),
            InstalledModel("qwen3:8b", callsTools = true),
            InstalledModel("gemma3:4b", callsTools = false),
        )

    @Test
    fun theRequestedModelWinsWhenItIsInstalledAndCallsTools() {
        assertEquals("llama3.2:3b", chooseModel(installed, PREFERRED_MODELS, requested = "llama3.2:3b"))
    }

    @Test
    fun aRequestedModelThatIsMissingOrCannotCallToolsFallsBackToThePreference() {
        assertEquals("qwen3:8b", chooseModel(installed, PREFERRED_MODELS, requested = "gemma3:4b"))
        assertEquals("qwen3:8b", chooseModel(installed, PREFERRED_MODELS, requested = "mistral:7b"))
    }

    @Test
    fun thePreferenceOrderDecidesAmongToolCapableModelsByFamily() {
        assertEquals("qwen3:8b", chooseModel(installed, PREFERRED_MODELS))
        assertEquals("llama3.2:3b", chooseModel(installed, listOf("llama3.2", "qwen3")))
    }

    @Test
    fun anyToolCapableModelBeatsNone() {
        val onlyOdd =
            listOf(InstalledModel("gemma3:4b", callsTools = false), InstalledModel("odd-model:1b", callsTools = true))

        assertEquals("odd-model:1b", chooseModel(onlyOdd, PREFERRED_MODELS))
    }

    @Test
    fun noToolCapableModelMeansNoChoice() {
        assertNull(chooseModel(installed.filter { !it.callsTools }, PREFERRED_MODELS))
        assertNull(chooseModel(emptyList(), PREFERRED_MODELS))
    }
}

package com.jassemsendi.unpacked.agent

/** A model a provider has installed, and whether it can call Tools; the only two facts the choice needs. */
public data class InstalledModel(
    val name: String,
    val callsTools: Boolean,
)

/**
 * Which installed model answers: the one the user named if it is installed and calls Tools, else the first of
 * [preferred] (by name or by family prefix, such as "qwen3" for "qwen3:8b") that does, else any that does. Null when
 * none calls Tools: the agent then stays off and says so. Pure, so the choice is tested without Ollama.
 */
public fun chooseModel(
    installed: List<InstalledModel>,
    preferred: List<String>,
    requested: String? = null,
): String? {
    val capable = installed.filter { it.callsTools }.map { it.name }
    val wanted = requested?.takeIf { it in capable }
    val liked =
        preferred.firstNotNullOfOrNull { family ->
            capable.firstOrNull { it == family || it.startsWith("$family:") || it.startsWith("$family-") }
        }
    return wanted ?: liked ?: capable.firstOrNull()
}

/** The families tried first, the ones that call Tools well enough to answer with citations on a laptop. */
public val PREFERRED_MODELS: List<String> = listOf("qwen3", "gpt-oss", "llama3.1", "llama3.2", "mistral", "qwen2.5")

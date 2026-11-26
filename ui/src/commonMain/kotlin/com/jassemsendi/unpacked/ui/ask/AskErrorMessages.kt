package com.jassemsendi.unpacked.ui.ask

import com.jassemsendi.unpacked.agent.AgentUnavailable
import com.jassemsendi.unpacked.agent.AskError

// The one place the agent's typed refusals become words (ADR 0009).

/** Why the agent is off, and what answers without it: the Insight cards on Home and the Gap report. */
fun AgentUnavailable.toMessage(): String =
    when (this) {
        AgentUnavailable.NoProvider -> {
            "The agent is off on this device: no model runs here yet. The Insight cards on Home and the Gap report " +
                "answer without one."
        }

        is AgentUnavailable.OllamaNotRunning -> {
            "The agent is off: nothing answers at Ollama ($baseUrl). Start Ollama with a model that calls tools, " +
                "such as “ollama run qwen3”, then check again. Meanwhile the Insight cards on Home and the Gap " +
                "report answer without a model."
        }

        is AgentUnavailable.NoToolModel -> {
            "The agent is off: Ollama answers at $baseUrl but none of its models calls tools" +
                (if (installed.isEmpty()) "" else " (installed: ${installed.joinToString()})") +
                ". Pull one that does, such as “ollama pull qwen3”, then check again. Meanwhile the Insight cards " +
                "on Home and the Gap report answer without a model."
        }
    }

/** Why a question got no outcome; an unavailable model is said where the model check says it. */
fun AskError.toMessage(): String =
    when (this) {
        is AskError.Unavailable -> reason.toMessage()
        AskError.BlankQuestion -> "Type a question first."
        is AskError.ModelFailed -> "The model stopped before answering: $message"
    }

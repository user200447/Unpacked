package com.jassemsendi.unpacked.ui.ask

import com.jassemsendi.unpacked.agent.AgentUnavailable
import com.jassemsendi.unpacked.agent.AskError

// The one place the agent's typed refusals become words (ADR 0009).

/** Why the agent is off, each reason in its own words (spec story 42), and what answers without it. */
fun AgentUnavailable.toMessage(): String =
    when (this) {
        AgentUnavailable.NoProvider -> {
            "The agent is off: no model runs on this device. To use the agent here, save a Gemini key in Settings " +
                "and choose the cloud model. $WITHOUT_A_MODEL"
        }

        is AgentUnavailable.OllamaNotRunning -> {
            "The agent is off: nothing answers at Ollama ($baseUrl), so it is not installed or it stopped. Start " +
                "Ollama with a model that calls tools, such as “ollama run qwen3”, then check again. $WITHOUT_A_MODEL"
        }

        is AgentUnavailable.NoToolModel -> {
            "The agent is off: Ollama answers at $baseUrl but none of its models calls tools" +
                (if (installed.isEmpty()) "" else " (installed: ${installed.joinToString()})") +
                ". Pull one that does, such as “ollama pull qwen3”, then check again. $WITHOUT_A_MODEL"
        }

        AgentUnavailable.Off -> {
            "The agent is off: you switched it off in Settings. $WITHOUT_A_MODEL"
        }

        AgentUnavailable.NoKey -> {
            "The agent is off: the cloud model is chosen but no Gemini key is saved. Paste one in Settings. " +
                WITHOUT_A_MODEL
        }

        AgentUnavailable.KeyUnreadable -> {
            "The agent is off: the Gemini key could not be read from this device's secure storage. Open Settings " +
                "and save it again. $WITHOUT_A_MODEL"
        }

        AgentUnavailable.KeyRejected -> {
            "The agent is off: Google rejected the Gemini key. Check it in Google AI Studio and save it again in " +
                "Settings. $WITHOUT_A_MODEL"
        }

        AgentUnavailable.QuotaExceeded -> {
            "The agent is off for now: the Gemini free tier's quota is exceeded. Wait a while, then check again. " +
                WITHOUT_A_MODEL
        }

        is AgentUnavailable.CloudUnreachable -> {
            "The agent is off: Gemini could not be reached ($message). Check the connection, then check again. " +
                WITHOUT_A_MODEL
        }
    }

private const val WITHOUT_A_MODEL = "Meanwhile the Insight cards on Home and the Gap report answer without a model."

/** Why a question got no outcome; an unavailable model is said where the model check says it. */
fun AskError.toMessage(): String =
    when (this) {
        is AskError.Unavailable -> {
            reason.toMessage()
        }

        AskError.BlankQuestion -> {
            "Type a question first."
        }

        AskError.PayloadRefused -> {
            "You kept the payload on this device; nothing was sent and the question was not answered."
        }

        is AskError.ModelFailed -> {
            "The model stopped before answering: $message"
        }
    }

package com.jassemsendi.unpacked.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.jassemsendi.unpacked.model.ClaimKind

/** The top-level screens, in the order a first-time user meets them, each with what it is opened on. */
sealed interface Screen {
    data object Welcome : Screen

    data object Import : Screen

    data object Home : Screen

    data object Search : Screen

    /** The Claims screen, with the group to open, or none. */
    data class Claims(
        val expanded: ClaimKind? = null,
    ) : Screen

    /** The Entities screen, opened on one Entity, say an advertiser named on an Insight card, or on none. */
    data class Entities(
        val opened: String? = null,
    ) : Screen

    companion object {
        /** Saved as a name and an argument, so the screen survives process death on every platform. */
        val saver: Saver<Screen, Any> = listSaver(save = { it.toSaved() }, restore = { it.toScreen() })

        private fun Screen.toSaved(): List<String> =
            when (this) {
                Welcome -> listOf("Welcome", "")
                Import -> listOf("Import", "")
                Home -> listOf("Home", "")
                Search -> listOf("Search", "")
                is Claims -> listOf("Claims", expanded?.name.orEmpty())
                is Entities -> listOf("Entities", opened.orEmpty())
            }

        /** A name no screen answers to any more, or a Kind renamed since, lands on Home. */
        private fun List<String>.toScreen(): Screen {
            val (name, argument) = this
            return when (name) {
                "Welcome" -> Welcome
                "Import" -> Import
                "Search" -> Search
                "Claims" -> Claims(ClaimKind.entries.firstOrNull { it.name == argument })
                "Entities" -> Entities(argument.ifEmpty { null })
                else -> Home
            }
        }
    }
}

package com.jassemsendi.unpacked.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.EntityKind

/** The top-level screens, in the order a first-time user meets them, each with what it is opened on. */
sealed interface Screen {
    data object Welcome : Screen

    data object Import : Screen

    data object Home : Screen

    /** Search, with text to search for at once, say a network address from an Insight card, or none. */
    data class Search(
        val text: String = "",
    ) : Screen

    /** The Claims screen, with the group to open, or none. */
    data class Claims(
        val expanded: ClaimKind? = null,
    ) : Screen

    /** The Entities screen, narrowed to one Kind or opened on one Entity, or neither. */
    data class Entities(
        val filter: EntityKind? = null,
        val opened: String? = null,
    ) : Screen

    companion object {
        /** Saved as a name and two strings, so the screen survives process death on every platform. */
        val saver: Saver<Screen, Any> = listSaver(save = { it.toSaved() }, restore = { it.toScreen() })

        private fun Screen.toSaved(): List<String> =
            when (this) {
                Welcome -> listOf("Welcome", "", "")
                Import -> listOf("Import", "", "")
                Home -> listOf("Home", "", "")
                is Search -> listOf("Search", text, "")
                is Claims -> listOf("Claims", expanded?.name.orEmpty(), "")
                is Entities -> listOf("Entities", filter?.name.orEmpty(), opened.orEmpty())
            }

        /** A name no screen answers to any more, or a Kind renamed since, lands on Home. */
        private fun List<String>.toScreen(): Screen {
            val (name, first, second) = this
            return when (name) {
                "Welcome" -> Welcome
                "Import" -> Import
                "Search" -> Search(first)
                "Claims" -> Claims(ClaimKind.entries.firstOrNull { it.name == first })
                "Entities" -> Entities(EntityKind.entries.firstOrNull { it.name == first }, second.ifEmpty { null })
                else -> Home
            }
        }
    }
}

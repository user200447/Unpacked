package com.jassemsendi.unpacked.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.ImportId

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

    /** The Gap report, over one Import, or over the latest when none is named. */
    data class GapReport(
        val importId: ImportId? = null,
    ) : Screen

    /** The Access request drafted from one Import's Gap report. */
    data class AccessRequest(
        val importId: ImportId,
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
                is GapReport -> listOf("GapReport", importId?.value?.toString().orEmpty())
                is AccessRequest -> listOf("AccessRequest", importId.value.toString())
            }

        /** A name no screen answers to any more, a Kind renamed since, or a letter with no Import, lands on Home. */
        private fun List<String>.toScreen(): Screen {
            val (name, argument) = this
            return when (name) {
                "Welcome" -> Welcome
                "Import" -> Import
                "Search" -> Search
                "Claims" -> Claims(ClaimKind.entries.firstOrNull { it.name == argument })
                "Entities" -> Entities(argument.ifEmpty { null })
                "GapReport" -> GapReport(argument.toLongOrNull()?.let(::ImportId))
                "AccessRequest" -> argument.toLongOrNull()?.let { AccessRequest(ImportId(it)) } ?: Home
                else -> Home
            }
        }
    }
}

package com.jassemsendi.unpacked.ingest.instagram

import com.jassemsendi.unpacked.ingest.Export
import com.jassemsendi.unpacked.model.Result

/**
 * Whose Export this is, as the Source names them in their own profile: the name every message
 * they sent carries as its sender, so the one thing that tells sent from received.
 */
internal data class ExportOwner(
    val name: String,
)

/** Finds the Export owner in the personal-information file, the Export's own statement of whose it is. */
internal object ExportOwners {
    private const val PROFILE_FILE = "personal_information.json"

    /** Null when the Export will not list, has no profile file, it cannot be read, or it names nobody. */
    fun of(export: Export): ExportOwner? =
        (export.files() as? Result.Success)
            ?.data
            .orEmpty()
            .filter { it.name.equals(PROFILE_FILE, ignoreCase = true) }
            .firstNotNullOfOrNull { file -> runCatching { export.readText(file) }.getOrNull()?.let(::ownerIn) }

    /** The profile's `Name`, which is what messages carry as the sender; the username only when there is no name. */
    private fun ownerIn(text: String): ExportOwner? =
        InstagramDecoder.decode(text).getOrNull()?.firstNotNullOfOrNull { record ->
            (record.fields["Name"].orBlank() ?: record.fields["Username"].orBlank())?.let(::ExportOwner)
        }
}

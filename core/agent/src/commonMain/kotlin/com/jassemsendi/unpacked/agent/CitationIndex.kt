package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.model.Provenance

/**
 * The citation markers of one question: every Provenance a Tool output cites gets a number the first time it is
 * seen, and keeps it, so the model can write `[3]` and the Ask screen can open the Record `[3]` names.
 */
internal class CitationIndex {
    private val markers = LinkedHashMap<Provenance, Int>()

    fun markerOf(provenance: Provenance): Int = markers.getOrPut(provenance) { markers.size + 1 }

    fun citationOf(marker: Int): Citation? =
        markers.entries.firstOrNull { it.value == marker }?.let { Citation(it.value, it.key) }

    /** The citations an answer text carries, resolved, in the order its markers first appear; unknown markers dropped. */
    fun citationsIn(text: String): List<Citation> = citationMarkersIn(text).mapNotNull(::citationOf)
}

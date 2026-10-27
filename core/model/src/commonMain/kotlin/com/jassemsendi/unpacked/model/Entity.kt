package com.jassemsendi.unpacked.model

/**
 * A named thing that recurs across Events and Claims. Owned by exactly one Source (ADR 0002);
 * identity within that Source is `source:kind:key`.
 */
public data class Entity(
    val source: Source,
    val kind: EntityKind,
    val key: String,
    val displayName: String,
    val attributes: Map<String, String> = emptyMap(),
) {
    val id: String get() = "${source.id}:${kind.name.lowercase()}:$key"

    public companion object {
        private val whitespace = Regex("\\s+")

        /** The key to use when a Source gives no stable identifier of its own. */
        public fun normalizeKey(displayName: String): String = displayName.trim().lowercase().replace(whitespace, " ")
    }
}

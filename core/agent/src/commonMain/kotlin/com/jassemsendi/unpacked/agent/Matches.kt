package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.model.Entity

/**
 * The Match rule (ADR 0002): two Entities of the same Kind from different Sources are a Match when their display
 * names agree once trimmed, lowercased and with their whitespace collapsed, which is [Entity.normalizeKey].
 * Pure and computed when asked, so a better rule changes answers without touching the Library; Entities of one
 * Source never match each other, however alike their names, because one Source already told them apart.
 * Matches come by normalised name, then by the first Entity's id, then the second's.
 */
public fun matchesAmong(entities: List<Entity>): List<Match> =
    entities
        .groupBy { Entity.normalizeKey(it.displayName) to it.kind }
        .flatMap { (key, alike) ->
            val ordered = alike.sortedWith(compareBy<Entity> { it.source.ordinal }.thenBy { it.id })
            ordered.flatMapIndexed { index, first ->
                ordered
                    .drop(index + 1)
                    .filter { second -> second.source != first.source }
                    .map { second -> Match(key.first, EntityRef.of(first), EntityRef.of(second)) }
            }
        }.sortedWith(compareBy<Match> { it.normalizedName }.thenBy { it.first.id }.thenBy { it.second.id })

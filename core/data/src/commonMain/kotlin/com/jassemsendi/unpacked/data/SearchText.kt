package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.data.db.ClaimTextRow
import com.jassemsendi.unpacked.data.db.EventTextRow
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.Event

// What a search reads of a record, and what the typed text becomes before SQLite sees it.

/**
 * Anything that is not a letter, a digit or a combining mark ends a word. A mark stays with its letter so
 * that an accent typed as a separate code point reaches the tokenizer as part of the word, not as a word break.
 */
private val wordBreak = Regex("[^\\p{L}\\p{M}\\p{N}]+")

/** The words of an Event or Claim a search can find: its attribute values and the names of its Entities, never its labels. */
private fun searchTextOf(
    attributes: Map<String, String>,
    entities: List<Entity>,
): String = (attributes.values + entities.map { it.displayName }).filter { it.isNotBlank() }.joinToString("\n")

/** Null when the Event has no words to find. */
internal fun Event.searchRow(): EventTextRow? =
    searchTextOf(attributes, entities).takeIf { it.isNotEmpty() }?.let { EventTextRow(id, it) }

/** Null when the Claim has no words to find. */
internal fun Claim.searchRow(): ClaimTextRow? =
    searchTextOf(attributes, entities).takeIf { it.isNotEmpty() }?.let { ClaimTextRow(id, it) }

/**
 * The FTS4 expression for what the user typed, or null when they typed no word. Each word becomes a
 * quoted prefix term, so "cour" finds "courier", every word is required, and the characters FTS4
 * would read as operators (quotes, stars, dashes, colons, OR) are only ever word breaks or words.
 */
internal fun matchExpression(text: String): String? =
    text
        .split(wordBreak)
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ") { "\"$it*\"" }

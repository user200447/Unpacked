package com.jassemsendi.unpacked.ui.entities

import com.jassemsendi.unpacked.data.EntityOrder
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.ui.home.AttributeUi
import com.jassemsendi.unpacked.ui.home.KindCountUi
import com.jassemsendi.unpacked.ui.home.RecordDetailUi
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.RecordRowUi

data class EntitiesState(
    /** True from the start until the Kind counts and the first page have been read, and again during a refresh. */
    val isLoading: Boolean = true,
    /** Entities in the whole library, before any filter. */
    val totalCount: Int = 0,
    /** Every Kind with at least one Entity, in Kind order, each with its count. */
    val kinds: List<KindCountUi<EntityKind>> = emptyList(),
    /** The Kind the list is narrowed to, or null for every Kind. */
    val filter: EntityKind? = null,
    val order: EntityOrder = EntityOrder.MostNamed,
    /** The Entities read so far, in [order]. */
    val entities: List<EntityRowUi> = emptyList(),
    /** True while the repository holds Entities beyond [entities] for the current filter. */
    val hasMore: Boolean = false,
    val isReadingNextPage: Boolean = false,
    /** The Entity opened from the list, with its own timeline and Claims, or null while the list is shown. */
    val opened: EntityDetailUi? = null,
    /** The Record opened from the Entity's Events or Claims, if any. */
    val detail: RecordDetailUi? = null,
    /** Why the last read failed, until the next one succeeds; what is listed is the last successful read. */
    val error: String? = null,
)

/** One Entity as the list shows it: its name, its Kind, the Source it belongs to and how often it is named. */
data class EntityRowUi(
    val id: String,
    val name: String,
    val kindLabel: String,
    /** Entities are per Source and never merged (ADR 0002), so the Source is shown on every row. */
    val sourceName: String,
    val eventCount: Int,
    val claimCount: Int,
)

/** An Entity opened from the list: what the Source recorded about it, the Events naming it in pages, and its Claims. */
data class EntityDetailUi(
    val entity: EntityRowUi,
    val attributes: List<AttributeUi>,
    /** The Events naming the Entity read so far, newest first. */
    val events: List<RecordRowUi>,
    /** True while the repository counts more Events for the Entity than are listed. */
    val hasMoreEvents: Boolean,
    val isReadingMoreEvents: Boolean = false,
    /** Every Claim naming the Entity: dated ones newest first, then undated ones in file order. */
    val claims: List<RecordRowUi>,
)

sealed interface EntitiesAction {
    /** Re-read the counts, the list and the opened Entity; the screen sends it whenever it appears. */
    data object Refresh : EntitiesAction

    data class Filter(
        val kind: EntityKind?,
    ) : EntitiesAction

    data class Sort(
        val order: EntityOrder,
    ) : EntitiesAction

    /** Read the page of Entities after the last one listed. */
    data object NextPage : EntitiesAction

    data class OpenEntity(
        val id: String,
    ) : EntitiesAction

    data object CloseEntity : EntitiesAction

    /** Read the page of the opened Entity's Events after the last one listed. */
    data object MoreEvents : EntitiesAction

    data class Open(
        val ref: RecordRef,
    ) : EntitiesAction

    data object CloseDetail : EntitiesAction
}

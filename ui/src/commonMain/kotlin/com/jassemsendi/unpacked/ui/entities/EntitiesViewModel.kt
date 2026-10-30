package com.jassemsendi.unpacked.ui.entities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EntityOrder
import com.jassemsendi.unpacked.data.EntityRepository
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.andThen
import com.jassemsendi.unpacked.model.map
import com.jassemsendi.unpacked.ui.home.KindCountUi
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.readRecord
import com.jassemsendi.unpacked.ui.home.toAttributes
import com.jassemsendi.unpacked.ui.home.toRow
import com.jassemsendi.unpacked.ui.home.toUi
import com.jassemsendi.unpacked.ui.toMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The Entities screen: the named things the library's Events and Claims refer to, counted and in pages,
 * narrowed to one Kind or sorted by name on request; one opened to its own timeline and Claims; and the
 * Record opened from those. Nothing is read until the screen asks for an [EntitiesAction.Refresh].
 */
class EntitiesViewModel(
    private val entities: EntityRepository,
    private val events: EventRepository,
    private val claims: ClaimRepository,
    private val timeZone: TimeZone,
    private val pageSize: Int = PAGE_SIZE,
) : ViewModel() {
    private val _state = MutableStateFlow(EntitiesState())
    val state = _state.asStateFlow()

    /** Bumped by every reload so a page that was in flight for an older filter or order is dropped on arrival. */
    private var generation = 0

    /** Bumped by every Entity opened or closed so a read that was in flight for an earlier one is dropped on arrival. */
    private var opening = 0

    /** How many Entities the repository has handed over for the current list, which is where the next page starts. */
    private var offset = 0

    /** How many Events the repository has handed over for the opened Entity, which is where its next page starts. */
    private var eventOffset = 0

    /** The Kind counts and the first page, read together so the screen never shows one without the other. */
    private data class FirstPage(
        val kinds: List<KindCountUi<EntityKind>>,
        val filter: EntityKind?,
        val entities: List<EntityRowUi>,
    )

    fun onAction(action: EntitiesAction) {
        val before = _state.value
        when (action) {
            // The list and the opened Entity are re-read side by side; an Entity that is gone closes, the list stays.
            EntitiesAction.Refresh -> {
                reload(before.filter, before.order)
                before.opened?.let { openEntity(it.entity.id) }
            }

            is EntitiesAction.Filter -> {
                reload(action.kind, before.order)
            }

            is EntitiesAction.Sort -> {
                reload(before.filter, action.order)
            }

            EntitiesAction.NextPage -> {
                nextPage()
            }

            is EntitiesAction.OpenEntity -> {
                openEntity(action.id)
            }

            EntitiesAction.CloseEntity -> {
                opening++
                _state.update { it.copy(opened = null, detail = null) }
            }

            EntitiesAction.MoreEvents -> {
                moreEvents()
            }

            is EntitiesAction.Open -> {
                open(action.ref)
            }

            EntitiesAction.CloseDetail -> {
                _state.update { it.copy(detail = null) }
            }
        }
    }

    private fun reload(
        filter: EntityKind?,
        order: EntityOrder,
    ) {
        val current = ++generation
        // A new filter or order empties the list at once; a plain refresh keeps the last read until the new one lands.
        _state.update {
            if (filter == it.filter && order == it.order) {
                it.copy(isLoading = true)
            } else {
                it.copy(isLoading = true, filter = filter, order = order, entities = emptyList(), hasMore = false)
            }
        }
        viewModelScope.launch {
            val read = readFirstPage(filter, order)
            if (current != generation) return@launch
            _state.update { state ->
                when (read) {
                    is Result.Success -> {
                        val page = read.data
                        offset = page.entities.size
                        state
                            .copy(
                                isLoading = false,
                                error = null,
                                filter = page.filter,
                                totalCount = page.kinds.sumOf { it.count },
                                kinds = page.kinds,
                                entities = page.entities,
                            ).withHasMore()
                    }

                    is Result.Error -> {
                        state.copy(isLoading = false, error = read.error.toMessage())
                    }
                }
            }
        }
    }

    private suspend fun readFirstPage(
        requested: EntityKind?,
        order: EntityOrder,
    ): Result<FirstPage, DataError.Local> =
        when (val counted = entities.countByKind()) {
            is Result.Error -> {
                counted
            }

            is Result.Success -> {
                val counts = counted.data
                val kinds = EntityKind.entries.mapNotNull { kind -> counts[kind]?.let { kind.toUi(it) } }
                // A Kind the library no longer holds, say after an Import was deleted, is no filter to keep.
                val filter = requested?.takeIf { it in counts }
                entities.list(filter, order, pageSize, 0).map { page ->
                    FirstPage(kinds, filter, page.map { it.toRow() })
                }
            }
        }

    private fun nextPage() {
        val before = _state.value
        if (before.isLoading || before.isReadingNextPage || !before.hasMore) return
        val current = generation
        _state.update { it.copy(isReadingNextPage = true) }
        viewModelScope.launch {
            val read = entities.list(before.filter, before.order, pageSize, offset)
            if (current != generation) return@launch
            _state.update { state ->
                when (read) {
                    is Result.Success -> {
                        // An Import landing meanwhile shifts the offsets; an Entity read twice is listed once.
                        offset += read.data.size
                        val rows = (state.entities + read.data.map { it.toRow() }).distinctBy { it.id }
                        state.copy(isReadingNextPage = false, error = null, entities = rows).withHasMore()
                    }

                    // The spinner would otherwise ask again at once; the next Refresh offers the rest again.
                    is Result.Error -> {
                        state.copy(isReadingNextPage = false, hasMore = false, error = read.error.toMessage())
                    }
                }
            }
        }
    }

    /** More to read while the repository counts more Entities for the filter than are listed. */
    private fun EntitiesState.withHasMore(): EntitiesState {
        val visible = if (filter == null) totalCount else kinds.first { it.kind == filter }.count
        return copy(hasMore = entities.size < visible)
    }

    private fun openEntity(id: String) {
        val current = ++opening
        viewModelScope.launch {
            val read = readEntity(id)
            if (current != opening) return@launch
            _state.update {
                when (read) {
                    is Result.Success -> {
                        eventOffset = read.data.events.size
                        it.copy(opened = read.data, error = null)
                    }

                    // An Entity that is gone, say with its Import, closes; the list is what the next Refresh re-reads.
                    is Result.Error -> {
                        it.copy(opened = null, detail = null, error = read.error.toMessage())
                    }
                }
            }
        }
    }

    /** The Entity, its first page of Events and every Claim, read together so the pane never shows one without the others. */
    private suspend fun readEntity(id: String): Result<EntityDetailUi, DataError.Local> =
        entities.counts(id).andThen { references ->
            events.forEntity(id, pageSize, 0).andThen { firstEvents ->
                claims.forEntity(id).map { claimRows ->
                    EntityDetailUi(
                        entity = references.toRow(),
                        attributes = references.entity.attributes.toAttributes(),
                        events = firstEvents.map { it.toRow(timeZone) },
                        hasMoreEvents = firstEvents.size < references.eventCount,
                        claims = claimRows.map { it.toRow(timeZone) },
                    )
                }
            }
        }

    private fun moreEvents() {
        val opened = _state.value.opened ?: return
        if (opened.isReadingMoreEvents || !opened.hasMoreEvents) return
        val current = opening
        _state.update { it.copy(opened = opened.copy(isReadingMoreEvents = true)) }
        viewModelScope.launch {
            val read = events.forEntity(opened.entity.id, pageSize, eventOffset)
            // The pane may have moved on, or re-read this Entity, meanwhile; a page for what it showed before is dropped.
            if (current != opening) return@launch
            _state.update { state ->
                val shown = state.opened ?: return@update state
                when (read) {
                    is Result.Success -> {
                        eventOffset += read.data.size
                        val rows = (shown.events + read.data.map { it.toRow(timeZone) }).distinctBy { it.ref }
                        val more = rows.size < shown.entity.eventCount
                        state.copy(
                            opened = shown.copy(events = rows, hasMoreEvents = more, isReadingMoreEvents = false),
                            error = null,
                        )
                    }

                    is Result.Error -> {
                        state.copy(
                            opened = shown.copy(hasMoreEvents = false, isReadingMoreEvents = false),
                            error = read.error.toMessage(),
                        )
                    }
                }
            }
        }
    }

    private fun open(ref: RecordRef) {
        viewModelScope.launch {
            val read = readRecord(ref, events, claims, timeZone)
            _state.update {
                when (read) {
                    is Result.Success -> it.copy(detail = read.data, error = null)
                    is Result.Error -> it.copy(detail = null, error = read.error.toMessage())
                }
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}

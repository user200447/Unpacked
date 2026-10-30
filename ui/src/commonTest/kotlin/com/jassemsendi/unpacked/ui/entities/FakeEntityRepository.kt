package com.jassemsendi.unpacked.ui.entities

import com.jassemsendi.unpacked.data.EntityOrder
import com.jassemsendi.unpacked.data.EntityReferences
import com.jassemsendi.unpacked.data.EntityRepository
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.ui.home.FakeClaimRepository
import com.jassemsendi.unpacked.ui.home.FakeEventRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * The Entities the Home fakes' Events and Claims name, counted and ordered the way the Room repository
 * answers. A test that wants to see the state while a read is in flight holds [gate]; one that wants every
 * read refused sets [failure].
 */
class FakeEntityRepository(
    private val events: FakeEventRepository,
    private val claims: FakeClaimRepository,
) : EntityRepository {
    var gate: CompletableDeferred<Unit>? = null
    var failure: DataError.Local? = null

    private val entities: List<Entity>
        get() = (events.events.flatMap { it.entities } + claims.claims.flatMap { it.entities }).distinctBy { it.id }

    private val referenced: List<EntityReferences>
        get() =
            entities.map { entity ->
                EntityReferences(
                    entity,
                    eventCount = events.events.count { event -> event.entities.any { it.id == entity.id } },
                    claimCount = claims.claims.count { claim -> claim.entities.any { it.id == entity.id } },
                )
            }

    override suspend fun byId(id: String): Result<Entity, DataError.Local> =
        answer { entities.find { it.id == id } }.orNotFound()

    override suspend fun all(): Result<List<Entity>, DataError.Local> = answer { entities.sortedBy { it.id } }

    override suspend fun countByKind(): Result<Map<EntityKind, Int>, DataError.Local> {
        gate?.await()
        return answer { entities.groupingBy { it.kind }.eachCount() }
    }

    override suspend fun list(
        kind: EntityKind?,
        order: EntityOrder,
        limit: Int,
        offset: Int,
    ): Result<List<EntityReferences>, DataError.Local> {
        gate?.await()
        val byName = compareBy<EntityReferences> { it.entity.displayName.lowercase() }.thenBy { it.entity.id }
        val ordering =
            when (order) {
                EntityOrder.MostReferenced -> compareByDescending<EntityReferences> { it.total }.then(byName)
                EntityOrder.Name -> byName
            }
        return answer {
            referenced
                .filter { kind == null || it.entity.kind == kind }
                .sortedWith(ordering)
                .drop(offset)
                .take(limit)
        }
    }

    override suspend fun references(id: String): Result<EntityReferences, DataError.Local> =
        answer { referenced.find { it.entity.id == id } }.orNotFound()

    private fun <T> answer(read: () -> T): Result<T, DataError.Local> =
        failure?.let { Result.Error(it) } ?: Result.Success(read())

    private fun <T : Any> Result<T?, DataError.Local>.orNotFound(): Result<T, DataError.Local> =
        when (this) {
            is Result.Error -> this
            is Result.Success -> data?.let { Result.Success(it) } ?: Result.Error(DataError.Local.NOT_FOUND)
        }
}

package com.jassemsendi.unpacked.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
internal interface ImportDao {
    @Insert
    suspend fun insert(row: ImportRow): Long

    @Query("UPDATE imports SET completed_at = :completedAt WHERE id = :id")
    suspend fun complete(
        id: Long,
        completedAt: Long,
    )

    @Query("SELECT * FROM imports ORDER BY started_at, id")
    suspend fun all(): List<ImportRow>

    @Query("SELECT * FROM imports WHERE id = :id")
    suspend fun byId(id: Long): ImportRow?

    @Query("DELETE FROM imports WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertFile(row: ImportFileRow)

    @Query("SELECT * FROM import_files WHERE import_id IN (:importIds) ORDER BY import_id, position")
    suspend fun files(importIds: List<Long>): List<ImportFileRow>
}

@Dao
internal interface EventDao {
    @Insert
    suspend fun insert(rows: List<EventRow>)

    @Insert
    suspend fun insertAttributes(rows: List<EventAttributeRow>)

    @Insert
    suspend fun insertLinks(rows: List<EventEntityRow>)

    /** Cascades to the attributes, Entity links and search text of those Events. */
    @Query("DELETE FROM events WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    /** The whole library when [importId] is null. */
    @Query("SELECT kind, COUNT(*) AS count FROM events WHERE :importId IS NULL OR import_id = :importId GROUP BY kind")
    suspend fun countByKind(importId: Long?): List<KindCount>

    @Query("SELECT * FROM events WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<EventRow>

    @Query("SELECT * FROM events WHERE import_id = :importId ORDER BY time, id")
    suspend fun forImport(importId: Long): List<EventRow>

    /** Every Kind when [kind] is null. */
    @Query(
        """
        SELECT * FROM events
        WHERE :kind IS NULL OR kind = :kind
        ORDER BY time DESC, id DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun newestFirst(
        kind: String?,
        limit: Int,
        offset: Int,
    ): List<EventRow>

    @Query("SELECT * FROM event_attributes WHERE event_id IN (:ids) ORDER BY event_id, position")
    suspend fun attributes(ids: List<Long>): List<EventAttributeRow>

    @Query("SELECT * FROM event_entities WHERE event_id IN (:ids) ORDER BY event_id, position")
    suspend fun links(ids: List<Long>): List<EventEntityRow>
}

@Dao
internal interface ClaimDao {
    @Insert
    suspend fun insert(rows: List<ClaimRow>)

    @Insert
    suspend fun insertAttributes(rows: List<ClaimAttributeRow>)

    @Insert
    suspend fun insertLinks(rows: List<ClaimEntityRow>)

    /** Cascades to the attributes, Entity links and search text of those Claims. */
    @Query("DELETE FROM claims WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    /** The whole library when [importId] is null. */
    @Query("SELECT kind, COUNT(*) AS count FROM claims WHERE :importId IS NULL OR import_id = :importId GROUP BY kind")
    suspend fun countByKind(importId: Long?): List<KindCount>

    @Query("SELECT * FROM claims WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<ClaimRow>

    @Query("SELECT * FROM claims WHERE import_id = :importId ORDER BY as_of, path, record_index, item_index")
    suspend fun forImport(importId: Long): List<ClaimRow>

    @Query("SELECT * FROM claim_attributes WHERE claim_id IN (:ids) ORDER BY claim_id, position")
    suspend fun attributes(ids: List<Long>): List<ClaimAttributeRow>

    @Query("SELECT * FROM claim_entities WHERE claim_id IN (:ids) ORDER BY claim_id, position")
    suspend fun links(ids: List<Long>): List<ClaimEntityRow>
}

@Dao
internal interface EntityDao {
    @Insert
    suspend fun insert(rows: List<EntityRow>)

    /** The latest Import to mention an Entity wins its display name. */
    @Update
    suspend fun update(rows: List<EntityRow>)

    @Query("DELETE FROM entity_attributes WHERE entity_id IN (:ids)")
    suspend fun deleteAttributes(ids: List<String>)

    @Insert
    suspend fun insertAttributes(rows: List<EntityAttributeRow>)

    @Query("SELECT * FROM entities WHERE id = :id")
    suspend fun byId(id: String): EntityRow?

    @Query("SELECT * FROM entities WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<EntityRow>

    @Query("SELECT * FROM entities ORDER BY id")
    suspend fun all(): List<EntityRow>

    @Query("SELECT * FROM entity_attributes WHERE entity_id IN (:ids) ORDER BY entity_id, position")
    suspend fun attributes(ids: List<String>): List<EntityAttributeRow>

    /** Entities no Event or Claim refers to any more, typically after an Import was deleted. */
    @Query(
        """
        DELETE FROM entities
        WHERE id NOT IN (SELECT entity_id FROM event_entities)
          AND id NOT IN (SELECT entity_id FROM claim_entities)
        """,
    )
    suspend fun deleteOrphans()
}

/**
 * The full-text index over Events and Claims (ADR 0004). Only the text tables are written; Room's
 * triggers fill the FTS tables from them, and the cascade from a deleted record empties both.
 */
@Dao
internal interface SearchDao {
    @Insert
    suspend fun insertEvents(rows: List<EventTextRow>)

    @Insert
    suspend fun insertClaims(rows: List<ClaimTextRow>)

    /**
     * One page of the Events and Claims whose text matches [match], an FTS4 expression: newest first by an
     * Event's time or a Claim's "as of" time, undated Claims last, ties in a fixed order so pages never
     * overlap. The literals are the names of [HitType].
     */
    @Query(
        """
        SELECT * FROM (
            SELECT 'EVENT' AS hit_type, id AS hit_id, time FROM events
            WHERE id IN (SELECT rowid FROM event_search WHERE event_search MATCH :match)
            UNION ALL
            SELECT 'CLAIM' AS hit_type, id AS hit_id, as_of AS time FROM claims
            WHERE id IN (SELECT rowid FROM claim_search WHERE claim_search MATCH :match)
        )
        ORDER BY time IS NULL, time DESC, hit_type, hit_id DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun hits(
        match: String,
        limit: Int,
        offset: Int,
    ): List<SearchHitRow>
}

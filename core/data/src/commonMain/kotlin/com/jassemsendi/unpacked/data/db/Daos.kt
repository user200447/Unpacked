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

    /** Cascades to the attributes and Entity links of those Events. */
    @Query("DELETE FROM events WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT kind, COUNT(*) AS count FROM events GROUP BY kind")
    suspend fun countByKind(): List<KindCount>

    @Query("SELECT kind, COUNT(*) AS count FROM events WHERE import_id = :importId GROUP BY kind")
    suspend fun countByKindForImport(importId: Long): List<KindCount>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun byId(id: Long): EventRow?

    @Query("SELECT * FROM events WHERE import_id = :importId ORDER BY time, id")
    suspend fun forImport(importId: Long): List<EventRow>

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

    /** Cascades to the attributes and Entity links of those Claims. */
    @Query("DELETE FROM claims WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT kind, COUNT(*) AS count FROM claims GROUP BY kind")
    suspend fun countByKind(): List<KindCount>

    @Query("SELECT kind, COUNT(*) AS count FROM claims WHERE import_id = :importId GROUP BY kind")
    suspend fun countByKindForImport(importId: Long): List<KindCount>

    @Query("SELECT * FROM claims WHERE id = :id")
    suspend fun byId(id: Long): ClaimRow?

    @Query("SELECT * FROM claims WHERE import_id = :importId ORDER BY as_of, id")
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

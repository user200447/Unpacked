package com.jassemsendi.unpacked.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

// Room's view of the library (ADR 0005). Instants are epoch milliseconds; Kinds and Sources are stored
// by name; the attribute maps of Events, Claims and Entities live in typed side tables that keep the
// label order of the record they came from.

@Entity(tableName = "imports")
internal data class ImportRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "export_id") val exportId: String,
    val source: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "json_files") val jsonFiles: Int,
    @ColumnInfo(name = "media_files") val mediaFiles: Int,
    @ColumnInfo(name = "other_files") val otherFiles: Int,
)

@Entity(
    tableName = "import_files",
    primaryKeys = ["import_id", "path"],
    foreignKeys = [
        ForeignKey(
            entity = ImportRow::class,
            parentColumns = ["id"],
            childColumns = ["import_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ImportFileRow(
    @ColumnInfo(name = "import_id") val importId: Long,
    val path: String,
    @ColumnInfo(name = "raw_kind") val rawKind: String,
    /** Visit order within the Import, so the file list reads back in the order it was reported. */
    val position: Int,
    val status: String,
    @ColumnInfo(name = "record_count") val recordCount: Int,
    @ColumnInfo(name = "event_count") val eventCount: Int,
    @ColumnInfo(name = "claim_count") val claimCount: Int,
    @ColumnInfo(name = "dropped_count") val droppedCount: Int,
    /** Why a Skipped or Failed file was not parsed, or why a Parsed file's records were dropped; null otherwise. */
    val reason: String?,
) {
    companion object {
        const val PARSED = "parsed"
        const val SKIPPED = "skipped"
        const val FAILED = "failed"
    }
}

/** A side-table row that belongs to one Event or Claim. */
internal interface OwnedByRecord {
    val recordId: Long
}

/** A join-table row linking one Event or Claim to one Entity, in [position] order. */
internal interface EntityLink : OwnedByRecord {
    val entityId: String
    val position: Int
}

/** The Provenance columns shared by Events and Claims. */
internal data class ProvenanceColumns(
    @ColumnInfo(name = "export_id") val exportId: String,
    val path: String,
    @ColumnInfo(name = "record_index") val recordIndex: Int,
    @ColumnInfo(name = "item_index") val itemIndex: Int,
)

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = ImportRow::class,
            parentColumns = ["id"],
            childColumns = ["import_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("import_id"), Index("kind"), Index("time")],
)
internal data class EventRow(
    /** The Provenance hash, so the same record in a re-imported Export lands on the same row. */
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "import_id") val importId: Long,
    val kind: String,
    @ColumnInfo(name = "raw_kind") val rawKind: String,
    val time: Long,
    @Embedded val provenance: ProvenanceColumns,
)

@Entity(
    tableName = "event_attributes",
    primaryKeys = ["event_id", "label"],
    foreignKeys = [
        ForeignKey(
            entity = EventRow::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class EventAttributeRow(
    @ColumnInfo(name = "event_id") override val recordId: Long,
    val position: Int,
    val label: String,
    val value: String,
) : OwnedByRecord

@Entity(
    tableName = "event_entities",
    primaryKeys = ["event_id", "entity_id"],
    foreignKeys = [
        ForeignKey(
            entity = EventRow::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EntityRow::class,
            parentColumns = ["id"],
            childColumns = ["entity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entity_id")],
)
internal data class EventEntityRow(
    @ColumnInfo(name = "event_id") override val recordId: Long,
    @ColumnInfo(name = "entity_id") override val entityId: String,
    override val position: Int,
) : EntityLink

@Entity(
    tableName = "claims",
    foreignKeys = [
        ForeignKey(
            entity = ImportRow::class,
            parentColumns = ["id"],
            childColumns = ["import_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("import_id"), Index("kind")],
)
internal data class ClaimRow(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "import_id") val importId: Long,
    val kind: String,
    @ColumnInfo(name = "raw_kind") val rawKind: String,
    @ColumnInfo(name = "as_of") val asOf: Long?,
    @Embedded val provenance: ProvenanceColumns,
)

@Entity(
    tableName = "claim_attributes",
    primaryKeys = ["claim_id", "label"],
    foreignKeys = [
        ForeignKey(
            entity = ClaimRow::class,
            parentColumns = ["id"],
            childColumns = ["claim_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ClaimAttributeRow(
    @ColumnInfo(name = "claim_id") override val recordId: Long,
    val position: Int,
    val label: String,
    val value: String,
) : OwnedByRecord

@Entity(
    tableName = "claim_entities",
    primaryKeys = ["claim_id", "entity_id"],
    foreignKeys = [
        ForeignKey(
            entity = ClaimRow::class,
            parentColumns = ["id"],
            childColumns = ["claim_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EntityRow::class,
            parentColumns = ["id"],
            childColumns = ["entity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entity_id")],
)
internal data class ClaimEntityRow(
    @ColumnInfo(name = "claim_id") override val recordId: Long,
    @ColumnInfo(name = "entity_id") override val entityId: String,
    override val position: Int,
) : EntityLink

/** One row per `source:kind:key` (ADR 0002), shared by every Import that mentions the Entity. */
@Entity(tableName = "entities", indices = [Index("kind")])
internal data class EntityRow(
    @PrimaryKey val id: String,
    val source: String,
    val kind: String,
    @ColumnInfo(name = "entity_key") val key: String,
    @ColumnInfo(name = "display_name") val displayName: String,
)

@Entity(
    tableName = "entity_attributes",
    primaryKeys = ["entity_id", "label"],
    foreignKeys = [
        ForeignKey(
            entity = EntityRow::class,
            parentColumns = ["id"],
            childColumns = ["entity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class EntityAttributeRow(
    @ColumnInfo(name = "entity_id") val entityId: String,
    val position: Int,
    val label: String,
    val value: String,
)

/**
 * The words of one Event that a search can find: its attribute values and the names of its Entities, under
 * the Event's own id so it goes when the Event goes. Room keeps [EventSearchRow] in step with this table
 * through triggers, so writing a row here is all an Import does for search.
 */
@Entity(
    tableName = "event_text",
    foreignKeys = [
        ForeignKey(
            entity = EventRow::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class EventTextRow(
    /** An INTEGER primary key is SQLite's rowid, which is the docid the FTS table files the text under. */
    @PrimaryKey @ColumnInfo(name = "event_id") val eventId: Long,
    val text: String,
)

/** The words of one Claim that a search can find; see [EventTextRow]. */
@Entity(
    tableName = "claim_text",
    foreignKeys = [
        ForeignKey(
            entity = ClaimRow::class,
            parentColumns = ["id"],
            childColumns = ["claim_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ClaimTextRow(
    @PrimaryKey @ColumnInfo(name = "claim_id") val claimId: Long,
    val text: String,
)

/**
 * The FTS4 index over [EventTextRow] (ADR 0004), queried by `rowid`, which is the Event's id. The unicode61
 * tokenizer folds case and accents, so "zoe" finds "Zoë". An external-content table rather than a
 * standalone one because Room's native processor cannot read the latter's default content marker.
 */
@Fts4(contentEntity = EventTextRow::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "event_search")
internal data class EventSearchRow(
    val text: String,
)

/** The FTS4 index over [ClaimTextRow]; see [EventSearchRow]. */
@Fts4(contentEntity = ClaimTextRow::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "claim_search")
internal data class ClaimSearchRow(
    val text: String,
)

/** Which table a search hit lives in; stored by name, which is what the search query selects as a literal. */
internal enum class HitType {
    EVENT,
    CLAIM,
}

/** One search hit before the Event or Claim is read back in full: its table, its id, and the time it sorts by. */
internal data class SearchHitRow(
    @ColumnInfo(name = "hit_type") val hitType: HitType,
    @ColumnInfo(name = "hit_id") val hitId: Long,
    val time: Long?,
)

/** An Entity row with how many Events and Claims link to it, as the reference queries select them. */
internal data class EntityReferenceRow(
    @Embedded val entity: EntityRow,
    @ColumnInfo(name = "event_count") val eventCount: Int,
    @ColumnInfo(name = "claim_count") val claimCount: Int,
)

/** One row of a `GROUP BY kind` count. */
internal data class KindCount(
    val kind: String,
    val count: Int,
)

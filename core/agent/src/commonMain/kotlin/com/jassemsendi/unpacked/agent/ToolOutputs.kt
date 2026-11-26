package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.insights.Evidence
import com.jassemsendi.unpacked.insights.GapReport
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.Source
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * What one Tool answers with: typed, and carrying the Provenance of the records it summarises so that an
 * answer can cite them. An output holds Kinds, times, counts, Entity names and Provenance, and never a
 * Record's own label and value pairs or Entity attributes, because Tool outputs are all a cloud model may
 * ever receive (ADR 0006).
 */
public sealed interface ToolOutput {
    /**
     * Where the records this output summarises came from: every record it lists, or the newest few of the
     * ones it counts. Empty for an output over Imports or Entities rather than records, since those are
     * cited by their ids and [EntityTimeline] cites an Entity's records.
     */
    public val cited: List<Provenance>
}

/** An Entity as a Tool names it: its id, Source, Kind and display name, and nothing the Source said about it. */
public data class EntityRef(
    val id: String,
    val source: Source,
    val kind: EntityKind,
    val displayName: String,
) {
    public companion object {
        public fun of(entity: Entity): EntityRef = EntityRef(entity.id, entity.source, entity.kind, entity.displayName)
    }
}

/** One Event or Claim as a Tool lists it: what happened, when, who was named and where it came from. */
public sealed interface RecordRef {
    public val entities: List<EntityRef>
    public val provenance: Provenance

    public data class OfEvent(
        val kind: EventKind,
        val time: Instant,
        override val entities: List<EntityRef>,
        override val provenance: Provenance,
    ) : RecordRef

    public data class OfClaim(
        val kind: ClaimKind,
        val asOf: Instant?,
        override val entities: List<EntityRef>,
        override val provenance: Provenance,
    ) : RecordRef

    public companion object {
        public fun of(event: Event): OfEvent =
            OfEvent(event.kind, event.time, event.entities.map(EntityRef::of), event.provenance)

        public fun of(claim: Claim): OfClaim =
            OfClaim(claim.kind, claim.asOf, claim.entities.map(EntityRef::of), claim.provenance)
    }
}

/** One Import as listImports describes it: when it ran, of what, and how much it produced. */
public data class ImportRef(
    val id: Long,
    val source: Source,
    val exportId: ExportId,
    val startedAt: Instant,
    /** Null while the Import runs or if it never finished. */
    val completedAt: Instant?,
    val eventCount: Int,
    val claimCount: Int,
    val parsedFiles: Int,
    val skippedFiles: Int,
    val failedFiles: Int,
) {
    public companion object {
        public fun of(import: Import): ImportRef =
            ImportRef(
                id = import.id.value,
                source = import.summary.source,
                exportId = import.summary.exportId,
                startedAt = import.startedAt,
                completedAt = import.completedAt,
                eventCount = import.summary.eventCount,
                claimCount = import.summary.claimCount,
                parsedFiles = import.summary.parsed.size,
                skippedFiles = import.summary.skipped.size,
                failedFiles = import.summary.failed.size,
            )
    }
}

/** Every Import in the Library, oldest first. */
public data class ImportsListed(
    val imports: List<ImportRef>,
) : ToolOutput {
    override val cited: List<Provenance> get() = emptyList()
}

/** The calendar days a Tool was asked about, in the device's zone; either end open when null. */
public data class DateRange(
    val from: LocalDate?,
    val to: LocalDate?,
)

/** How a count is spread over time: one point per calendar day or month in the device's zone. */
public enum class Grouping {
    Day,
    Month,
}

/** One bucket of a count over time: the first day of the day or month, and how many fell in it. */
public data class SeriesPoint(
    val start: LocalDate,
    val count: Int,
)

/**
 * How many Events there are of one Kind (or of every Kind), within a date range and from one Source, each
 * when asked for; and when a [Grouping] was asked for, the count spread over every day or month from the
 * first with an Event to the last, quiet ones included, so that a quiet stretch shows as zero.
 */
public data class EventsCounted(
    val kind: EventKind?,
    val range: DateRange,
    val source: Source?,
    val total: Int,
    val grouping: Grouping?,
    /** Null when no grouping was asked for; empty when there is nothing to spread. */
    val series: List<SeriesPoint>?,
    override val cited: List<Provenance>,
) : ToolOutput

/** One Entity and how many of the counted Events name it, with the newest few of those Events to cite. */
public data class RankedEntity(
    val entity: EntityRef,
    val count: Int,
    val cited: List<Provenance>,
)

/**
 * The Entities of one Kind that Events of one Kind name most often within a date range: how many such Entities
 * there are in all, and the top of them, most named first, ties by name then id.
 */
public data class TopEntities(
    val kind: EventKind,
    val entityKind: EntityKind,
    val range: DateRange,
    /** How many distinct Entities the Events name at all, not only the ones in [top]. */
    val count: Int,
    val top: List<RankedEntity>,
) : ToolOutput {
    override val cited: List<Provenance> get() = top.flatMap { it.cited }
}

/** One Entity, how many Events and Claims name it, the newest of those Events and every one of those Claims. */
public data class EntityTimeline(
    val entity: EntityRef,
    val eventCount: Int,
    val claimCount: Int,
    val events: List<RecordRef.OfEvent>,
    val claims: List<RecordRef.OfClaim>,
) : ToolOutput {
    override val cited: List<Provenance> get() = events.map { it.provenance } + claims.map { it.provenance }
}

/** The newest Events and Claims whose Records or Entity names contain every word of [text]. */
public data class TextFound(
    val text: String,
    val hits: List<RecordRef>,
) : ToolOutput {
    override val cited: List<Provenance> get() = hits.map { it.provenance }
}

/**
 * Two Entities of one Kind from different Sources whose normalised names agree (glossary: Match), with both
 * original names so that the user can judge whether they are the same thing. Computed when asked, never stored.
 */
public data class Match(
    val normalizedName: String,
    val first: EntityRef,
    val second: EntityRef,
)

/** Every Match among the Library's Entities of one Kind, by normalised name then by the Entities' ids. */
public data class MatchesFound(
    val kind: EntityKind,
    val matches: List<Match>,
) : ToolOutput {
    override val cited: List<Provenance> get() = emptyList()
}

/** The Gap report for one Import, citing the records its Verdicts rest on. */
public data class GapReported(
    val report: GapReport,
) : ToolOutput {
    override val cited: List<Provenance>
        get() =
            report.verdicts.flatMap { verdict ->
                verdict.evidence.filterIsInstance<Evidence.Records>().flatMap { it.cited }
            }
}

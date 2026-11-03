package com.jassemsendi.unpacked.insights

import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.data.ImportRepository
import com.jassemsendi.unpacked.ingest.SourceAdapter
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.EvidenceRule
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.Import
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import com.jassemsendi.unpacked.model.andThen
import com.jassemsendi.unpacked.model.map

/** Where a Source's Evidence rules come from: each adapter declares its own, and this is all the Gap report asks of it. */
public fun interface EvidenceRules {
    public fun of(source: Source): List<EvidenceRule>
}

/** The rules each Source's adapter declares: what the app uses; tests hand the reader others. */
public object DeclaredEvidenceRules : EvidenceRules {
    override fun of(source: Source): List<EvidenceRule> = SourceAdapter.of(source).evidenceRules
}

/** The nine Verdicts for one Import (glossary: Gap report), computed with no model involved. */
public data class GapReport(
    public val importId: ImportId,
    public val exportId: ExportId,
    public val source: Source,
    /** One per Article item, in the article's order. */
    public val verdicts: List<Verdict>,
) {
    public fun count(coverage: Coverage): Int = verdicts.count { it.coverage == coverage }
}

/**
 * Reads what one Import holds through the repository interfaces and applies its Source's Evidence rules to it.
 * Every Kind a rule names is read once for its count and once for the Provenance of its first [citedCount]
 * records, so the report costs a handful of queries however large the Import.
 */
public class GapReportReader(
    private val imports: ImportRepository,
    private val events: EventRepository,
    private val claims: ClaimRepository,
    private val rules: EvidenceRules = DeclaredEvidenceRules,
    private val citedCount: Int = CITED_COUNT,
) {
    /** [DataError.Local.NOT_FOUND] when no Import has that id. */
    public suspend fun read(importId: ImportId): Result<GapReport, DataError.Local> =
        imports.get(importId).andThen { import ->
            val sourceRules = rules.of(import.summary.source)
            contentsOf(import, sourceRules).map { contents ->
                GapReport(import.id, import.summary.exportId, import.summary.source, verdictsOf(sourceRules, contents))
            }
        }

    private suspend fun contentsOf(
        import: Import,
        rules: List<EvidenceRule>,
    ): Result<ImportContents, DataError.Local> =
        events.countByKind(import.id).andThen { eventCounts ->
            claims.countByKind(import.id).andThen { claimCounts ->
                val eventKinds = rules.filterIsInstance<EvidenceRule.EventsOf>().map { it.kind }
                val claimKinds = rules.filterIsInstance<EvidenceRule.ClaimsOf>().map { it.kind }
                cited(eventCounts, eventKinds) { kind ->
                    events.newestFirst(kind, limit = citedCount, offset = 0, importId = import.id).map { page ->
                        page.map { it.provenance }
                    }
                }.andThen { eventRecords ->
                    cited(claimCounts, claimKinds) { kind ->
                        claims.byKind(kind, limit = citedCount, offset = 0, importId = import.id).map { page ->
                            page.map { it.provenance }
                        }
                    }.map { claimRecords -> ImportContents(import.summary.files, eventRecords, claimRecords) }
                }
            }
        }

    /** Every Kind with a count, citing records only for the Kinds a rule names: the rest are never shown. */
    private suspend fun <K> cited(
        counts: Map<K, Int>,
        named: List<K>,
        read: suspend (K) -> Result<List<Provenance>, DataError.Local>,
    ): Result<Map<K, KindRecords>, DataError.Local> {
        val records = LinkedHashMap<K, KindRecords>()
        for ((kind, count) in counts) {
            val provenances =
                if (kind in named) {
                    when (val page = read(kind)) {
                        is Result.Error -> return page
                        is Result.Success -> page.data
                    }
                } else {
                    emptyList()
                }
            records[kind] = KindRecords(count, provenances)
        }
        return Result.Success(records)
    }

    public companion object {
        /** How many records of a Kind a Verdict cites by their Provenance. */
        public const val CITED_COUNT: Int = 3
    }
}

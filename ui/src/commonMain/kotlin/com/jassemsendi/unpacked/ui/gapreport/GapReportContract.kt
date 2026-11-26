package com.jassemsendi.unpacked.ui.gapreport

import com.jassemsendi.unpacked.model.ArticleItem
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.ui.home.RecordDetailUi
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.imports.FileResultUi

data class GapReportState(
    /** True from the start until the Imports and the report have been read, and again during a refresh. */
    val isLoading: Boolean = true,
    /** Every stored Import, newest first: the ones the report can be computed over. */
    val imports: List<ImportChoiceUi> = emptyList(),
    /** The Import reported on, or null while there is none. */
    val selected: ImportId? = null,
    /** The report over [selected]; the last one read while a refresh is in flight or failed. */
    val report: GapReportUi? = null,
    /** The Record opened from a Verdict's evidence, if any. */
    val detail: RecordDetailUi? = null,
    /** Why the last read failed, until the next one succeeds; what is shown is the last successful read. */
    val error: String? = null,
)

/** One stored Import as the chooser names it. */
data class ImportChoiceUi(
    val id: ImportId,
    val exportName: String,
    /** When the run started, in the device's time zone. */
    val startedAt: String,
    /** False for a run that never finished (ADR 0007); its report covers what landed before it stopped. */
    val isComplete: Boolean,
)

/** The nine Verdicts for one Import as the screen shows them. */
data class GapReportUi(
    val exportName: String,
    val sourceName: String,
    /** How the nine fell out, such as "2 covered, 3 partial, 4 missing". */
    val summary: String,
    /** One per Article item, in the article's order. */
    val items: List<ArticleItemUi>,
)

/** One Article item with its Verdict: how far the Import goes toward it and what was found. */
data class ArticleItemUi(
    val item: ArticleItem,
    /** The article letter, such as "15(1)(a)". */
    val article: String,
    val title: String,
    /** One sentence on what the article asks for. */
    val description: String,
    val coverage: Coverage,
    val coverageLabel: String,
    /** What each satisfied Evidence rule found, in rule order; empty when the item is Missing. */
    val evidence: List<EvidenceUi>,
    /** What the card says in place of evidence when nothing counts toward the item. */
    val emptyText: String?,
)

/** What one Evidence rule found, captioned with how far it counts. */
sealed interface EvidenceUi {
    val caption: String

    /** The first files of the inventory that matched, each with what the Import made of it, and how many more there are. */
    data class Files(
        override val caption: String,
        val files: List<FileResultUi>,
        val moreFiles: Int,
    ) : EvidenceUi

    /** The cited Events or Claims, each opening to its Record detail. */
    data class Records(
        override val caption: String,
        val cited: List<CitedRecordUi>,
    ) : EvidenceUi
}

/** One cited Event or Claim: its Provenance in words, and the reference that opens its Record. */
data class CitedRecordUi(
    val ref: RecordRef,
    val text: String,
)

sealed interface GapReportAction {
    /** Re-read the Imports and the report; the screen sends it whenever it appears, since Imports may have changed. */
    data object Refresh : GapReportAction

    /** Report on another Import. */
    data class Select(
        val id: ImportId,
    ) : GapReportAction

    data class Open(
        val ref: RecordRef,
    ) : GapReportAction

    data object CloseDetail : GapReportAction
}

/** The one notice the screen carries, shown once: the report is a tool, not legal advice (spec story 50). */
const val NOT_LEGAL_ADVICE =
    "Unpacked is a tool, not legal advice: this report says what the Export did and did not contain."

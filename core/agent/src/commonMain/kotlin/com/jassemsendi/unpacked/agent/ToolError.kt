package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Error
import kotlinx.datetime.LocalDate

/**
 * Why a Tool could not answer (ADR 0009): an input the model wrote that names nothing the Library knows, or a
 * Library that could not be read. Every case is something a strategy can tell the model about and let it retry
 * with; none is thrown.
 */
public sealed interface ToolError : Error {
    /** An input that names a Kind, Entity Kind or Source the Library does not know; [known] are the names it does. */
    public sealed interface UnknownName : ToolError {
        public val given: String
        public val known: List<String>
    }

    public data class UnknownKind(
        override val given: String,
        override val known: List<String> = EVENT_KIND_NAMES,
    ) : UnknownName

    public data class UnknownEntityKind(
        override val given: String,
        override val known: List<String> = ENTITY_KIND_NAMES,
    ) : UnknownName

    public data class UnknownSource(
        override val given: String,
        override val known: List<String> = SOURCE_NAMES,
    ) : UnknownName

    public data class UnknownGrouping(
        override val given: String,
        override val known: List<String> = GROUPING_NAMES,
    ) : UnknownName

    /** A date that is not a calendar day written as `YYYY-MM-DD`. */
    public data class InvalidDate(
        val given: String,
    ) : ToolError

    /** A range whose last day comes before its first. */
    public data class InvertedRange(
        val from: LocalDate,
        val to: LocalDate,
    ) : ToolError

    /** A count or page size outside what a Tool answers with. */
    public data class CountOutOfRange(
        val given: Int,
        val allowed: IntRange,
    ) : ToolError

    /** A search with nothing to look for. */
    public data object BlankText : ToolError

    /** No Import has this id. */
    public data class UnknownImport(
        val id: Long,
    ) : ToolError

    /** No Entity has this id. */
    public data class UnknownEntity(
        val id: String,
    ) : ToolError

    /** A Tool output with no shape a chart can show; [reason] says which shape it lacks. */
    public data class NotChartable(
        val output: ToolOutput,
        val reason: Reason,
    ) : ToolError {
        public enum class Reason {
            /** One number counted once: ask for the count grouped by day or month to see it over time. */
            SingleNumber,

            /** A series or ranking with no points in it. */
            NothingToChart,

            /** Imports, a timeline, search hits, Matches or a Gap report: not quantities. */
            NotQuantities,
        }
    }

    /** The Library could not be read; the Tool has nothing to say about the question. */
    public data class Library(
        val error: DataError.Local,
    ) : ToolError
}

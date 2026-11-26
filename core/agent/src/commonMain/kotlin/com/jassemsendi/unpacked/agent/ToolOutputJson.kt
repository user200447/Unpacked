package com.jassemsendi.unpacked.agent

import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// What a Tool output looks like to the model (ADR 0006: the model sees Tool outputs and nothing else). Every cited
// Provenance is replaced by its citation marker, so the model can quote `[3]` and never sees a file path; times are
// written in the Library's zone, the zone the Ask screen shows them in, so a time the model repeats verifies.

/** Renders Tool outputs for one question, marking every Provenance through the question's [citations]. */
internal class ToolOutputJson(
    citations: CitationIndex,
    timeZone: TimeZone,
) {
    private val marks = JsonMarks(citations, timeZone)
    private val gaps = GapReportJson(marks)

    fun render(output: ToolOutput): JsonObject =
        when (output) {
            is ImportsListed -> imports(output)
            is EventsCounted -> counted(output)
            is TopEntities -> top(output)
            is EntityTimeline -> timeline(output)
            is TextFound -> found(output)
            is MatchesFound -> matches(output)
            is GapReported -> gaps.render(output.report)
            is ChartSpec -> chart(output)
        }

    private fun imports(output: ImportsListed) =
        buildJsonObject {
            putJsonArray("imports") {
                output.imports.forEach { import ->
                    add(
                        buildJsonObject {
                            put("id", import.id)
                            put("source", import.source.name)
                            put("exportId", import.exportId.value)
                            put("startedAt", marks.local(import.startedAt))
                            put("completedAt", import.completedAt?.let(marks::local))
                            put("events", import.eventCount)
                            put("claims", import.claimCount)
                            putJsonObject("files") {
                                put("parsed", import.parsedFiles)
                                put("skipped", import.skippedFiles)
                                put("failed", import.failedFiles)
                            }
                        },
                    )
                }
            }
        }

    private fun counted(output: EventsCounted) =
        buildJsonObject {
            put("kind", output.kind?.name)
            put("from", output.range.from?.toString())
            put("to", output.range.to?.toString())
            put("source", output.source?.name)
            put("total", output.total)
            put("groupBy", output.grouping?.name)
            output.series?.let { series ->
                putJsonArray("series") {
                    series.forEach { point ->
                        add(
                            buildJsonObject {
                                put("start", point.start.toString())
                                put("count", point.count)
                            },
                        )
                    }
                }
            }
            put("cited", marks.cited(output.cited))
        }

    private fun top(output: TopEntities) =
        buildJsonObject {
            put("kind", output.kind.name)
            put("entityKind", output.entityKind.name)
            put("from", output.range.from?.toString())
            put("to", output.range.to?.toString())
            put("count", output.count)
            putJsonArray("top") {
                output.top.forEach { ranked ->
                    add(
                        buildJsonObject {
                            put("entity", marks.entity(ranked.entity))
                            put("count", ranked.count)
                            put("cited", marks.cited(ranked.cited))
                        },
                    )
                }
            }
        }

    private fun timeline(output: EntityTimeline) =
        buildJsonObject {
            put("entity", marks.entity(output.entity))
            put("eventCount", output.eventCount)
            put("claimCount", output.claimCount)
            put("events", marks.records(output.events))
            put("claims", marks.records(output.claims))
        }

    private fun found(output: TextFound) =
        buildJsonObject {
            put("text", output.text)
            put("hits", marks.records(output.hits))
        }

    private fun matches(output: MatchesFound) =
        buildJsonObject {
            put("entityKind", output.kind.name)
            putJsonArray("matches") {
                output.matches.forEach { match ->
                    add(
                        buildJsonObject {
                            put("name", match.normalizedName)
                            put("first", marks.entity(match.first))
                            put("second", marks.entity(match.second))
                        },
                    )
                }
            }
        }

    private fun chart(output: ChartSpec) =
        buildJsonObject {
            put("chart", output.kind.name)
            put("title", output.title)
            put("valueLabel", output.valueLabel)
            put("pointLabel", output.pointLabel)
            putJsonArray("points") {
                output.points.forEach { point ->
                    add(
                        buildJsonObject {
                            put("label", point.label)
                            put("value", point.value)
                        },
                    )
                }
            }
            put("cited", marks.cited(output.cited))
        }
}

/** The reason a Tool refused a call, in the words the model (and the Tool trace) sees. */
public fun ToolError.describe(): String =
    when (this) {
        is ToolError.UnknownName -> "Unknown ${nameOf()} \"$given\". Known: ${known.joinToString()}."
        is ToolError.InvalidDate -> "\"$given\" is not a date. Write dates as YYYY-MM-DD."
        is ToolError.InvertedRange -> "The range ends ($to) before it starts ($from)."
        is ToolError.CountOutOfRange -> "$given is out of range; ask for ${allowed.first} to ${allowed.last}."
        ToolError.BlankText -> "The text to search for is blank."
        is ToolError.UnknownImport -> "No Import has the id $id. Call listImports for the ids."
        is ToolError.UnknownEntity -> "No Entity has the id \"$id\". Entity ids come from topEntities or searchText."
        is ToolError.NotChartable -> "That output cannot be charted: ${reason.describe()}"
        is ToolError.NoOutputToChart -> "No earlier output of $tool to chart. Call $tool first."
        is ToolError.Library -> "The Library could not be read: $error"
    }

private fun ToolError.UnknownName.nameOf(): String =
    when (this) {
        is ToolError.UnknownKind -> "kind"
        is ToolError.UnknownEntityKind -> "entity kind"
        is ToolError.UnknownSource -> "source"
        is ToolError.UnknownGrouping -> "grouping"
    }

private fun ToolError.NotChartable.Reason.describe(): String =
    when (this) {
        ToolError.NotChartable.Reason.SingleNumber -> "it is a single number; count with groupBy to get a series."
        ToolError.NotChartable.Reason.NothingToChart -> "it has no points."
        ToolError.NotChartable.Reason.NotQuantities -> "it holds records, not quantities."
    }

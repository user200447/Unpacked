package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.insights.Evidence
import com.jassemsendi.unpacked.insights.GapReport
import com.jassemsendi.unpacked.model.Coverage
import com.jassemsendi.unpacked.model.EvidenceRule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A Gap report as the model sees it: the Verdicts with their evidence, records cited by marker, files by path. */
internal class GapReportJson(
    private val marks: JsonMarks,
) {
    fun render(report: GapReport): JsonObject =
        buildJsonObject {
            put("importId", report.importId.value)
            put("exportId", report.exportId.value)
            put("source", report.source.name)
            put("covered", report.count(Coverage.Covered))
            put("partial", report.count(Coverage.Partial))
            put("missing", report.count(Coverage.Missing))
            putJsonArray("verdicts") {
                report.verdicts.forEach { verdict ->
                    add(
                        buildJsonObject {
                            put("item", verdict.item.name)
                            put("article", verdict.item.article)
                            put("coverage", verdict.coverage.name)
                            putJsonArray("evidence") { verdict.evidence.forEach { add(evidence(it)) } }
                        },
                    )
                }
            }
        }

    private fun evidence(evidence: Evidence): JsonObject =
        buildJsonObject {
            put("rule", ruleName(evidence.rule))
            when (evidence) {
                is Evidence.Files -> {
                    putJsonArray("files") { evidence.files.forEach { add(JsonPrimitive(it.path)) } }
                }

                is Evidence.Records -> {
                    put("count", evidence.count)
                    put("cited", marks.cited(evidence.cited))
                }
            }
        }

    private fun ruleName(rule: EvidenceRule): String =
        when (rule) {
            is EvidenceRule.FileNamed -> "file named ${rule.pattern}"
            is EvidenceRule.EventsOf -> "${rule.kind.name} Events"
            is EvidenceRule.ClaimsOf -> "${rule.kind.name} Claims"
        }
}

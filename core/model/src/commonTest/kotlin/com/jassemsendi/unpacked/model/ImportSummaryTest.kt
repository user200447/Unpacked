package com.jassemsendi.unpacked.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ImportSummaryTest {
    @Test
    fun countsEachOutcomeAndTotalsRecords() {
        val summary =
            ImportSummary(
                exportId = "export",
                source = Source.Instagram,
                files =
                    listOf(
                        FileResult.Parsed("a.json", RawKind("a"), recordCount = 3, eventCount = 2, claimCount = 1),
                        FileResult.Parsed("b.json", RawKind("b"), recordCount = 1, eventCount = 1, claimCount = 0),
                        FileResult.Skipped("c.json", RawKind("c"), reason = "no mapper"),
                        FileResult.Failed("d.json", RawKind("d"), reason = "bad json"),
                    ),
                inventory = Inventory(jsonFiles = 4, mediaFiles = 7, otherFiles = 1),
            )
        assertEquals(2, summary.parsed.size)
        assertEquals(1, summary.skipped.size)
        assertEquals(1, summary.failed.size)
        assertEquals(3, summary.eventCount)
        assertEquals(1, summary.claimCount)
    }
}

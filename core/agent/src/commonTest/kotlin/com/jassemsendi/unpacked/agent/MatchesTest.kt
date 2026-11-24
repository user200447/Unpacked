package com.jassemsendi.unpacked.agent

import com.jassemsendi.unpacked.fixtures.orFail
import com.jassemsendi.unpacked.insights.EvidenceRules
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.Source
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The Match rule (ADR 0002): by normalised name, across Sources only, computed and never stored. */
class MatchesTest {
    private val library = TestLibrary()
    private val tools =
        with(library) {
            LibraryTools(imports, events, claims, entities, search, zone, EvidenceRules { emptyList() })
        }

    @Test
    fun findMatchesPairsAnAdvertiserFromTwoSourcesByNormalisedNameWithBothOriginalNames() =
        runTest {
            val before = library.entities.all().orFail()

            val found = tools.findMatches("Advertiser").orFail()

            assertEquals(EntityKind.Advertiser, found.kind)
            assertEquals(
                listOf(Match("example courier", EntityRef.of(library.courier), EntityRef.of(library.googleCourier))),
                found.matches,
            )
            assertEquals(
                "Example Courier",
                found.matches
                    .single()
                    .first.displayName,
            )
            assertEquals(
                "EXAMPLE  Courier",
                found.matches
                    .single()
                    .second.displayName,
            )
            assertEquals(emptyList(), found.cited, "a Match is over Entities; entityTimeline cites their records")

            assertEquals(before, library.entities.all().orFail(), "nothing is written: the Entities are as they were")
            assertEquals(
                emptyList(),
                tools.findMatches("Topic").orFail().matches,
                "one Source alone never matches itself",
            )
            assertEquals(
                ToolError.UnknownEntityKind("Brand"),
                assertIs<Result.Error<ToolError>>(tools.findMatches("Brand")).error,
            )
        }

    @Test
    fun theRuleNeverPairsEntitiesOfOneSourceAndPairsEveryCrossSourcePairOfALikeName() {
        val instagramA = Entity(Source.Instagram, EntityKind.Advertiser, "a", "Acme Co")
        val instagramB = Entity(Source.Instagram, EntityKind.Advertiser, "b", "ACME CO")
        val google = Entity(Source.GoogleTakeout, EntityKind.Advertiser, "acme co", " acme   co ")
        val googleTopic = Entity(Source.GoogleTakeout, EntityKind.Topic, "acme co", "Acme Co")
        val other = Entity(Source.GoogleTakeout, EntityKind.Advertiser, "zed", "Zed")

        val matches = matchesAmong(listOf(other, googleTopic, google, instagramB, instagramA))

        assertEquals(
            listOf(
                Match("acme co", EntityRef.of(instagramA), EntityRef.of(google)),
                Match("acme co", EntityRef.of(instagramB), EntityRef.of(google)),
            ),
            matches,
            "the two Instagram advertisers each match the Google one and never each other; the Topic is another Kind",
        )
        assertEquals(emptyList(), matchesAmong(emptyList()))
    }
}

package com.jassemsendi.unpacked.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ProvenanceTest {
    private val provenance =
        Provenance(
            exportId = ExportId("instagram-2026-10-14"),
            path = "ads_information/ads_viewed.json",
            recordIndex = 3,
        )

    @Test
    fun hashIsStableAcrossInstances() {
        assertEquals(provenance.hash, provenance.copy().hash)
    }

    @Test
    fun hashChangesWithAnyField() {
        assertNotEquals(provenance.hash, provenance.copy(recordIndex = 4).hash)
        assertNotEquals(provenance.hash, provenance.copy(path = "ads_information/ads_clicked.json").hash)
        assertNotEquals(provenance.hash, provenance.copy(exportId = ExportId("other")).hash)
    }

    @Test
    fun fnv1a64MatchesTheReferenceVectors() {
        assertEquals(-3750763034362895579L, fnv1a64(ByteArray(0)))
        assertEquals(-5808556873153909620L, fnv1a64("a".encodeToByteArray()))
    }
}

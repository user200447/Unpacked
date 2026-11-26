package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.Source
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tested Exports Settings shows come from the adapters alone, one list per Source, each entry about its own Source. */
class TestedExportTest {
    @Test
    fun everyAdapterDeclaresAtLeastOneTestedExportOfItsOwnSource() {
        for (adapter in SourceAdapter.all) {
            assertTrue(adapter.testedExports.isNotEmpty(), "${adapter.source} declares none")
            val own = adapter.testedExports.all { it.source == adapter.source }
            assertTrue(own, "${adapter.source} names another Source")
        }
    }

    @Test
    fun theListSettingsShowsIsEveryAdaptersListInSourceOrder() {
        assertEquals(SourceAdapter.all.flatMap { it.testedExports }, SourceAdapter.allTestedExports)
        val instagram = SourceAdapter.allTestedExports.single { it.source == Source.Instagram }
        assertEquals("JSON", instagram.format)
        assertEquals(LocalDate(2026, 10, 20), instagram.downloadedOn)
    }
}

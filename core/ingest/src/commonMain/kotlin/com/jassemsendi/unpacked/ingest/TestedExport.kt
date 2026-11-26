package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.Source
import kotlinx.datetime.LocalDate

/**
 * One Export the app was tested against (spec story 55): the Source, the format it was downloaded in and when, and
 * the choices made when requesting it, so a user can judge whether theirs is likely to import. Each Source's adapter
 * declares its own; Settings shows them all from that one declaration.
 */
public data class TestedExport(
    val source: Source,
    val format: String,
    val downloadedOn: LocalDate,
    val note: String,
)

package com.jassemsendi.unpacked.ui.home

import com.jassemsendi.unpacked.data.ClaimRepository
import com.jassemsendi.unpacked.data.EventRepository
import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Result
import com.jassemsendi.unpacked.model.map
import kotlinx.datetime.TimeZone

/**
 * Reads the Record behind [ref] from whichever repository holds it, as the detail pane shows it. Every
 * screen that opens a Record goes through here, so a hit and a timeline entry open the same detail.
 */
internal suspend fun readRecord(
    ref: RecordRef,
    events: EventRepository,
    claims: ClaimRepository,
    timeZone: TimeZone,
): Result<RecordDetailUi, DataError.Local> =
    when (ref) {
        is RecordRef.Event -> events.byProvenance(ref.provenance).map { it.toDetail(timeZone) }
        is RecordRef.Claim -> claims.byProvenance(ref.provenance).map { it.toDetail(timeZone) }
    }

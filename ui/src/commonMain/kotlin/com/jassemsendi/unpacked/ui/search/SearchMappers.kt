package com.jassemsendi.unpacked.ui.search

import com.jassemsendi.unpacked.data.SearchHit
import com.jassemsendi.unpacked.ui.home.RecordRef
import com.jassemsendi.unpacked.ui.home.RecordTimeUi
import com.jassemsendi.unpacked.ui.home.label
import com.jassemsendi.unpacked.ui.home.summaryOf
import com.jassemsendi.unpacked.ui.time.toDeviceMinute
import kotlinx.datetime.TimeZone

fun SearchHit.toUi(timeZone: TimeZone): SearchHitUi =
    when (this) {
        is SearchHit.OfEvent -> {
            SearchHitUi(
                ref = RecordRef.Event(provenance),
                kindLabel = event.kind.label(),
                time = RecordTimeUi.Happened(event.time.toDeviceMinute(timeZone)),
                summary = summaryOf(event.entities, event.attributes),
            )
        }

        is SearchHit.OfClaim -> {
            SearchHitUi(
                ref = RecordRef.Claim(provenance),
                kindLabel = claim.kind.label(),
                time =
                    claim.asOf?.let {
                        RecordTimeUi.LastUpdated(
                            it.toDeviceMinute(timeZone),
                        )
                    } ?: RecordTimeUi.Undated,
                summary = summaryOf(claim.entities, claim.attributes),
            )
        }
    }

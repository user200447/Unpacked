package com.jassemsendi.unpacked.ui.search

import com.jassemsendi.unpacked.data.SearchHit
import com.jassemsendi.unpacked.ui.home.RecordRowUi
import com.jassemsendi.unpacked.ui.home.toRow
import kotlinx.datetime.TimeZone

fun SearchHit.toUi(timeZone: TimeZone): RecordRowUi =
    when (this) {
        is SearchHit.OfEvent -> event.toRow(timeZone)
        is SearchHit.OfClaim -> claim.toRow(timeZone)
    }

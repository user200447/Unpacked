package com.jassemsendi.unpacked.ui.entities

import com.jassemsendi.unpacked.data.EntityCounts
import com.jassemsendi.unpacked.ui.home.counted
import com.jassemsendi.unpacked.ui.home.label

fun EntityCounts.toRow(): EntityRowUi =
    EntityRowUi(
        id = entity.id,
        name = entity.displayName,
        kindLabel = entity.kind.label(),
        sourceName = entity.source.name,
        eventCount = eventCount,
        claimCount = claimCount,
    )

/** "3 Events, 1 Claim": how often the Entity is named, as every row and the pane header say it. */
internal fun EntityRowUi.counts(): String = "${counted(eventCount, "Event")}, ${counted(claimCount, "Claim")}"

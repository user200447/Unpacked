package com.jassemsendi.unpacked.ui.accessrequest

import com.jassemsendi.unpacked.insights.AccessRequest

internal fun AccessRequest.toUi(): AccessRequestUi =
    AccessRequestUi(
        exportName = exportId.value,
        sourceName = source.name,
        summary =
            if (isOutstanding) {
                "Asks for ${items.size} of the nine items: ${items.joinToString { it.item.article }}."
            } else {
                "Nothing outstanding: the Export addresses all nine items."
            },
        text = text,
        fileName = "access-request-${exportId.value}.txt",
    )

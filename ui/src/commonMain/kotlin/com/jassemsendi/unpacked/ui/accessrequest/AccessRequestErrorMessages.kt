package com.jassemsendi.unpacked.ui.accessrequest

/** The one place an [AccessRequestSaveError] becomes words (ADR 0009). */
fun AccessRequestSaveError.toMessage(): String =
    when (this) {
        AccessRequestSaveError.NOT_WRITABLE -> "The letter could not be written there."
    }

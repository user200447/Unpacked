package com.jassemsendi.unpacked.ui.imports

import com.jassemsendi.unpacked.ingest.ExportError
import com.jassemsendi.unpacked.ingest.ImportError
import com.jassemsendi.unpacked.ui.toMessage

// What the Import screen says when a run stops before storing an Import.

fun ExportError.toMessage(): String =
    when (this) {
        ExportError.NOT_FOUND -> {
            "There is nothing at that location any more."
        }

        ExportError.NOT_READABLE -> {
            "That is not an Export Unpacked can read. " +
                "Pick the zip as you downloaded it, or the folder you unzipped it into."
        }
    }

fun ImportError.toMessage(): String =
    when (this) {
        is ImportError.Reading -> error.toMessage()
        is ImportError.Writing -> error.toMessage()
    }

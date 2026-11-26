package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.model.DataError
import com.jassemsendi.unpacked.model.Error

/**
 * Why an Import stopped before storing a complete Import. A file that could not be parsed is not
 * one of these: it is a [com.jassemsendi.unpacked.model.FileResult.Failed] and the run goes on.
 */
public sealed interface ImportError : Error {
    /** The Export could not be opened or listed; nothing was written. */
    public data class Reading(
        val error: ExportError,
    ) : ImportError

    /** The library refused a write; what was written before stays as an Import with no completion time. */
    public data class Writing(
        val error: DataError.Local,
    ) : ImportError
}

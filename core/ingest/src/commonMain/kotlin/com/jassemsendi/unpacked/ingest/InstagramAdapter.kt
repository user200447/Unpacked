package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.ingest.instagram.FlatRecord
import com.jassemsendi.unpacked.ingest.instagram.InstagramFiles
import com.jassemsendi.unpacked.ingest.instagram.InstagramMapper
import com.jassemsendi.unpacked.ingest.instagram.LabelValueRecord
import com.jassemsendi.unpacked.ingest.instagram.LabelValues
import com.jassemsendi.unpacked.ingest.instagram.flatten
import com.jassemsendi.unpacked.ingest.instagram.time
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.Source

/** Reads an Instagram "Download your information" Export in its JSON `label_values` dialect. */
public object InstagramAdapter : SourceAdapter {
    override val source: Source = Source.Instagram

    override fun parse(
        file: ExportFile,
        text: String,
        exportId: String,
    ): FileOutcome {
        val mapper =
            InstagramFiles.mapperFor(file.name)
                ?: return FileOutcome(
                    FileResult.Skipped(file.path, file.rawKind, "no mapper registered for ${file.name}"),
                )
        return LabelValues.decode(text).fold(
            onSuccess = { records -> map(file, exportId, records, mapper) },
            onFailure = { error ->
                FileOutcome(FileResult.Failed(file.path, file.rawKind, error.message ?: "could not decode JSON"))
            },
        )
    }

    private fun map(
        file: ExportFile,
        exportId: String,
        records: List<LabelValueRecord>,
        mapper: InstagramMapper,
    ): FileOutcome {
        val events = ArrayList<Event>()
        val claims = ArrayList<Claim>()
        records.forEachIndexed { index, record ->
            val flat = FlatRecord(record.flatten(), record.time(), Provenance(exportId, file.path, index), file.rawKind)
            val mapped = mapper.map(flat)
            events += mapped.events
            claims += mapped.claims
        }
        val result = FileResult.Parsed(file.path, file.rawKind, records.size, events.size, claims.size)
        return FileOutcome(result, events, claims)
    }
}

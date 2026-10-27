package com.jassemsendi.unpacked.ingest

import com.jassemsendi.unpacked.ingest.instagram.FlatRecord
import com.jassemsendi.unpacked.ingest.instagram.InstagramMapper
import com.jassemsendi.unpacked.ingest.instagram.InstagramMappers
import com.jassemsendi.unpacked.ingest.instagram.LabelValueRecord
import com.jassemsendi.unpacked.ingest.instagram.LabelValuesDecoder
import com.jassemsendi.unpacked.ingest.instagram.flatten
import com.jassemsendi.unpacked.ingest.instagram.time
import com.jassemsendi.unpacked.model.FileResult
import com.jassemsendi.unpacked.model.Source

/** Reads an Instagram "Download your information" Export in its JSON `label_values` dialect. */
public object InstagramAdapter : SourceAdapter {
    override val source: Source = Source.Instagram

    override fun parse(
        file: ExportFile,
        text: String,
    ): FileOutcome {
        val mapper =
            InstagramMappers.forFile(file.name)
                ?: return FileOutcome(
                    FileResult.Skipped(file.path, file.rawKind, "no mapper registered for ${file.name}"),
                )
        return LabelValuesDecoder.decode(text).fold(
            onSuccess = { records -> map(file, records, mapper) },
            onFailure = { error ->
                FileOutcome(FileResult.Failed(file.path, file.rawKind, error.message ?: "could not decode JSON"))
            },
        )
    }

    private fun map(
        file: ExportFile,
        records: List<LabelValueRecord>,
        mapper: InstagramMapper,
    ): FileOutcome {
        var output = Output.NONE
        var dropped = 0
        records.forEachIndexed { index, record ->
            val mapped = mapper.map(FlatRecord(record.flatten(), record.time(), file.provenanceOf(index), file.rawKind))
            if (mapped.isEmpty) dropped++ else output += mapped
        }
        val result =
            FileResult.Parsed(
                path = file.path,
                rawKind = file.rawKind,
                recordCount = records.size,
                eventCount = output.events.size,
                claimCount = output.claims.size,
                droppedCount = dropped,
            )
        return FileOutcome(result, output)
    }
}

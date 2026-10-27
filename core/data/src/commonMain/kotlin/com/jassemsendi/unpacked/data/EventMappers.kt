package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.data.db.EventAttributeRow
import com.jassemsendi.unpacked.data.db.EventEntityRow
import com.jassemsendi.unpacked.data.db.EventRow
import com.jassemsendi.unpacked.data.db.KindCount
import com.jassemsendi.unpacked.data.db.ProvenanceColumns
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.Event
import com.jassemsendi.unpacked.model.EventKind
import com.jassemsendi.unpacked.model.ExportId
import com.jassemsendi.unpacked.model.ImportId
import com.jassemsendi.unpacked.model.Provenance
import com.jassemsendi.unpacked.model.RawKind

internal fun Provenance.toColumns(): ProvenanceColumns = ProvenanceColumns(exportId.value, path, recordIndex, itemIndex)

internal fun ProvenanceColumns.toModel(): Provenance = Provenance(ExportId(exportId), path, recordIndex, itemIndex)

internal fun <K : Enum<K>> List<KindCount>.toMap(kindOf: (String) -> K): Map<K, Int> =
    associate { kindOf(it.kind) to it.count }

internal fun Event.toRow(importId: ImportId): EventRow =
    EventRow(id, importId.value, kind.name, rawKind.value, time.toEpochMillis(), provenance.toColumns())

internal fun Event.attributeRows(): List<EventAttributeRow> =
    attributes.entries.mapIndexed { position, (label, value) -> EventAttributeRow(id, position, label, value) }

internal fun Event.linkRows(): List<EventEntityRow> =
    entities.distinctBy { it.id }.mapIndexed { position, entity -> EventEntityRow(id, entity.id, position) }

internal fun EventRow.toModel(
    attributes: List<EventAttributeRow>,
    entities: List<Entity>,
): Event =
    Event(
        kind = EventKind.valueOf(kind),
        rawKind = RawKind(rawKind),
        time = time.toInstant(),
        provenance = provenance.toModel(),
        entities = entities,
        attributes = attributes.associate { it.label to it.value },
    )

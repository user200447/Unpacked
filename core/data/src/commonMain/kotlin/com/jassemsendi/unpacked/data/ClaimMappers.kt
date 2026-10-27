package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.data.db.ClaimAttributeRow
import com.jassemsendi.unpacked.data.db.ClaimEntityRow
import com.jassemsendi.unpacked.data.db.ClaimRow
import com.jassemsendi.unpacked.model.Claim
import com.jassemsendi.unpacked.model.ClaimKind
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.RawKind

internal fun Claim.toRow(importId: Long): ClaimRow =
    ClaimRow(id, importId, kind.name, rawKind.value, asOf?.toEpochMillis(), provenance.toColumns())

internal fun Claim.attributeRows(): List<ClaimAttributeRow> =
    attributes.entries.mapIndexed { position, (label, value) -> ClaimAttributeRow(id, position, label, value) }

internal fun Claim.linkRows(): List<ClaimEntityRow> =
    entities.distinctBy { it.id }.mapIndexed { position, entity -> ClaimEntityRow(id, entity.id, position) }

internal fun ClaimRow.toModel(
    attributes: List<ClaimAttributeRow>,
    entities: List<Entity>,
): Claim =
    Claim(
        kind = ClaimKind.valueOf(kind),
        rawKind = RawKind(rawKind),
        provenance = provenance.toModel(),
        asOf = asOf?.toInstant(),
        entities = entities,
        attributes = attributes.associate { it.label to it.value },
    )

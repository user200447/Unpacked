package com.jassemsendi.unpacked.data

import com.jassemsendi.unpacked.data.db.EntityAttributeRow
import com.jassemsendi.unpacked.data.db.EntityRow
import com.jassemsendi.unpacked.model.Entity
import com.jassemsendi.unpacked.model.EntityKind

internal fun Entity.toRow(): EntityRow = EntityRow(id, source.id, kind.name, key, displayName)

internal fun Entity.attributeRows(): List<EntityAttributeRow> =
    attributes.entries.mapIndexed { position, (label, value) -> EntityAttributeRow(id, position, label, value) }

internal fun EntityRow.toModel(attributes: List<EntityAttributeRow>): Entity =
    Entity(
        source = sourceOf(source),
        kind = EntityKind.valueOf(kind),
        key = key,
        displayName = displayName,
        attributes = attributes.associate { it.label to it.value },
    )

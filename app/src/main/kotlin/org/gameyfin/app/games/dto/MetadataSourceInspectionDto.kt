package org.gameyfin.app.games.dto

class MetadataSourceInspectionDto(
    val sources: List<MetadataSourceDataDto>,
    /** Field name -> key of the source that wins the field when merged */
    val winners: Map<String, String>
)

class MetadataSourceDataDto(
    /** Unique per plugin and role, referenced by [MetadataSourceInspectionDto.winners] */
    val key: String,
    val pluginId: String,
    val priority: Int,
    val isBaseGame: Boolean,
    val originalId: String,
    /** Field name -> display values. Fields the source did not provide are omitted. */
    val values: Map<String, List<String>>
)

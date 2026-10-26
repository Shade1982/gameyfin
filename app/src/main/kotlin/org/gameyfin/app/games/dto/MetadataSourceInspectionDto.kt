package org.gameyfin.app.games.dto

class MetadataSourceInspectionDto(
    val sources: List<MetadataSourceDataDto>,
    /** Field name -> key of the source that wins the field when merged */
    val winners: Map<String, String>
)

enum class MetadataSourceStatus { OK, NO_DATA, ERROR }

class MetadataSourceDataDto(
    /** Unique per plugin and role, referenced by [MetadataSourceInspectionDto.winners] */
    val key: String,
    val pluginId: String,
    val priority: Int,
    val baseGame: Boolean,
    val originalId: String,
    val status: MetadataSourceStatus,
    val error: String?,
    /** Field name -> display values (image fields contain URLs). Fields the source did not provide are omitted. */
    val values: Map<String, List<String>>
)

/** How many fields of the games in the library currently come from a plugin */
class MetadataSourceStatsDto(
    val pluginId: String,
    val field: String,
    val count: Long
)

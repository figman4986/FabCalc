package com.figapps.fractioncalculatorv1.wire

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Metal family a filler wire is intended for. */
enum class MetalType(val label: String) {
    CARBON_STEEL("Carbon steel"),
    STAINLESS("Stainless"),
    ALUMINUM("Aluminum"),
    NICKEL("Nickel"),
    HARDFACING("Hardfacing"),
    OTHER("Other"),
}

/** Welding process. [marker] is the short GMAW/FCAW badge shown on a library row. */
enum class WireProcess(val label: String, val marker: String) {
    SOLID_GMAW("Solid wire (GMAW)", "GMAW"),
    FLUX_CORE("Flux-cored (FCAW)", "FCAW"),
    METAL_CORE("Metal-cored (GMAW)", "GMAW"),
    OTHER("Other", "\u2014"),
}

/**
 * One imported welding-wire datasheet PDF plus its tags.
 *
 * The PDF itself lives in `filesDir/wireDatasheets/<fileName>` — only the metadata is in Room.
 * [sha256] is unique so the same PDF can never be imported twice. [wireSizeMm] is the canonical
 * size in millimetres (null = unspecified); it is converted for display per the global unit.
 */
@Entity(
    tableName = "wire_datasheets",
    indices = [Index(value = ["sha256"], unique = true), Index(value = ["metalType"])],
)
data class WireDatasheet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val sha256: String,
    val brand: String,
    val productName: String,
    val awsClassification: String,
    val metalType: MetalType,
    val process: WireProcess,
    val wireSizeMm: Double?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** The user-editable tag set; the import and edit tag forms share it. */
data class WireTags(
    val brand: String = "",
    val productName: String = "",
    val awsClassification: String = "",
    val metalType: MetalType = MetalType.CARBON_STEEL,
    val process: WireProcess = WireProcess.SOLID_GMAW,
    val wireSizeMm: Double? = null,
    val notes: String? = null,
) {
    /** Minimum required tag: brand must be present before the form can be saved. */
    val isComplete: Boolean get() = brand.isNotBlank()
}

/** The tags carried by an existing entity, for prefilling the edit form. */
fun WireDatasheet.toTags(): WireTags = WireTags(
    brand = brand,
    productName = productName,
    awsClassification = awsClassification,
    metalType = metalType,
    process = process,
    wireSizeMm = wireSizeMm,
    notes = notes,
)

/** Applies [tags] on top of this entity, touching metadata only — never the file. */
fun WireDatasheet.withTags(tags: WireTags, now: Long): WireDatasheet = copy(
    brand = tags.brand.trim(),
    productName = tags.productName.trim(),
    awsClassification = tags.awsClassification.trim().uppercase(),
    metalType = tags.metalType,
    process = tags.process,
    wireSizeMm = tags.wireSizeMm,
    notes = tags.notes?.trim()?.takeIf { it.isNotEmpty() },
    updatedAt = now,
)

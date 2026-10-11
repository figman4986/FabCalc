package com.figapps.fractioncalculatorv1.domain.convert

import com.figapps.fractioncalculatorv1.domain.Fraction

/**
 * A physical quantity the unit converter can work in.
 *
 * [id] is a stable key used to persist the user's last-used units per category and to tag history
 * records; [title] is the label shown in the category grid; [glyph] is a short text mark used as the
 * category "icon" (kept as text so the app stays free of an extended icon dependency).
 */
enum class Quantity(val id: String, val title: String, val glyph: String) {
    LENGTH("length", "Length", "\u2194"),
    MASS("mass", "Mass", "kg"),
    TEMPERATURE("temperature", "Temperature", "\u00B0C"),
    PRESSURE("pressure", "Pressure", "psi"),
    FORCE("force", "Force", "N"),
    TORQUE("torque", "Torque", "N\u00B7m"),
    AREA("area", "Area", "m\u00B2"),
    VOLUME("volume", "Volume", "L"),
    ANGLE("angle", "Angle", "\u00B0"),
    GAUGE("gauge", "Sheet Metal Gauge", "#");
}

/**
 * Extra display a unit's converted value gets, on top of the plain decimal:
 *  - [PLAIN] — decimal only.
 *  - [INCH_FRACTION] — also show the value as a workshop fraction (uses the global precision).
 *  - [FEET_INCHES] — also show the value split into feet + inches.
 *  - [DEGREES_DMS] — also show the value as degrees / minutes / seconds.
 */
enum class UnitDisplay { PLAIN, INCH_FRACTION, FEET_INCHES, DEGREES_DMS }

/**
 * One unit within a quantity.
 *
 * [factor] is how many base units one of this unit equals and [offset] shifts the value first, so a
 * value maps to the quantity's base unit as `value * factor + offset` and back as
 * `(base - offset) / factor`. The offset is non-zero only for affine scales such as temperature.
 *
 * Factors are exact [Fraction]s (rational), so factor-based conversions round-trip without any
 * floating-point drift.
 */
data class UnitDef(
    val id: String,
    val name: String,
    val symbol: String,
    val factor: Fraction,
    val offset: Fraction = Fraction.ZERO,
    val display: UnitDisplay = UnitDisplay.PLAIN,
) {
    /** This unit's value expressed in the quantity's base unit. */
    fun toBase(value: Fraction): Fraction = value * factor + offset

    /** A base-unit value expressed in this unit. */
    fun fromBase(base: Fraction): Fraction =
        if (offset.isZero) base / factor else (base - offset) / factor
}

/** A conversion category: an ordered list of units plus which two units start in the hero cards. */
data class ConverterCategory(
    val quantity: Quantity,
    val units: List<UnitDef>,
    val defaultFromId: String,
    val defaultToId: String,
) {
    fun unit(id: String): UnitDef? = units.firstOrNull { it.id == id }
}

/** The unit pair the user last used for a category (persisted in settings). */
data class UnitSelection(val fromId: String, val toId: String)

/** Converts a value from one unit to another via the quantity's base unit. */
object ConverterEngine {
    fun convert(value: Fraction, from: UnitDef, to: UnitDef): Fraction =
        to.fromBase(from.toBase(value))
}

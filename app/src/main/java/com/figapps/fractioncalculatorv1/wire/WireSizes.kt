package com.figapps.fractioncalculatorv1.wire

import com.figapps.fractioncalculatorv1.domain.LengthUnit
import java.util.Locale

/**
 * Canonical wire-size handling. Everything is stored in millimetres regardless of the unit the user
 * typed; the size is converted for display per the global unit setting.
 */
object WireSizes {
    const val MILLIMETRES_PER_INCH = 25.4

    /** Common solid-wire diameters in inches. */
    val COMMON_INCHES = listOf(0.023, 0.030, 0.035, 0.045, 0.052)

    /** The same shop sizes in millimetres. */
    val COMMON_MILLIMETRES = listOf(0.8, 0.9, 1.0, 1.2, 1.6)

    fun inchesToMillimetres(inches: Double): Double = inches * MILLIMETRES_PER_INCH

    fun millimetresToInches(millimetres: Double): Double = millimetres / MILLIMETRES_PER_INCH

    /** The canonical size as it reads in the active unit, or null when unspecified. */
    fun display(millimetres: Double?, unit: LengthUnit): String? = millimetres?.let {
        when (unit) {
            LengthUnit.INCHES -> "${trim(millimetresToInches(it), 3)} in"
            LengthUnit.MILLIMETERS -> "${trim(it, 2)} mm"
        }
    }

    /** The common sizes as they read in [unit], paired with their canonical millimetre value. */
    fun commonOptions(unit: LengthUnit): List<Pair<String, Double>> = when (unit) {
        LengthUnit.INCHES -> COMMON_INCHES.map { "${trim(it, 3)} in" to inchesToMillimetres(it) }
        LengthUnit.MILLIMETERS -> COMMON_MILLIMETRES.map { "${trim(it, 2)} mm" to it }
    }

    private fun trim(value: Double, places: Int): String {
        val text = String.format(Locale.US, "%.${places}f", value)
        return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    }
}

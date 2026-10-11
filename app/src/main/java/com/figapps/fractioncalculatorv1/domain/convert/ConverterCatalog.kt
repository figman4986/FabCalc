package com.figapps.fractioncalculatorv1.domain.convert

import com.figapps.fractioncalculatorv1.domain.Fraction

/**
 * Every conversion category the app knows about, declared as data.
 *
 * Each quantity has one base unit; every unit carries an exact rational `factor` (and, for affine
 * scales like temperature, an `offset`). Conversion is `base = v * factor + offset`, so adding a
 * unit is a one-line data change and never touches the maths.
 *
 * Factors are the exact definitions wherever one exists (an inch is exactly 0.0254 m, a pound is
 * exactly 0.45359237 kg, and so on) so results round-trip without floating-point error.
 */
object ConverterCatalog {

    private fun d(text: String) = Fraction.fromDecimal(text)
    private fun ratio(numerator: Long, denominator: Long) = Fraction.of(numerator, denominator)
    private val ONE = Fraction.ONE

    // --- shared exact building blocks -----------------------------------------------------------

    /** 1 lb-force = 0.45359237 kg × 9.80665 m/s² exactly. */
    private val POUND_FORCE = d("4.4482216152605")

    /** 1 in² in m². */
    private val SQUARE_INCH = d("0.00064516")

    /** 1 psi in Pa (lbf / in²). */
    private val PSI = POUND_FORCE / SQUARE_INCH

    /** π as a rational, reused from the rest of the app. */
    private val PI = Fraction.PI

    /** 1 atmosphere in Pa (exact). */
    private val ATMOSPHERE = d("101325")

    /** 1 US gallon in m³ (exact). */
    private val US_GALLON = d("0.003785411784")

    // --- categories -----------------------------------------------------------------------------

    private val length = ConverterCategory(
        quantity = Quantity.LENGTH,
        defaultFromId = "mm",
        defaultToId = "in",
        units = listOf(
            UnitDef("mm", "Millimetre", "mm", d("0.001")),
            UnitDef("cm", "Centimetre", "cm", d("0.01")),
            UnitDef("m", "Metre", "m", ONE),
            UnitDef("km", "Kilometre", "km", d("1000")),
            UnitDef("in", "Inch", "in", d("0.0254"), display = UnitDisplay.INCH_FRACTION),
            UnitDef("ft", "Foot", "ft", d("0.3048"), display = UnitDisplay.FEET_INCHES),
            UnitDef("yd", "Yard", "yd", d("0.9144")),
            UnitDef("mi", "Mile", "mi", d("1609.344")),
            UnitDef("thou", "Thou (mil)", "thou", d("0.0000254")),
            UnitDef("um", "Micrometre (micron)", "\u00B5m", d("0.000001")),
        ),
    )

    private val mass = ConverterCategory(
        quantity = Quantity.MASS,
        defaultFromId = "kg",
        defaultToId = "lb",
        units = listOf(
            UnitDef("mg", "Milligram", "mg", d("0.000001")),
            UnitDef("g", "Gram", "g", d("0.001")),
            UnitDef("kg", "Kilogram", "kg", ONE),
            UnitDef("t", "Metric ton", "t", d("1000")),
            UnitDef("oz", "Ounce", "oz", d("0.028349523125")),
            UnitDef("lb", "Pound", "lb", d("0.45359237")),
            UnitDef("uston", "US ton", "ton", d("907.18474")),
            UnitDef("grain", "Grain", "gr", d("0.00006479891")),
        ),
    )

    private val temperature = ConverterCategory(
        quantity = Quantity.TEMPERATURE,
        defaultFromId = "c",
        defaultToId = "f",
        units = listOf(
            // Base unit is the kelvin; the Celsius/Fahrenheit scales are affine.
            UnitDef("c", "Celsius", "\u00B0C", ONE, offset = d("273.15")),
            UnitDef("f", "Fahrenheit", "\u00B0F", ratio(5, 9), offset = ratio(45967, 180)),
            UnitDef("k", "Kelvin", "K", ONE),
            UnitDef("r", "Rankine", "\u00B0R", ratio(5, 9)),
        ),
    )

    private val pressure = ConverterCategory(
        quantity = Quantity.PRESSURE,
        defaultFromId = "mpa",
        defaultToId = "psi",
        units = listOf(
            UnitDef("pa", "Pascal", "Pa", ONE),
            UnitDef("kpa", "Kilopascal", "kPa", d("1000")),
            UnitDef("mpa", "Megapascal", "MPa", d("1000000")),
            UnitDef("bar", "Bar", "bar", d("100000")),
            UnitDef("psi", "Pound per square inch", "psi", PSI),
            UnitDef("ksi", "Kip per square inch", "ksi", PSI * d("1000")),
            UnitDef("atm", "Atmosphere", "atm", ATMOSPHERE),
            UnitDef("torr", "Torr (mmHg)", "torr", ATMOSPHERE / d("760")),
        ),
    )

    private val force = ConverterCategory(
        quantity = Quantity.FORCE,
        defaultFromId = "n",
        defaultToId = "lbf",
        units = listOf(
            UnitDef("n", "Newton", "N", ONE),
            UnitDef("kn", "Kilonewton", "kN", d("1000")),
            UnitDef("lbf", "Pound-force", "lbf", POUND_FORCE),
            UnitDef("kgf", "Kilogram-force", "kgf", d("9.80665")),
            UnitDef("dyn", "Dyne", "dyn", d("0.00001")),
        ),
    )

    private val torque = ConverterCategory(
        quantity = Quantity.TORQUE,
        defaultFromId = "nm",
        defaultToId = "lbfft",
        units = listOf(
            UnitDef("nm", "Newton metre", "N\u00B7m", ONE),
            UnitDef("ncm", "Newton centimetre", "N\u00B7cm", d("0.01")),
            UnitDef("lbfft", "Pound-force foot", "lbf\u00B7ft", POUND_FORCE * d("0.3048")),
            UnitDef("lbfin", "Pound-force inch", "lbf\u00B7in", POUND_FORCE * d("0.0254")),
            UnitDef("kgfm", "Kilogram-force metre", "kgf\u00B7m", d("9.80665")),
        ),
    )

    private val area = ConverterCategory(
        quantity = Quantity.AREA,
        defaultFromId = "mm2",
        defaultToId = "in2",
        units = listOf(
            UnitDef("mm2", "Square millimetre", "mm\u00B2", d("0.000001")),
            UnitDef("cm2", "Square centimetre", "cm\u00B2", d("0.0001")),
            UnitDef("m2", "Square metre", "m\u00B2", ONE),
            UnitDef("in2", "Square inch", "in\u00B2", SQUARE_INCH),
            UnitDef("ft2", "Square foot", "ft\u00B2", d("0.09290304")),
            UnitDef("yd2", "Square yard", "yd\u00B2", d("0.83612736")),
        ),
    )

    private val volume = ConverterCategory(
        quantity = Quantity.VOLUME,
        defaultFromId = "l",
        defaultToId = "gal",
        units = listOf(
            UnitDef("ml", "Millilitre", "mL", d("0.000001")),
            UnitDef("l", "Litre", "L", d("0.001")),
            UnitDef("m3", "Cubic metre", "m\u00B3", ONE),
            UnitDef("in3", "Cubic inch", "in\u00B3", d("0.000016387064")),
            UnitDef("ft3", "Cubic foot", "ft\u00B3", d("0.028316846592")),
            UnitDef("floz", "US fluid ounce", "fl oz", d("0.0000295735295625")),
            UnitDef("gal", "US gallon", "gal", US_GALLON),
            UnitDef("qt", "US quart", "qt", US_GALLON / d("4")),
        ),
    )

    private val angle = ConverterCategory(
        quantity = Quantity.ANGLE,
        defaultFromId = "deg",
        defaultToId = "rad",
        units = listOf(
            UnitDef("deg", "Degree", "\u00B0", PI / d("180"), display = UnitDisplay.DEGREES_DMS),
            UnitDef("rad", "Radian", "rad", ONE),
            UnitDef("grad", "Gradian", "grad", PI / d("200")),
            UnitDef("rev", "Revolution", "rev", PI * d("2")),
        ),
    )

    /** All factor-based categories, in the order shown on the landing grid (gauge is handled last). */
    val categories: List<ConverterCategory> = listOf(
        length, mass, temperature, pressure, force, torque, area, volume, angle,
    )

    fun category(id: String): ConverterCategory? = categories.firstOrNull { it.quantity.id == id }

    fun allQuantityIds(): List<String> = categories.map { it.quantity.id } + Quantity.GAUGE.id
}

package com.figapps.fractioncalculatorv1.domain.convert

import com.figapps.fractioncalculatorv1.domain.Fraction

/**
 * One material's sheet-gauge table. [inches] maps a gauge number to its thickness in inches as an
 * exact [Fraction]; gauge numbers are NOT linear (each step is a different decrement) and the tables
 * differ per material, which is why each material carries its own map.
 */
data class GaugeMaterial(
    val id: String,
    val name: String,
    val shortName: String,
    val inches: Map<Int, Fraction>,
)

/**
 * Published sheet-metal gauge thickness tables, used as a shop lookup.
 *
 * Values are the standard published thicknesses in inches for the three common materials. Gauge
 * numbers run 7 (thickest) to 36 (thinnest); the millimetre and fraction equivalents are derived
 * from the inch figures when displayed, never stored separately.
 *
 * Sources: "Standard Gage for Sheet Steel" (steel), Brown & Sharpe / aluminium sheet gauge
 * (aluminium), and the commonly published galvanized sheet thickness table (galvanized).
 */
object SheetMetalGauge {

    /** Gauge numbers shown, thickest first (7) to thinnest last (36). */
    val gaugeNumbers: List<Int> = (7..36).toList()

    private fun d(text: String) = Fraction.fromDecimal(text)

    val steel = GaugeMaterial(
        id = "steel",
        name = "US Steel (standard)",
        shortName = "Steel",
        inches = mapOf(
            7 to d("0.1793"), 8 to d("0.1644"), 9 to d("0.1495"), 10 to d("0.1345"),
            11 to d("0.1196"), 12 to d("0.1046"), 13 to d("0.0897"), 14 to d("0.0747"),
            15 to d("0.0673"), 16 to d("0.0598"), 17 to d("0.0538"), 18 to d("0.0478"),
            19 to d("0.0418"), 20 to d("0.0359"), 21 to d("0.0329"), 22 to d("0.0299"),
            23 to d("0.0269"), 24 to d("0.0239"), 25 to d("0.0209"), 26 to d("0.0179"),
            27 to d("0.0164"), 28 to d("0.0149"), 29 to d("0.0135"), 30 to d("0.0120"),
            31 to d("0.0105"), 32 to d("0.0097"), 33 to d("0.0090"), 34 to d("0.0082"),
            35 to d("0.0075"), 36 to d("0.0067"),
        ),
    )

    val aluminum = GaugeMaterial(
        id = "aluminum",
        name = "Aluminium (Brown & Sharpe)",
        shortName = "Aluminium",
        inches = mapOf(
            7 to d("0.1443"), 8 to d("0.1285"), 9 to d("0.1144"), 10 to d("0.1019"),
            11 to d("0.0907"), 12 to d("0.0808"), 13 to d("0.0720"), 14 to d("0.0641"),
            15 to d("0.0571"), 16 to d("0.0508"), 17 to d("0.0453"), 18 to d("0.0403"),
            19 to d("0.0359"), 20 to d("0.0320"), 21 to d("0.0285"), 22 to d("0.0253"),
            23 to d("0.0226"), 24 to d("0.0201"), 25 to d("0.0179"), 26 to d("0.0159"),
            27 to d("0.0142"), 28 to d("0.0126"), 29 to d("0.0113"), 30 to d("0.0100"),
            31 to d("0.0089"), 32 to d("0.0080"), 33 to d("0.0071"), 34 to d("0.0063"),
            35 to d("0.0056"), 36 to d("0.0051"),
        ),
    )

    val galvanized = GaugeMaterial(
        id = "galvanized",
        name = "Galvanized steel",
        shortName = "Galvanized",
        inches = mapOf(
            7 to d("0.0950"), 8 to d("0.0875"), 9 to d("0.0805"), 10 to d("0.0740"),
            11 to d("0.0680"), 12 to d("0.0625"), 13 to d("0.0575"), 14 to d("0.0530"),
            15 to d("0.0480"), 16 to d("0.0438"), 17 to d("0.0400"), 18 to d("0.0368"),
            19 to d("0.0338"), 20 to d("0.0312"), 21 to d("0.0288"), 22 to d("0.0263"),
            23 to d("0.0240"), 24 to d("0.0220"), 25 to d("0.0200"), 26 to d("0.0180"),
            27 to d("0.0164"), 28 to d("0.0149"), 29 to d("0.0132"), 30 to d("0.0120"),
            31 to d("0.0110"), 32 to d("0.0102"), 33 to d("0.0094"), 34 to d("0.0086"),
            35 to d("0.0078"), 36 to d("0.0070"),
        ),
    )

    val materials: List<GaugeMaterial> = listOf(steel, aluminum, galvanized)

    /** 1 inch in millimetres (exact). */
    private val INCH_IN_MM = Fraction.fromDecimal("25.4")

    fun material(id: String): GaugeMaterial? = materials.firstOrNull { it.id == id }

    /** Thickness of [gauge] in [material] expressed in millimetres, or null if unknown. */
    fun thicknessMm(material: GaugeMaterial, gauge: Int): Fraction? =
        material.inches[gauge]?.let { it * INCH_IN_MM }
}

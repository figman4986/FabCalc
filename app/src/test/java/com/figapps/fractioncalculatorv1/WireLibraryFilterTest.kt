package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.wire.MetalType
import com.figapps.fractioncalculatorv1.wire.WireDatasheet
import com.figapps.fractioncalculatorv1.wire.WireLibraryFilter
import com.figapps.fractioncalculatorv1.wire.WireProcess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exhaustive coverage of the pure filtering/sorting behind the wire library browse screen. */
class WireLibraryFilterTest {

    /** One row for every metal type × process pair, so every combination can be exercised. */
    private val grid: List<WireDatasheet> = buildList {
        var id = 1L
        for (metal in MetalType.entries) {
            for (process in WireProcess.entries) {
                add(sheet(id, metal = metal, process = process))
                id++
            }
        }
    }

    // --- single-select enum filters -------------------------------------------------------------

    @Test
    fun everyMetalTypeCanBeSelectedIndividually() {
        for (metal in MetalType.entries) {
            val result = WireLibraryFilter(metalType = metal).apply(grid)
            assertTrue("metal=$metal returned nothing", result.isNotEmpty())
            assertTrue("metal=$metal leaked other types", result.all { it.metalType == metal })
            assertEquals(
                "metal=$metal count",
                WireProcess.entries.size,
                result.size,
            )
        }
    }

    @Test
    fun everyProcessCanBeSelectedIndividually() {
        for (process in WireProcess.entries) {
            val result = WireLibraryFilter(process = process).apply(grid)
            assertTrue("process=$process returned nothing", result.isNotEmpty())
            assertTrue("process=$process leaked other processes", result.all { it.process == process })
            assertEquals("process=$process count", MetalType.entries.size, result.size)
        }
    }

    @Test
    fun everyMetalTypeAndProcessCombinationSelectsExactlyTheMatchingRows() {
        for (metal in MetalType.entries) {
            for (process in WireProcess.entries) {
                val result = WireLibraryFilter(metalType = metal, process = process).apply(grid)
                val expected = grid.filter { it.metalType == metal && it.process == process }
                assertEquals("metal=$metal process=$process", 1, expected.size)
                assertEquals("metal=$metal process=$process", expected.map { it.id }, result.map { it.id })
            }
        }
    }

    @Test
    fun neitherFilterSelectedKeepsEverything() {
        val result = WireLibraryFilter().apply(grid)
        assertEquals(grid.size, result.size)
        assertEquals(grid.map { it.id }.sorted(), result.map { it.id }.sorted())
    }

    @Test
    fun aCombinationWithNoRowsIsEmpty() {
        val single = listOf(sheet(1, metal = MetalType.STAINLESS, process = WireProcess.SOLID_GMAW))
        assertTrue(
            WireLibraryFilter(metalType = MetalType.STAINLESS, process = WireProcess.FLUX_CORE)
                .apply(single)
                .isEmpty()
        )
    }

    // --- search ---------------------------------------------------------------------------------

    private val library = listOf(
        sheet(1, brand = "Lincoln", product = "UltraCore", aws = "E71T-1", metal = MetalType.CARBON_STEEL, process = WireProcess.FLUX_CORE, sizeMm = 1.2),
        sheet(2, brand = "ESAB", product = "Arcaloy", aws = "ER308L", metal = MetalType.STAINLESS, process = WireProcess.SOLID_GMAW, sizeMm = 0.9),
        sheet(3, brand = "Hobart", product = "FabCo", aws = "ER70S-6", metal = MetalType.CARBON_STEEL, process = WireProcess.SOLID_GMAW, sizeMm = 0.9),
        sheet(4, brand = "Lincoln", product = "BlueMax", aws = "ER70S-3", metal = MetalType.CARBON_STEEL, process = WireProcess.SOLID_GMAW, sizeMm = null),
        sheet(5, brand = "AlcoTec", product = "AlumaPower", aws = "ER4043", metal = MetalType.ALUMINUM, process = WireProcess.SOLID_GMAW, sizeMm = 1.2),
    )

    @Test
    fun searchMatchesBrand() {
        assertEquals(listOf(1L, 4L), WireLibraryFilter(searchText = "lincoln").apply(library).map { it.id })
        assertEquals(listOf(1L, 4L), WireLibraryFilter(searchText = "LINCOLN").apply(library).map { it.id })
        assertEquals(listOf(1L, 4L), WireLibraryFilter(searchText = "incol").apply(library).map { it.id })
    }

    @Test
    fun searchMatchesProductName() {
        assertEquals(listOf(5L), WireLibraryFilter(searchText = "alumapower").apply(library).map { it.id })
        assertEquals(listOf(5L), WireLibraryFilter(searchText = "ALUMA").apply(library).map { it.id })
    }

    @Test
    fun searchMatchesAwsClassification() {
        assertEquals(listOf(2L), WireLibraryFilter(searchText = "er308l").apply(library).map { it.id })
        assertEquals(listOf(1L), WireLibraryFilter(searchText = "e71t").apply(library).map { it.id })
    }

    @Test
    fun blankSearchKeepsEverything() {
        assertEquals(library.size, WireLibraryFilter(searchText = "").apply(library).size)
        assertEquals(library.size, WireLibraryFilter(searchText = "   ").apply(library).size)
    }

    @Test
    fun searchIsTrimmedBeforeMatching() {
        assertEquals(listOf(5L), WireLibraryFilter(searchText = "  aluma  ").apply(library).map { it.id })
    }

    @Test
    fun searchThatMatchesNothingReturnsEmpty() {
        assertTrue(WireLibraryFilter(searchText = "zzz-no-such-wire").apply(library).isEmpty())
    }

    @Test
    fun searchCombinesWithBothEnumFilters() {
        // "er70" matches Hobart ER70S-6 (solid) and Lincoln ER70S-3 (solid); the flux-core row is out.
        val solidCarbon = WireLibraryFilter(
            searchText = "er70",
            metalType = MetalType.CARBON_STEEL,
            process = WireProcess.SOLID_GMAW,
        ).apply(library)
        assertEquals(listOf(3L, 4L), solidCarbon.map { it.id })

        // Same search but the wrong process yields nothing.
        assertTrue(
            WireLibraryFilter(searchText = "er70", metalType = MetalType.CARBON_STEEL, process = WireProcess.FLUX_CORE)
                .apply(library)
                .isEmpty()
        )

        // Same search but the wrong metal yields nothing.
        assertTrue(
            WireLibraryFilter(searchText = "er70", metalType = MetalType.ALUMINUM)
                .apply(library)
                .isEmpty()
        )
    }

    @Test
    fun matchesIsTrueOnlyForRowsThatSurviveEveryFilter() {
        assertTrue(WireLibraryFilter().matches(library.first()))
        assertFalse(WireLibraryFilter(metalType = MetalType.NICKEL).matches(library.first()))
        assertFalse(WireLibraryFilter(searchText = "esab").matches(library.first()))
    }

    // --- sorting --------------------------------------------------------------------------------

    @Test
    fun sortingIsMetalTypeAscendingThenSizeAscendingWithUnknownSizesLast() {
        val sorted = WireLibraryFilter.sortForLibrary(library)
        assertEquals(
            listOf(
                MetalType.CARBON_STEEL,
                MetalType.CARBON_STEEL,
                MetalType.CARBON_STEEL,
                MetalType.STAINLESS,
                MetalType.ALUMINUM,
            ),
            sorted.map { it.metalType },
        )
        // Within carbon steel: 0.9, 1.2, then the row with no size.
        assertEquals(listOf(3L, 1L, 4L), sorted.take(3).map { it.id })
    }

    @Test
    fun sortingIsStableAcrossEveryMetalType() {
        val sorted = WireLibraryFilter.sortForLibrary(grid)
        val ordinals = sorted.map { it.metalType.ordinal }
        assertEquals(ordinals.sorted(), ordinals)
    }

    @Test
    fun groupingIsMetalTypeAscendingWithProductsAlphabeticalInsideAGroup() {
        val groups = WireLibraryFilter.group(WireLibraryFilter().apply(library))
        assertEquals(
            listOf(MetalType.CARBON_STEEL, MetalType.STAINLESS, MetalType.ALUMINUM),
            groups.map { it.metalType },
        )
        assertEquals(listOf("BlueMax", "FabCo", "UltraCore"), groups.first().entries.map { it.productName })
        assertEquals(listOf("UltraCore", "FabCo", "BlueMax"), groups.first().entries.map { it.productName }.sortedDescending())
    }

    @Test
    fun groupingIsNotAffectedByTheSortOrderOfTheInput() {
        val forward = WireLibraryFilter.group(library)
        val backward = WireLibraryFilter.group(library.reversed())
        assertEquals(forward.map { it.metalType }, backward.map { it.metalType })
        assertEquals(
            forward.flatMap { group -> group.entries.map { it.id } },
            backward.flatMap { group -> group.entries.map { it.id } },
        )
    }

    @Test
    fun anEmptyLibraryYieldsNoGroups() {
        assertTrue(WireLibraryFilter().apply(emptyList()).isEmpty())
        assertTrue(WireLibraryFilter.group(emptyList()).isEmpty())
    }

    private fun sheet(
        id: Long,
        brand: String = "Brand$id",
        product: String = "Product$id",
        aws: String = "ER70S-6",
        metal: MetalType = MetalType.CARBON_STEEL,
        process: WireProcess = WireProcess.SOLID_GMAW,
        sizeMm: Double? = 1.0,
    ) = WireDatasheet(
        id = id,
        fileName = "f$id.pdf",
        sha256 = "hash$id",
        brand = brand,
        productName = product,
        awsClassification = aws,
        metalType = metal,
        process = process,
        wireSizeMm = sizeMm,
        notes = null,
        createdAt = id,
        updatedAt = id,
    )
}

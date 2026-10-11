package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.wire.MetalType
import com.figapps.fractioncalculatorv1.wire.WireDatasheet
import com.figapps.fractioncalculatorv1.wire.WireProcess
import com.figapps.fractioncalculatorv1.wire.duplicateCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The pure "have we already imported this PDF?" decision that stops a second copy of the same
 * datasheet being stored. No Android, no files: just the hash and a candidate row.
 */
class DuplicateDecisionTest {

    @Test
    fun returnsTheExistingRowWhenTheHashMatches() {
        val existing = sheet(id = 7, hash = "abc123")
        assertSame(existing, duplicateCheck("abc123", existing))
    }

    @Test
    fun hashComparisonIgnoresCase() {
        val existing = sheet(id = 7, hash = "ABCDEF")
        assertSame(existing, duplicateCheck("abcdef", existing))
        assertSame(existing, duplicateCheck("AbCdEf", existing))
    }

    @Test
    fun returnsNullWhenTheHashIsNew() {
        assertNull(duplicateCheck("brand-new", sheet(id = 7, hash = "something-else")))
    }

    @Test
    fun returnsNullWhenTheLibraryIsEmpty() {
        assertNull(duplicateCheck("abc123", null))
    }

    @Test
    fun anEmptyHashNeverMatchesABlankStoredHash() {
        assertNull(duplicateCheck("", sheet(id = 1, hash = "abc")))
        assertNull(duplicateCheck("abc", sheet(id = 1, hash = "")))
    }

    @Test
    fun onlyTheRowWithTheMatchingHashIsEverReturned() {
        val library = listOf(
            sheet(id = 1, hash = "aaa"),
            sheet(id = 2, hash = "bbb"),
            sheet(id = 3, hash = "ccc"),
        )
        for (candidate in library) {
            val hit = duplicateCheck("bbb", candidate)
            if (candidate.sha256 == "bbb") {
                assertSame(candidate, hit)
            } else {
                assertNull(hit)
            }
        }
    }

    @Test
    fun theDecisionIsDrivenByTheHashAndNotByTheTags() {
        val first = sheet(id = 1, hash = "same", brand = "Lincoln", product = "UltraCore")
        val second = sheet(id = 2, hash = "same", brand = "ESAB", product = "Arcaloy")
        assertSame(first, duplicateCheck("same", first))
        assertSame(second, duplicateCheck("same", second))
        assertEquals(1L, duplicateCheck("same", first)?.id)
    }

    private fun sheet(
        id: Long,
        hash: String,
        brand: String = "Brand",
        product: String = "Product",
    ) = WireDatasheet(
        id = id,
        fileName = "f$id.pdf",
        sha256 = hash,
        brand = brand,
        productName = product,
        awsClassification = "ER70S-6",
        metalType = MetalType.CARBON_STEEL,
        process = WireProcess.SOLID_GMAW,
        wireSizeMm = 0.9,
        notes = null,
        createdAt = 0,
        updatedAt = 0,
    )
}

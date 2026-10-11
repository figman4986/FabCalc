package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.wire.WireHashing
import com.figapps.fractioncalculatorv1.wire.hexDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** SHA-256 stability — the substrate the duplicate check rests on. Pure JVM, needs no Android. */
class WireHashingTest {

    @Test
    fun knownVectorForAbc() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            WireHashing.sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun knownVectorForEmptyInput() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            WireHashing.sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun theSameBytesAlwaysHashToTheSameValue() {
        assertEquals(
            WireHashing.sha256Hex("%PDF-1.7 a datasheet".toByteArray()),
            WireHashing.sha256Hex("%PDF-1.7 a datasheet".toByteArray()),
        )
    }

    @Test
    fun differentBytesHashDifferently() {
        assertNotEquals(
            WireHashing.sha256Hex("datasheet A".toByteArray()),
            WireHashing.sha256Hex("datasheet B".toByteArray()),
        )
    }

    @Test
    fun aSingleChangedByteChangesTheHash() {
        assertNotEquals(
            WireHashing.sha256Hex("%PDF-1.7".toByteArray()),
            WireHashing.sha256Hex("%PDF-1.8".toByteArray()),
        )
    }

    @Test
    fun hashesAreLowercase64CharacterHex() {
        val hex = WireHashing.sha256Hex("anything".toByteArray())
        assertEquals(64, hex.length)
        assertEquals(hex, hex.lowercase())
        assertEquals(hex, hex.filter { it in "0123456789abcdef" })
    }

    /** The importer hashes *while* copying in 64 KiB chunks; that must equal the one-shot digest. */
    @Test
    fun streamingDigestMatchesTheOneShotDigest() {
        val payload = ByteArray(200_000) { index -> (index % 251).toByte() }
        val digest = WireHashing.digest()
        var offset = 0
        while (offset < payload.size) {
            val length = minOf(64 * 1024, payload.size - offset)
            digest.update(payload, offset, length)
            offset += length
        }
        assertEquals(WireHashing.sha256Hex(payload), digest.hexDigest())
    }
}

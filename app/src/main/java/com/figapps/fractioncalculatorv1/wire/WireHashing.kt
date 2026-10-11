package com.figapps.fractioncalculatorv1.wire

import java.security.MessageDigest

/**
 * SHA-256 helpers backing datasheet de-duplication. Pure JVM, so the hashing that a duplicate
 * check relies on is unit-testable without Android.
 */
object WireHashing {

    /** A fresh SHA-256 digest; callers stream bytes in with [MessageDigest.update]. */
    fun digest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    /** Hex digest of [bytes] (lower-case, 64 characters). */
    fun sha256Hex(bytes: ByteArray): String = digest().apply { update(bytes) }.hexDigest()
}

/** The lower-case hex form of this digest's result. */
fun MessageDigest.hexDigest(): String = digest().joinToString("") { byte -> "%02x".format(byte) }

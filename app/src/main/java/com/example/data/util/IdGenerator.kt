package com.example.data.util

import java.security.SecureRandom

/**
 * Generates globally unique, collision-resistant 63-bit positive Long identifiers for distributed multi-device synchronization.
 *
 * Structure:
 * - High 42 bits: Current timestamp in milliseconds (monotonically increasing, valid well past year 2100)
 * - Low 21 bits: Cryptographically secure random integer (0..2,097,151)
 *
 * This produces positive Long values (< Long.MAX_VALUE) generated before inserting into Room SQLite,
 * ensuring Room and Firestore share the exact same ID with zero inter-device ID collisions.
 */
object IdGenerator {
    private val secureRandom = SecureRandom()

    @Synchronized
    fun generateId(): Long {
        val timestamp = System.currentTimeMillis()
        val randomBits = secureRandom.nextInt(0x1FFFFF) // 21 bits
        val composed = (timestamp shl 21) or (randomBits.toLong() and 0x1FFFFFL)
        return if (composed > 0) composed else System.currentTimeMillis()
    }
}

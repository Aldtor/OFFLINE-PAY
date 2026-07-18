package com.offlinepay.core.common.uuid

import java.util.UUID

/**
 * Generator for UUID v4 identifiers used throughout OfflinePay.
 *
 * Wraps [UUID.randomUUID] to allow clock/random injection in tests,
 * enabling deterministic test scenarios for transaction IDs and other UUIDs.
 *
 * Design reference: Section 3.5 (`:core:common`)
 *
 * ### Production usage
 * Inject the default implementation provided by the Hilt `CommonModule`:
 * ```kotlin
 * @Inject lateinit var uuidGenerator: UuidGenerator
 * val transactionId = uuidGenerator.generate()
 * ```
 *
 * ### Test usage
 * ```kotlin
 * val generator = UuidGenerator { "fixed-uuid-for-test" }
 * ```
 */
fun interface UuidGenerator {

    /**
     * Generates a new unique identifier string.
     *
     * @return A unique string identifier (UUID v4 format by default).
     */
    fun generate(): String
}

/**
 * Default [UuidGenerator] implementation that produces random UUID v4 strings.
 *
 * This is the implementation bound in production via Hilt's `CommonModule`.
 */
val DefaultUuidGenerator: UuidGenerator = UuidGenerator {
    UUID.randomUUID().toString()
}

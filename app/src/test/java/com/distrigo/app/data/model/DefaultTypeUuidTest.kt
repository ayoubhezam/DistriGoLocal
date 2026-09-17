package com.distrigo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The built-in types' uuids, pinned. Every phone, and MIGRATION_49_50, relies on these never changing:
 * a different value would make two phones' "Casse" two different types again. The expected values were
 * computed outside Kotlin, as an MD5 name-based (version 3) UUID of each string.
 */
class DefaultTypeUuidTest {

    @Test
    fun theFormulaNeverChanges() {
        assertEquals("beaf2e79-d096-3af8-a336-8b5595b7bde4", DefaultPerteType.CASSE.uuid)
        assertEquals("b1599331-8261-3a09-8ed0-9ea9a333288a", defaultChargeTypeUuid("vehicule"))
        assertEquals("f80be342-ce94-3f9f-89a7-cb421add8872", defaultChargeSubTypeUuid("achats", "divers"))
        assertEquals("21ac2d85-5b38-37cd-9d0d-9cd6b5e09efd", com.distrigo.app.data.local.entity.BUSINESS_SETTINGS_UUID)
    }

    /** "Divers" is a type and a subtype of Achats; they are different built-ins. */
    @Test
    fun aTypeAndASubtypeOfTheSameNameDiffer() {
        assertNotEquals(defaultChargeTypeUuid("divers"), defaultChargeSubTypeUuid("achats", "divers"))
    }

    @Test
    fun everyBuiltInPerteTypeIsDistinct() {
        assertEquals(DefaultPerteType.entries.size, DefaultPerteType.entries.map { it.uuid }.toSet().size)
    }
}

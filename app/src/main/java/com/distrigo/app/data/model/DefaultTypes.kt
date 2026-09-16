package com.distrigo.app.data.model

import java.util.UUID

/**
 * The identity every phone gives the same built-in type.
 *
 * Built-in charge types, charge subtypes and perte types are seeded by each installation on its own.
 * With random uuids, two phones would each hold a "Casse" that a sync could only see as two different
 * types. A name-based UUID (version 3) of a fixed key instead gives "Casse" the same uuid everywhere,
 * so the two are recognisably one.
 *
 * The key, not the display name, feeds the UUID: a name can be re-worded, the key cannot.
 *
 * **Never change this function or an existing key.** Every phone's built-in types, and MIGRATION_49_50,
 * depend on them producing the same values forever; DefaultTypesTest pins one of them literally.
 */
fun defaultTypeUuid(kind: String, key: String): String =
    UUID.nameUUIDFromBytes("distrigo:$kind:$key".toByteArray(Charsets.UTF_8)).toString()

/** The built-in perte types, by stable key. [seedName] is the name they are created with. */
enum class DefaultPerteType(val key: String, val seedName: String) {
    CASSE("casse", "Casse"),
    PEREMPTION("peremption", "Péremption"),
    VOL("vol", "Vol"),
    PERTE_TRANSPORT("perte_transport", "Perte de transport"),
    DON("don", "Don"),
    AUTRE("autre", "Autre");

    val uuid: String get() = defaultTypeUuid(KIND, key)

    companion object {
        const val KIND = "perte_type"
    }
}

/** The uuid of a built-in charge type. */
fun defaultChargeTypeUuid(typeKey: String): String = defaultTypeUuid("charge_type", typeKey)

/** The uuid of a built-in charge subtype, which is only unique within its type. */
fun defaultChargeSubTypeUuid(typeKey: String, subTypeKey: String): String =
    defaultTypeUuid("charge_subtype", "$typeKey/$subTypeKey")

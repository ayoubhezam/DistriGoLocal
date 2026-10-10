package com.distrigo.app.data.license

import com.distrigo.app.BuildConfig
import java.security.PublicKey

/**
 * The server public keys a license may be signed with, compiled in: a key is never downloaded, or whoever
 * served it could sign licenses.
 *
 * Two release keys from the first release on (docs/license_architecture.md §4.3): the one in use and a
 * standby kept offline, so a leaked key is replaced by switching the server, without waiting for an update.
 */
internal object LicenseKeys {

    /**
     * SubjectPublicKeyInfo, base64, made by tools/license/make-release-keys.mjs on 2026-10-10. The server signs
     * with lic-2026-a; lic-2026-b is the standby, kept offline. Their private halves are in Supabase's secrets
     * and the owner's backups, never in this repository.
     */
    private val RELEASE: Map<String, String> = mapOf(
        "lic-2026-a" to "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEpGrfKZZswIf30J72OABdS8VMLlgvAL1xF3FoFiW8QDlODOyXcllV38KOppYePzDkqkgWA6imNPmpvFjjtDJ0/A==",
        "lic-2026-b" to "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEDl0N7wglend6eHAh7dOnc6mwQV0wYI4VyPlyYRUNwWAHcw/DUZ4mR4/bWXr8U7mggJCPX+4P+DEzBjhipMrZuQ==",
    )

    /**
     * Test keys, trusted by debug builds only. Their private halves are in tools/license/test-keys.json and
     * sign the golden tokens in the unit tests (tools/license/golden-tokens.mjs).
     */
    private val TEST: Map<String, String> = mapOf(
        "test-a" to "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEM3lyvzPnOiU+SgeeAEsd9czU02G7uhEkIXVgnLKd/dKwnCchVemr2dAq/L+qW0YLzZ7S8Ol2fwteoeo7MW70lA==",
        "test-b" to "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPhrg/L+P/QREDPoSvIGbnOP+40BR/vZTX76IhkCdXnj2hyuYzc+moznu8mxBX94T23/p5OvrwlwNqqCjZ1R3Jg==",
    )

    /** The keys this build trusts. Benchmark and release builds are not DEBUG, so they never trust a test key. */
    fun trusted(debug: Boolean = BuildConfig.DEBUG): Map<String, PublicKey> =
        (if (debug) RELEASE + TEST else RELEASE).mapValues { (_, key) -> LicenseCrypto.publicKey(key) }
}

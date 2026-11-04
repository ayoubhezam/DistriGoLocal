package com.distrigo.app.data.backup.cloud

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One access token for a whole run instead of one per Drive request (2026-11-04: a single backup asked Google Play
 * services about ten times, half a second each).
 *
 * The token is reused for [reuseForMillis] (Google's access tokens last about an hour; 45 minutes leaves a margin),
 * then fetched again. A token Drive rejects (401) is forgotten — [forget] also clears Play services' own cache, which
 * would otherwise hand the same token back — and a fresh one fetched. When several requests are rejected together,
 * only the first refreshes; the others get the token it fetched.
 */
class CachedTokens(
    private val fetch: suspend () -> String,
    private val forget: suspend (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val reuseForMillis: Long = REUSE_FOR_MILLIS,
) : DriveTokens {
    private val lock = Mutex()
    private var token: String? = null
    private var fetchedAt = 0L

    override suspend fun accessToken(): String = lock.withLock {
        val current = token
        if (current != null && clock() - fetchedAt < reuseForMillis) return@withLock current
        fresh()
    }

    override suspend fun refresh(rejected: String): String = lock.withLock {
        val current = token
        // Another request already replaced the rejected token: use the new one.
        if (current != null && current != rejected) return@withLock current
        token = null
        forget(rejected)
        fresh()
    }

    private suspend fun fresh(): String = fetch().also {
        token = it
        fetchedAt = clock()
    }

    companion object {
        const val REUSE_FOR_MILLIS = 45L * 60 * 1000
    }
}

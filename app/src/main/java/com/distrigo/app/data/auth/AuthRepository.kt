package com.distrigo.app.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Whether someone is signed in on this phone. */
sealed interface AccountState {
    /** Before the stored session has been read. */
    data object Loading : AccountState
    data object SignedOut : AccountState
    data class SignedIn(val session: AuthSession) : AccountState
}

/** An access token for a call to the server, or why there is none. */
sealed interface AccessToken {
    data class Granted(val token: String) : AccessToken
    data class Denied(val error: AuthError) : AccessToken
}

/**
 * The account signed in on this phone (docs/license_architecture.md §1): who is asking, for the calls to the
 * server. It is not what lets the app work offline — the license is — so losing the session never stops the
 * app; it only stops check-ins until the user signs in again.
 */
class AuthRepository(
    private val api: AuthApi,
    private val store: SessionStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<AccountState>(AccountState.Loading)
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** One refresh at a time: a refresh token serves once, so two at once would sign the user out. */
    private val refreshing = Mutex()

    private val session: AuthSession? get() = (_state.value as? AccountState.SignedIn)?.session

    /** Reads the stored session. File and Keystore I/O: call it off the main thread, once at startup. */
    fun restore() {
        if (_state.value == AccountState.Loading) {
            _state.value = store.load()?.let(AccountState::SignedIn) ?: AccountState.SignedOut
        }
    }

    suspend fun signIn(email: String, password: String): AuthOutcome =
        keep(api.signInWithPassword(email.trim(), password))

    suspend fun signUp(email: String, password: String, name: String): AuthOutcome =
        keep(api.signUp(email.trim(), password, name.trim()))

    suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthOutcome =
        keep(api.signInWithGoogle(idToken, rawNonce))

    /** An account without an e-mail, for an activation code. */
    suspend fun signInAnonymously(): AuthOutcome = keep(api.signInAnonymously())

    /**
     * « Ajouter une adresse e-mail »: the account made by a code gets an e-mail and a password. Then a refresh, so
     * the session (and the token the server reads) says it is an e-mail account.
     */
    suspend fun addEmail(email: String, password: String): EmailUpdate {
        val token = when (val access = accessToken()) {
            is AccessToken.Granted -> access.token
            is AccessToken.Denied -> return EmailUpdate.Failed(access.error)
        }
        val update = api.updateUser(token, email.trim(), password)
        if (update == EmailUpdate.Done) accessToken(forceRefresh = true)
        return update
    }

    /**
     * An access token good for at least a minute: the stored one, or a fresh one. [forceRefresh] when the server
     * has just refused the stored one. A session the server no longer knows signs the phone out.
     */
    suspend fun accessToken(forceRefresh: Boolean = false): AccessToken = refreshing.withLock {
        val current = session ?: return AccessToken.Denied(AuthError.SESSION_EXPIRED)
        if (!forceRefresh && current.expiresAt - clock() > MIN_VALIDITY) return AccessToken.Granted(current.accessToken)
        when (val outcome = api.refresh(current.refreshToken)) {
            is AuthOutcome.SignedIn -> {
                save(outcome.session)
                AccessToken.Granted(outcome.session.accessToken)
            }
            is AuthOutcome.Failed -> {
                if (outcome.error == AuthError.SESSION_EXPIRED) forget()
                AccessToken.Denied(outcome.error)
            }
            is AuthOutcome.ConfirmEmail -> AccessToken.Denied(AuthError.SERVER)
        }
    }

    /** Signs out here and, if it can, on the server. */
    suspend fun signOut() {
        session?.let { api.signOut(it.accessToken) }
        forget()
    }

    /** Forgets the session on this phone only: after the account was deleted, or the server ended it. */
    suspend fun forget() = withContext(Dispatchers.IO) {
        store.clear()
        _state.value = AccountState.SignedOut
    }

    private suspend fun keep(outcome: AuthOutcome): AuthOutcome {
        if (outcome is AuthOutcome.SignedIn) save(outcome.session)
        return outcome
    }

    private suspend fun save(session: AuthSession) = withContext(Dispatchers.IO) {
        store.save(session)
        _state.value = AccountState.SignedIn(session)
    }

    private companion object {
        const val MIN_VALIDITY = 60_000L
    }
}

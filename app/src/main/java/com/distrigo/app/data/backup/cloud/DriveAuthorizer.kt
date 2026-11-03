package com.distrigo.app.data.backup.cloud

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.distrigo.app.data.backup.cloud.CloudBackupException.Problem
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google's permission to use the app's hidden Drive folder (plan §4): the Authorization API, scope `drive.appdata`.
 *
 * Not the Google sign-in of the account screen: that gives an identity for Supabase, not Drive access. The two may
 * even be different Google accounts. No refresh token is stored: Google Play services hands out a fresh access token
 * silently (about an hour long) as long as the grant stands.
 */
class DriveAuthorizer(context: Context) {
    private val context = context.applicationContext

    sealed class Outcome {
        class Granted(val accessToken: String) : Outcome()

        /** The user must see Google's consent: launch [intent] from a screen, then pass its result to [consentResult]. */
        class NeedsConsent(val intent: PendingIntent) : Outcome()

        /** No Google Play services (e.g. recent Huawei phones): « Non disponible sur ce téléphone ». */
        object Unavailable : Outcome()

        class Failed(val problem: Problem, val message: String) : Outcome()
    }

    fun isAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /**
     * Asks for the scope. With [account] (the linked email) Google answers for that account only, silently when it was
     * granted before; without it, Google lets the user pick the account on its consent screen.
     */
    suspend fun authorize(account: String? = null): Outcome {
        if (!isAvailable()) return Outcome.Unavailable
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveClient.SCOPE)))
            .apply { account?.let { setAccount(Account(it, GOOGLE_ACCOUNT_TYPE)) } }
            .build()
        return try {
            outcomeOf(Identity.getAuthorizationClient(context).authorize(request).await())
        } catch (e: ApiException) {
            Outcome.Failed(problemOf(e), "Authorization ${e.statusCode}")
        } catch (e: Exception) {
            Outcome.Failed(Problem.NETWORK, e.message ?: "Authorization")
        }
    }

    /** What Google's consent screen returned. */
    fun consentResult(data: Intent?): Outcome = try {
        outcomeOf(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (e: ApiException) {
        // The user closed the screen, or chose no account.
        Outcome.Failed(if (e.statusCode == CommonStatusCodes.CANCELED) Problem.NEEDS_CONSENT else problemOf(e), "Consent ${e.statusCode}")
    }

    /** Silent tokens for [account], for the worker: anything needing a screen becomes [Problem.NEEDS_CONSENT]. */
    fun tokens(account: String): DriveTokens = object : DriveTokens {
        override suspend fun accessToken(): String = when (val outcome = authorize(account)) {
            is Outcome.Granted -> outcome.accessToken
            is Outcome.NeedsConsent -> throw CloudBackupException(Problem.NEEDS_CONSENT, "Drive access must be granted again")
            Outcome.Unavailable -> throw CloudBackupException(Problem.UNAVAILABLE, "No Google Play services")
            is Outcome.Failed -> throw CloudBackupException(outcome.problem, outcome.message)
        }

        override suspend fun refresh(rejected: String): String {
            withContext(Dispatchers.IO) {
                // Play services caches tokens: a rejected one would come back unless it is cleared.
                runCatching { GoogleAuthUtil.clearToken(context, rejected) }
            }
            return accessToken()
        }
    }

    private fun outcomeOf(result: AuthorizationResult): Outcome {
        if (result.hasResolution()) {
            return result.pendingIntent?.let { Outcome.NeedsConsent(it) }
                ?: Outcome.Failed(Problem.NEEDS_CONSENT, "Resolution without an intent")
        }
        val token = result.accessToken
        // An empty list is not « refused »: only a list that names other scopes and not this one is.
        val scopes = result.grantedScopes.orEmpty()
        val granted = scopes.isEmpty() || scopes.contains(DriveClient.SCOPE)
        return when {
            token.isNullOrEmpty() -> Outcome.Failed(Problem.NEEDS_CONSENT, "No access token")
            !granted -> Outcome.Failed(Problem.NEEDS_CONSENT, "drive.appdata not granted")
            else -> Outcome.Granted(token)
        }
    }

    private fun problemOf(e: ApiException) = when (e.statusCode) {
        CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT, CommonStatusCodes.INTERNAL_ERROR,
        CommonStatusCodes.INTERRUPTED -> Problem.NETWORK
        else -> Problem.NEEDS_CONSENT
    }

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

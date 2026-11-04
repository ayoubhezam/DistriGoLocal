package com.distrigo.app.data.backup.cloud

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
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

        /** The user closed Google's screen or chose no account: nothing to tell. */
        object Cancelled : Outcome()

        /**
         * Google refused or failed. [statusCode] is Google's own (`CommonStatusCodes`: 7 network, 8 internal error,
         * 10 developer error = the app is not set up in Google Cloud, …), shown to the user so a report can name it.
         */
        class Failed(val problem: Problem, val message: String, val statusCode: Int? = null) : Outcome()
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
        val outcome = try {
            outcomeOf(Identity.getAuthorizationClient(context).authorize(request).await())
        } catch (e: ApiException) {
            failed("authorize", e)
        } catch (e: Exception) {
            Log.w(TAG, "authorize: ${e.javaClass.simpleName}: ${e.message}", e)
            Outcome.Failed(Problem.NETWORK, e.message ?: "Authorization")
        }
        Log.i(TAG, "authorize(${if (account == null) "pick an account" else "linked account"}) -> ${describe(outcome)}")
        return outcome
    }

    /**
     * What Google's consent screen returned. [resultCode] is the activity result: anything but OK with no data is the
     * user closing it.
     */
    fun consentResult(resultCode: Int, data: Intent?): Outcome {
        val outcome = try {
            outcomeOf(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) Outcome.Cancelled else failed("consent", e)
        } catch (e: Exception) {
            Log.w(TAG, "consent: ${e.javaClass.simpleName}: ${e.message}", e)
            if (data == null) Outcome.Cancelled else Outcome.Failed(Problem.REJECTED, e.message ?: "Consent")
        }
        Log.i(TAG, "consent result (activity result $resultCode, data ${if (data == null) "none" else "present"}) -> ${describe(outcome)}")
        return outcome
    }

    /** Google's code, its name and its message in the log; network-like codes stay retryable, the others are refusals. */
    private fun failed(step: String, e: ApiException): Outcome.Failed {
        val name = CommonStatusCodes.getStatusCodeString(e.statusCode)
        Log.w(TAG, "$step: ApiException ${e.statusCode} ($name): ${e.status.statusMessage ?: e.message}", e)
        return Outcome.Failed(problemOf(e), "$step $name", e.statusCode)
    }

    private fun describe(outcome: Outcome) = when (outcome) {
        is Outcome.Granted -> "granted (token received)"
        is Outcome.NeedsConsent -> "needs Google's consent screen"
        Outcome.Unavailable -> "no Google Play services"
        Outcome.Cancelled -> "cancelled by the user"
        is Outcome.Failed -> "failed: ${outcome.message} (code ${outcome.statusCode}, ${outcome.problem})"
    }

    /** Silent tokens for [account], for the worker: anything needing a screen becomes [Problem.NEEDS_CONSENT]. */
    fun tokens(account: String): DriveTokens = object : DriveTokens {
        override suspend fun accessToken(): String = when (val outcome = authorize(account)) {
            is Outcome.Granted -> outcome.accessToken
            is Outcome.NeedsConsent -> throw CloudBackupException(Problem.NEEDS_CONSENT, "Drive access must be granted again")
            Outcome.Unavailable -> throw CloudBackupException(Problem.UNAVAILABLE, "No Google Play services")
            Outcome.Cancelled -> throw CloudBackupException(Problem.NEEDS_CONSENT, "Drive access must be granted again")
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
        Log.i(TAG, "authorization result: token ${if (token.isNullOrEmpty()) "missing" else "present"}, granted scopes $scopes")
        val granted = scopes.isEmpty() || scopes.contains(DriveClient.SCOPE)
        return when {
            token.isNullOrEmpty() -> Outcome.Failed(Problem.NEEDS_CONSENT, "No access token")
            !granted -> Outcome.Failed(Problem.NEEDS_CONSENT, "drive.appdata not granted")
            else -> Outcome.Granted(token)
        }
    }

    /**
     * Only a real connection problem is NETWORK (retried). INTERNAL_ERROR (8) is not: it is what Google answers for an
     * app it does not know (no OAuth client, Drive API off), and filing it under « no connection » hid exactly that.
     */
    private fun problemOf(e: ApiException) = when (e.statusCode) {
        CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT, CommonStatusCodes.INTERRUPTED -> Problem.NETWORK
        CommonStatusCodes.SIGN_IN_REQUIRED, CommonStatusCodes.RESOLUTION_REQUIRED -> Problem.NEEDS_CONSENT
        else -> Problem.REJECTED
    }

    private companion object {
        const val TAG = "DriveAuthorizer"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

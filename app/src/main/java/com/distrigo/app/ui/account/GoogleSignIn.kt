package com.distrigo.app.ui.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.distrigo.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * « Continuer avec Google », through Credential Manager (docs/license_architecture.md §6.6). Google signs the
 * SHA-256 of a nonce into its ID token; Supabase gets the nonce itself and checks the two agree, so a token
 * cannot be replayed.
 */
object GoogleSignIn {

    /** Off until local.properties names the Google Cloud Web client (GOOGLE_WEB_CLIENT_ID). */
    val available: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    sealed interface Result {
        data class Token(val idToken: String, val rawNonce: String) : Result
        data object Cancelled : Result
        data class Failed(val message: String) : Result
    }

    suspend fun request(activity: Activity): Result {
        val rawNonce = ByteArray(32).also(SecureRandom()::nextBytes).hex()
        val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray()).hex()
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(hashedNonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Result.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken, rawNonce)
            } else {
                Result.Failed("Google a répondu de façon inattendue. Réessayez.")
            }
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: NoCredentialException) {
            Result.Failed("Aucun compte Google n'est disponible sur ce téléphone.")
        } catch (e: GetCredentialException) {
            Result.Failed("La connexion avec Google a échoué. Réessayez.")
        } catch (e: GoogleIdTokenParsingException) {
            Result.Failed("La connexion avec Google a échoué. Réessayez.")
        }
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}

/** The activity behind a Compose context, which Credential Manager needs to show its sheet. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

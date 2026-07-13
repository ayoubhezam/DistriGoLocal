package com.distrigo.app.data.api

import retrofit2.HttpException
import org.json.JSONObject

fun extractErrorMessage(e: Exception): String {
    if (e is HttpException) {
        return try {
            val errorBodyString = e.response()?.errorBody()?.string()
            if (!errorBodyString.isNullOrEmpty()) {
                val json = JSONObject(errorBodyString)
                json.optString("error", e.message ?: "Erreur inconnue")
            } else {
                e.message ?: "Erreur inconnue"
            }
        } catch (parseError: Exception) {
            e.message ?: "Erreur inconnue"
        }
    }
    return e.message ?: "Erreur inconnue"
}
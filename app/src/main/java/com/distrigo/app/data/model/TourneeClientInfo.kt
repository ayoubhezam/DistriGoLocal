package com.distrigo.app.data.model

data class TourneeClientInfo(
    val client    : Client,
    val status    : String,      // "a_visiter" | "en_cours" | "visite"
    val visitedAt : String?
)
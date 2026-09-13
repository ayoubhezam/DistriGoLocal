package com.distrigo.app.data.model

/**
 * One secteur as recorded on a tournée: the real secteur's id, plus the name as it stood when the
 * tournée was created. See `TourneeSecteurEntity` for why both are carried.
 */
data class TourneeSecteur(
    val secteurId : Int,
    val nom       : String
)

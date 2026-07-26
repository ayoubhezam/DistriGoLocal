package com.distrigo.app.data.model.report

data class ClientsRapportKpis(
    val clientsActifs: Int,
    val nouveauxClients: Int,
    val clientsPerdus: Int,
    val clientsSansAchat: Int
)
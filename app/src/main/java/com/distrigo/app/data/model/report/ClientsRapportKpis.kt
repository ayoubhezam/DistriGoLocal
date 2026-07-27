package com.distrigo.app.data.model.report

data class ClientsRapportKpis(
    val clientsActifs: Int,
    val nouveauxClients: Int,
    val clientsPerdus: Int,
    val clientsSansAchat: Int
)
data class ClientRankItem(
    val clientId: Int,
    val name: String,
    val subtitle: String,     // "Wilaya, Commune"
    val amount: Double,
    val facturesCount: Int,
    val rank: Int
)

data class ClientInvoiceItem(
    val venteId: Int,
    val createdAt: String,
    val total: Double,
    val montantPaye: Double,
    val reste: Double,
    val status: String   // "payee" | "partielle" | "impayee"
)
package com.distrigo.app.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.license.ClientBusiness
import com.distrigo.app.ui.designsystem.BrandCard
import com.distrigo.app.ui.designsystem.BrandCardBody
import com.distrigo.app.ui.designsystem.BrandCardHeader
import com.distrigo.app.ui.designsystem.BrandDialog
import com.distrigo.app.ui.designsystem.BrandDialogText
import com.distrigo.app.ui.designsystem.BrandMessage
import com.distrigo.app.ui.designsystem.BrandPill
import com.distrigo.app.ui.designsystem.BrandRowDivider
import com.distrigo.app.ui.designsystem.BrandScreen
import com.distrigo.app.ui.designsystem.BrandSize
import com.distrigo.app.ui.designsystem.BrandWideButton
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.onboarding.BrandTextField
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// « Comptes clients » in the owner's Administration (docs/activation_codes.md, step C4): find a client's business,
// see its subscription, suspend, reactivate or delete it.

@Composable
fun ClientsScreen(viewModel: AdminViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val state by viewModel.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val searching = state.work == AdminWork.SEARCHING

    // The latest clients, until something is searched
    LaunchedEffect(Unit) {
        if (state.clients == null) viewModel.searchClients("")
    }

    BrandScreen(title = "Comptes clients", onBack = onBack) {
        state.message?.let { BrandMessage(it, isError = false) }
        BrandCard {
            BrandCardBody {
                BrandTextField(
                    value         = query,
                    onValueChange = { query = it },
                    label         = "Rechercher un client",
                    placeholder   = "Nom, téléphone, e-mail ou code",
                    icon          = Icons.Outlined.Search,
                    onDone        = { viewModel.searchClients(query) }
                )
                BrandWideButton("Rechercher", Icons.Outlined.Search, filled = true, enabled = state.work == null, busy = searching) {
                    viewModel.searchClients(query)
                }
            }
        }
        BrandCard {
            val clients = state.clients
            BrandCardHeader(Icons.Outlined.Storefront, if (state.clientsQuery == null) "Derniers clients" else "Résultats") {
                clients?.let { BrandPill("${it.size}", DsBrand.Blue, DsBrand.Blue.copy(alpha = 0.1f)) }
            }
            when {
                clients == null && state.clientsProblem != null -> BrandCardBody { Note(state.clientsProblem!!) }
                clients == null -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsBrand.Blue, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                }
                clients.isEmpty() -> BrandCardBody { Note("Aucun client ne correspond.") }
                else -> clients.forEachIndexed { i, business ->
                    if (i > 0) BrandRowDivider()
                    ClientRow(business, own = business.id == state.ownBusinessId) { viewModel.openClient(business) }
                }
            }
        }
    }

    state.selectedClient?.let { ClientDialog(it, state, viewModel) }
}

/** A client: its name, who it is, its subscription, and its state as a pill. */
@Composable
private fun ClientRow(business: ClientBusiness, own: Boolean, onClick: () -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = DsSpacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                business.name + if (own) " (vous)" else "",
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.Bold,
                fontSize   = BrandSize.Body,
                lineHeight = BrandSize.BodyLine,
                color      = DsBrand.Ink,
                maxLines   = 2
            )
            Line(listOfNotNull(who(business), business.contactPhone).joinToString(" · "))
            Line(subscription(business))
        }
        Spacer(Modifier.width(DsSpacing.md))
        val look = stateLook(business)
        BrandPill(look.first, look.second, look.third)
    }
}

/** A client opened: everything known, and what can be done to it. */
@Composable
private fun ClientDialog(business: ClientBusiness, state: AdminUiState, viewModel: AdminViewModel) {
    val own = business.id == state.ownBusinessId
    val busy = state.work == AdminWork.CHANGING
    when (state.clientAction) {
        null -> BrandDialog(
            title   = business.name,
            text    = null,
            confirm = "Fermer" to viewModel::closeClient,
        ) {
            val look = stateLook(business)
            BrandPill(look.first, look.second, look.third)
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.xs)) {
                Detail("Compte", who(business) ?: "—")
                business.contactPhone?.let { Detail("Téléphone", it) }
                Detail("Abonnement", subscription(business))
                Detail("Téléphones actifs", "${business.phones}")
                business.lastSeen?.let { Detail("Dernière connexion", day(it.atZone(ZoneId.systemDefault()).toLocalDate())) }
                business.createdOn?.let { Detail("Client depuis", day(it)) }
            }
            state.clientError?.let { BrandMessage(it, isError = true) }
            if (own) {
                BrandDialogText("C'est votre entreprise : elle ne peut être ni suspendue ni supprimée ici.")
            } else {
                if (business.status == "suspended") {
                    BrandWideButton("Réactiver", Icons.Outlined.PlayCircleOutline, filled = true, enabled = !busy) {
                        viewModel.askClient(ClientAction.REACTIVATE)
                    }
                } else {
                    BrandWideButton("Suspendre", Icons.Outlined.PauseCircleOutline, filled = false, enabled = !busy, danger = true) {
                        viewModel.askClient(ClientAction.SUSPEND)
                    }
                }
                BrandWideButton("Supprimer le compte", Icons.Outlined.DeleteOutline, filled = false, enabled = !busy, danger = true) {
                    viewModel.askClient(ClientAction.DELETE)
                }
            }
        }
        ClientAction.SUSPEND -> {
            var note by rememberSaveable { mutableStateOf("") }
            BrandDialog(
                title   = "Suspendre « ${business.name} » ?",
                text    = "Ses téléphones perdront la licence à leur prochaine vérification, au plus tard dans 14 jours. " +
                    "L'activation lui sera refusée. Vous pourrez le réactiver.",
                confirm = "Suspendre" to { viewModel.applyClientAction(note) },
                dismiss = "Annuler" to viewModel::cancelClientAction,
                danger  = true,
                busy    = busy,
            ) {
                BrandTextField(
                    value         = note,
                    onValueChange = { note = it },
                    label         = "Motif (facultatif)",
                    placeholder   = "Ex. : impayé",
                    icon          = Icons.Outlined.EditNote,
                    onDone        = { viewModel.applyClientAction(note) }
                )
            }
        }
        ClientAction.REACTIVATE -> BrandDialog(
            title   = "Réactiver « ${business.name} » ?",
            text    = "Ses téléphones pourront de nouveau vérifier ou activer l'abonnement, jusqu'au " +
                (business.validTo?.let(::day) ?: "terme prévu") + ".",
            confirm = "Réactiver" to { viewModel.applyClientAction(null) },
            dismiss = "Annuler" to viewModel::cancelClientAction,
            busy    = busy,
        )
        ClientAction.DELETE -> BrandDialog(
            title   = "Supprimer « ${business.name} » ?",
            text    = "L'entreprise, son abonnement et les places de ses téléphones sont supprimés définitivement ; " +
                "son code d'activation ne fonctionnera plus. Les données restent sur ses téléphones.",
            confirm = "Supprimer" to { viewModel.applyClientAction(null) },
            dismiss = "Annuler" to viewModel::cancelClientAction,
            danger  = true,
            busy    = busy,
        )
    }
}

/** Who holds the account: its e-mail, or « Activé avec un code ». */
private fun who(business: ClientBusiness): String? =
    business.ownerEmail ?: if (business.byCode) "Activé avec un code" else null

/** « DistriGo Solo · jusqu'au 12 nov. 2026 ». */
private fun subscription(business: ClientBusiness): String = listOfNotNull(
    when (business.plan) {
        "trial" -> "Essai gratuit"
        "solo" -> "DistriGo Solo"
        null -> "Sans abonnement"
        else -> business.plan
    },
    business.validTo?.let { "jusqu'au ${SHORT_DAY.format(it)}" },
).joinToString(" · ")

/** Its state as a pill: suspended in red, ended in amber, running in green. */
private fun stateLook(business: ClientBusiness): Triple<String, Color, Color> = when {
    business.status == "suspended" -> Triple("Suspendu", DsColors.Danger, DsColors.DangerLight)
    business.status == "canceled" -> Triple("Résilié", DsColors.Danger, DsColors.DangerLight)
    business.validTo?.isBefore(LocalDate.now()) == true -> Triple("Expiré", DsColors.Warning, DsColors.WarningLight)
    business.plan == "trial" -> Triple("Essai", DsBrand.Blue, DsBrand.Blue.copy(alpha = 0.1f))
    else -> Triple("Actif", DsColors.Success, DsColors.SuccessLight)
}

private val SHORT_DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

@Composable
private fun Line(text: String) {
    if (text.isEmpty()) return
    Text(text, fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = BrandSize.Small, lineHeight = BrandSize.SmallLine, color = DsBrand.Slate, maxLines = 1)
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        fontFamily = DsBrand.Font,
        fontWeight = FontWeight.Medium,
        fontSize   = BrandSize.Body,
        lineHeight = BrandSize.BodyLine,
        color      = DsBrand.Slate,
        textAlign  = TextAlign.Center,
        modifier   = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text("$label : ", fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = BrandSize.Body, color = DsBrand.Slate)
        Text(value, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Body, color = DsBrand.Ink)
    }
}

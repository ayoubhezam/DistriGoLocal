package com.distrigo.app.ui.admin

import com.distrigo.app.core.format.subscriptionLength
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.license.CodeBatch
import com.distrigo.app.data.license.BatchUse
import com.distrigo.app.data.license.CodeExpiry
import com.distrigo.app.data.license.CodeOrder
import com.distrigo.app.data.license.CodeState
import com.distrigo.app.ui.designsystem.BrandCard
import com.distrigo.app.ui.designsystem.BrandCardBody
import com.distrigo.app.ui.designsystem.BrandCardHeader
import com.distrigo.app.ui.designsystem.BrandChips
import com.distrigo.app.ui.designsystem.BrandCodeText
import com.distrigo.app.ui.designsystem.BrandDialog
import com.distrigo.app.ui.designsystem.BrandDialogText
import com.distrigo.app.ui.designsystem.BrandFieldLabel
import com.distrigo.app.ui.designsystem.BrandInfoRow
import com.distrigo.app.ui.designsystem.BrandMessage
import com.distrigo.app.ui.designsystem.BrandNotice
import com.distrigo.app.ui.designsystem.BrandPill
import com.distrigo.app.ui.designsystem.BrandRowDivider
import com.distrigo.app.ui.designsystem.BrandScreen
import com.distrigo.app.ui.designsystem.BrandSize
import com.distrigo.app.ui.designsystem.BrandWideButton
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.onboarding.BrandTextField
import com.distrigo.app.ui.onboarding.CodeField
import com.distrigo.app.ui.onboarding.normalizeCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

// The owner's « Administration » (docs/activation_codes.md, step C4), in the design language of Compte
// (ui/designsystem/BrandComponents.kt): the batches, a new batch, its codes shown once, one code checked.

/** The batches, and the two things to do: make a batch, check a code. */
@Composable
fun AdminHomeScreen(viewModel: AdminViewModel, onBack: () -> Unit, onNewBatch: () -> Unit, onClients: () -> Unit) {
    BackHandler { onBack() }
    val state by viewModel.state.collectAsState()
    val idle = state.work == null

    BrandScreen(title = "Administration", onBack = onBack) {
        state.message?.let { BrandMessage(it, isError = false) }
        if (state.notAllowed) {
            BrandCard {
                BrandCardBody { Body("Ce compte n'a pas accès à l'administration.") }
            }
            return@BrandScreen
        }
        BrandCard {
            BrandCardHeader(Icons.Outlined.Key, "Codes d'activation")
            BrandCardBody {
                Body("Créez des lots pour vos revendeurs ou vos clients, puis partagez-les sur WhatsApp ou en cartes à imprimer.")
                BrandWideButton("Créer un lot de codes", Icons.Outlined.AddCircleOutline, filled = true, enabled = idle, onClick = onNewBatch)
                BrandWideButton("Vérifier un code", Icons.Outlined.Search, filled = false, enabled = idle, onClick = viewModel::openCheck)
            }
        }
        BrandCard {
            BrandCardHeader(Icons.Outlined.Storefront, "Comptes clients")
            BrandCardBody {
                Body("Retrouvez un client par son nom, son téléphone, son e-mail ou son code ; suspendez, réactivez ou supprimez son compte.")
                BrandWideButton("Rechercher un client", Icons.Outlined.PersonSearch, filled = false, enabled = idle, onClick = onClients)
            }
        }
        BrandCard {
            BrandCardHeader(Icons.Outlined.Inventory2, "Lots") {
                state.batches?.let { BrandPill("${it.size}", DsBrand.Blue, DsBrand.Blue.copy(alpha = 0.1f)) }
            }
            val batches = state.batches
            when {
                batches == null && state.problem != null -> BrandCardBody {
                    Body(state.problem!!, center = true)
                    TextButton(onClick = viewModel::refresh, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.common_action_retry), fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Action, color = DsBrand.Blue)
                    }
                }
                batches == null -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsBrand.Blue, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                }
                batches.isEmpty() -> BrandCardBody { Body("Aucun lot pour l'instant.", center = true) }
                else -> batches.forEachIndexed { i, batch ->
                    if (i > 0) BrandRowDivider()
                    BatchRow(batch) { viewModel.openBatch(batch) }
                }
            }
        }
    }

    state.check?.let { CheckDialog(it, state.work, viewModel) }
    state.openedBatch?.let { BatchDialog(it, state, viewModel) }
}

/** A batch: its name, what its codes give, and how many are still free. Tapped, its details. */
@Composable
private fun BatchRow(batch: CodeBatch, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = DsSpacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(batch.name, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Body, lineHeight = BrandSize.BodyLine, color = DsBrand.Ink, maxLines = 2)
            // Every code is DistriGo Solo today: the plan is named only when it differs
            Text(
                listOfNotNull(
                    plan(batch.plan).takeIf { batch.plan != "solo" },
                    subscriptionLength(batch.days),
                    batch.createdOn?.let { "créé le ${SHORT.format(it)}" },
                ).joinToString(" · "),
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.Medium,
                fontSize   = BrandSize.Small,
                lineHeight = BrandSize.SmallLine,
                color      = DsBrand.Slate
            )
            uses(batch)?.let {
                Text(it, fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = BrandSize.Small, lineHeight = BrandSize.SmallLine, color = DsBrand.Blue)
            }
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(horizontalAlignment = Alignment.End) {
            Text("${batch.unused}/${batch.total}", fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = BrandSize.Name, color = DsBrand.Ink)
            Text("libres", fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = BrandSize.Tiny, color = DsBrand.Slate)
        }
    }
}

/** What the batch's codes became, when any did. */
private fun uses(batch: CodeBatch): String? = listOfNotNull(
    batch.keys.takeIf { it > 0 }?.let { "$it ${if (it > 1) "activés" else "activé"}" },
    batch.renewals.takeIf { it > 0 }?.let { "$it ${if (it > 1) "renouvelés" else "renouvelé"}" },
    batch.revoked.takeIf { it > 0 }?.let { "$it ${if (it > 1) "révoqués" else "révoqué"}" },
    batch.expired.takeIf { it > 0 }?.let { "$it ${if (it > 1) "expirés" else "expiré"}" },
).joinToString(" · ").ifEmpty { null }

/** The used codes listed at most: a dialog doesn't scroll, and the counts above say the rest. */
private const val USES_SHOWN = 6

/**
 * A batch opened from « Lots »: what it gives, until when, what its codes became and for which businesses; its free
 * codes revoked, or the batch deleted once none of its codes opens a business (each asked first).
 */
@Composable
private fun BatchDialog(batch: CodeBatch, state: AdminUiState, viewModel: AdminViewModel) {
    val details = state.batchDetails
    val busy = state.work == AdminWork.CHANGING
    when (state.batchAction) {
        null -> BrandDialog(
            title   = batch.name,
            text    = null,
            confirm = "Fermer" to viewModel::closeBatch,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.xs)) {
                Detail("Formule", "${plan(batch.plan)} · ${subscriptionLength(batch.days)} par code")
                batch.createdOn?.let { Detail("Créé le", SHORT.format(it)) }
                details?.let { d ->
                    Detail("À activer avant le", d.expiresOn?.let { SHORT.format(it) } ?: "Sans limite")
                    d.businessName?.let { Detail("Entreprise", it) }
                    d.note?.let { Detail("Note", it) }
                }
                Detail("Codes libres", "${batch.unused} sur ${batch.total}")
                uses(batch)?.let { Detail("Les autres", it) }
            }
            when {
                details != null -> if (details.uses.isNotEmpty()) {
                    BrandFieldLabel("Codes utilisés")
                    details.uses.take(USES_SHOWN).forEach { UseLine(it) }
                    if (details.uses.size > USES_SHOWN) Small("… et ${details.uses.size - USES_SHOWN} de plus")
                }
                state.batchError != null -> Unit
                state.work == AdminWork.READING_BATCH -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsBrand.Blue, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                }
                // Read while something else ran, so not read: the owner asks again
                else -> TextButton(onClick = { viewModel.retryBatch() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Afficher le détail", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Action, color = DsBrand.Blue)
                }
            }
            state.batchError?.let { BrandMessage(it, isError = true) }
            if (details != null) {
                if (batch.unused > 0) {
                    BrandWideButton("Révoquer les codes libres", Icons.Outlined.Block, filled = false, enabled = !busy, danger = true) {
                        viewModel.askBatch(BatchAction.REVOKE_FREE)
                    }
                }
                if (details.liveKeys == 0) {
                    BrandWideButton("Supprimer le lot", Icons.Outlined.DeleteOutline, filled = false, enabled = !busy, danger = true) {
                        viewModel.askBatch(BatchAction.DELETE)
                    }
                } else {
                    BrandDialogText(inUse(details.liveKeys))
                }
            }
        }
        BatchAction.REVOKE_FREE -> BrandDialog(
            title   = "Révoquer les codes libres ?",
            text    = (if (batch.unused > 1) "Les ${batch.unused} codes libres de « ${batch.name} » ne pourront plus être utilisés."
                       else "Le code libre de « ${batch.name} » ne pourra plus être utilisé.") +
                " Les codes déjà utilisés, et les entreprises qu'ils ont ouvertes, ne changent pas.",
            confirm = "Révoquer" to viewModel::applyBatchAction,
            dismiss = "Annuler" to viewModel::cancelBatchAction,
            danger  = true,
            busy    = busy,
        )
        BatchAction.DELETE -> BrandDialog(
            title   = "Supprimer le lot ?",
            text    = "« ${batch.name} » et ses ${codes(batch.total)} seront effacés : un code encore libre ne pourra plus être utilisé." +
                if (batch.keys + batch.renewals > 0) " Les abonnements déjà obtenus avec ses codes ne changent pas." else "",
            confirm = "Supprimer" to viewModel::applyBatchAction,
            dismiss = "Annuler" to viewModel::cancelBatchAction,
            danger  = true,
            busy    = busy,
        )
    }
}

/** A used code: the business it went to, as its key or a renewal, and when. */
@Composable
private fun UseLine(use: BatchUse) {
    Small(
        listOfNotNull(
            use.businessName ?: "Entreprise supprimée",
            if (use.key) "clé" else "renouvellement",
            use.redeemedAt?.let { SHORT.format(it.atZone(java.time.ZoneId.systemDefault())) },
        ).joinToString(" · ")
    )
}

private enum class Validity { DEFAULT, SIX_MONTHS, NEVER }

/** The « Autre » chip among the durations in days: no code gives zero days. */
private const val OTHER_LENGTH = 0

/** « Nouveau lot »: a name, how many, how long, until when; a company and a note if wanted. */
@Composable
fun NewBatchScreen(viewModel: AdminViewModel, onBack: () -> Unit, onCreated: () -> Unit) {
    BackHandler { onBack() }
    val state by viewModel.state.collectAsState()
    val busy = state.work == AdminWork.CREATING
    var name by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableIntStateOf(10) }
    var days by rememberSaveable { mutableIntStateOf(365) }
    // « Autre »: a duration typed in days, months or years
    var customAmount by rememberSaveable { mutableStateOf("") }
    var customUnit by rememberSaveable { mutableStateOf(LengthUnit.DAYS) }
    val chosenDays: Int? = if (days == OTHER_LENGTH) customDays(customAmount, customUnit) else days
    var validity by rememberSaveable { mutableStateOf(Validity.DEFAULT) }
    var company by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    // The codes are made: their own screen shows them, once
    LaunchedEffect(state.created) {
        if (state.created != null) onCreated()
    }

    val expiry = when (validity) {
        Validity.DEFAULT -> CodeExpiry.Default
        Validity.SIX_MONTHS -> CodeExpiry.On(LocalDate.now().plusMonths(6))
        Validity.NEVER -> CodeExpiry.Never
    }
    val create = {
        chosenDays?.let { length ->
            viewModel.create(CodeOrder(count = count, days = length, batch = name, expiry = expiry, businessName = company, note = note))
        }
        Unit
    }

    BrandScreen(title = "Nouveau lot", onBack = onBack) {
        BrandCard {
            BrandCardHeader(Icons.Outlined.Inventory2, "Le lot")
            BrandCardBody {
                BrandTextField(
                    value          = name,
                    onValueChange  = {
                        name = it
                        if (state.createError != null) viewModel.dismissCreateError()
                    },
                    label          = "Nom du lot",
                    placeholder    = "Ex. : Revendeur Sétif — oct. 2026",
                    icon           = Icons.Outlined.Inventory2,
                    capitalization = KeyboardCapitalization.Sentences
                )
                BrandFieldLabel("Nombre de codes")
                BrandChips(listOf(1, 5, 10, 20, 50, 100).map { it to "$it" }, count, enabled = !busy) { count = it }
                BrandFieldLabel("Durée de l'abonnement, par code")
                BrandChips(
                    listOf(30, 90, 182, 365).map { it to subscriptionLength(it) } + (OTHER_LENGTH to "Autre"),
                    days,
                    enabled = !busy
                ) { days = it }
                if (days == OTHER_LENGTH) {
                    BrandTextField(
                        value         = customAmount,
                        onValueChange = { typed -> customAmount = typed.filter(Char::isDigit).take(4) },
                        label         = "Durée",
                        placeholder   = "Ex. : 45",
                        icon          = Icons.Outlined.Event,
                        error         = "De 1 jour à 3 ans (${CodeOrder.MAX_DAYS} jours au plus).".takeIf { customAmount.isNotEmpty() && chosenDays == null },
                        keyboardType  = KeyboardType.Number
                    )
                    BrandChips(LengthUnit.entries.map { it to it.label }, customUnit, enabled = !busy) { customUnit = it }
                }
                BrandFieldLabel("Codes non utilisés valables")
                BrandChips(
                    listOf(Validity.DEFAULT to "12 mois", Validity.SIX_MONTHS to "6 mois", Validity.NEVER to "Sans limite"),
                    validity,
                    enabled = !busy
                ) { validity = it }
            }
        }
        BrandCard {
            BrandCardHeader(Icons.Outlined.Tune, "Facultatif")
            BrandCardBody {
                BrandTextField(
                    value          = company,
                    onValueChange  = { company = it },
                    label          = "Entreprise",
                    placeholder    = "Ex. : Épicerie Benali",
                    icon           = Icons.Outlined.Storefront,
                    capitalization = KeyboardCapitalization.Words
                )
                Small("Pour un client précis : son entreprise est créée avec ce nom, sans le lui demander.")
                BrandTextField(
                    value         = note,
                    onValueChange = { note = it },
                    label         = "Note",
                    placeholder   = "Ex. : payé en espèces",
                    icon          = Icons.Outlined.EditNote,
                    onDone        = create
                )
            }
        }
        state.createError?.let { BrandMessage(it, isError = true) }
        BrandWideButton(
            if (count == 1) "Créer le code" else "Créer les $count codes",
            Icons.Outlined.Key,
            filled  = true,
            enabled = state.work == null && chosenDays != null,
            busy    = busy,
            onClick = create
        )
        // A typed duration also in days, so « 2 ans » shows what the codes will really give
        val length = chosenDays?.let { d ->
            val label = subscriptionLength(d)
            label + if (days == OTHER_LENGTH && "jour" !in label) " ($d jours)" else ""
        } ?: "durée à choisir"
        Small(
            "DistriGo Solo · $length par code · " + when (val e = expiry) {
                // The server counts the 12 months from its own day (UTC): no date here that could differ by one
                CodeExpiry.Default -> "à activer dans les 12 mois"
                CodeExpiry.Never -> "sans date limite"
                is CodeExpiry.On -> "à activer avant le ${day(e.day)}"
            },
            center = true
        )
    }
}

/** The codes just made, the one time they show: shared now, or lost to the phone. */
@Composable
fun CreatedCodesScreen(viewModel: AdminViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val created = state.created
    if (created == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmLeave by remember { mutableStateOf(false) }
    var printing by remember { mutableStateOf(false) }
    var pdfProblem by remember { mutableStateOf<String?>(null) }
    val leave = {
        if (created.shared) {
            viewModel.forgetCreated()
            onDone()
        } else {
            confirmLeave = true
        }
    }
    BackHandler { leave() }

    BrandScreen(title = "Codes créés", onBack = leave) {
        BrandNotice(
            "Ces codes ne seront plus affichés : partagez-les maintenant.",
            DsColors.Warning,
            DsColors.WarningLight
        )
        BrandCard {
            BrandCardHeader(Icons.Outlined.Inventory2, created.order.batch) {
                BrandPill(if (created.codes.size == 1) "1 code" else "${created.codes.size} codes", DsBrand.Blue, DsBrand.Blue.copy(alpha = 0.1f))
            }
            BrandInfoRow(Icons.Outlined.WorkspacePremium, "Formule", plan(created.order.plan))
            BrandRowDivider()
            BrandInfoRow(Icons.Outlined.Event, "Durée, par code", subscriptionLength(created.order.days))
            BrandRowDivider()
            BrandInfoRow(Icons.Outlined.EventAvailable, "À activer avant le", created.codes.firstOrNull()?.expiresOn?.let(::day) ?: "Sans limite")
            created.order.businessName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                BrandRowDivider()
                BrandInfoRow(Icons.Outlined.Storefront, "Entreprise", it)
            }
        }
        BrandCard {
            BrandCardHeader(Icons.Outlined.Key, "Les codes")
            created.codes.forEachIndexed { i, code ->
                if (i > 0) BrandRowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}.",
                        fontFamily = DsBrand.Font,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = BrandSize.Small,
                        color      = DsBrand.Slate,
                        modifier   = Modifier.width(BrandSize.RowIcon + DsSpacing.md)
                    )
                    BrandCodeText(code.code, BrandSize.Code)
                }
            }
        }
        pdfProblem?.let { BrandMessage(it, isError = true) }
        BrandWideButton("Envoyer sur WhatsApp", Icons.AutoMirrored.Outlined.Send, filled = true, enabled = !printing) {
            shareOnWhatsApp(context, whatsAppText(created))
            viewModel.markShared()
        }
        BrandWideButton("Cartes PDF à imprimer", Icons.Outlined.PictureAsPdf, filled = false, enabled = !printing, busy = printing) {
            printing = true
            pdfProblem = null
            scope.launch {
                try {
                    shareCodesPdf(context, CodeCardsPdf.write(context, created))
                    viewModel.markShared()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    pdfProblem = "Les cartes n'ont pas pu être préparées. Réessayez."
                } finally {
                    printing = false
                }
            }
        }
        TextButton(onClick = leave, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Terminé", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Action, color = DsBrand.Blue)
        }
    }

    if (confirmLeave) {
        BrandDialog(
            title   = "Quitter sans partager ?",
            text    = "Ces codes ne seront plus affichés. Partagez-les d'abord, sur WhatsApp ou en cartes PDF.",
            confirm = "Quitter" to {
                confirmLeave = false
                viewModel.forgetCreated()
                onDone()
            },
            dismiss = "Rester" to { confirmLeave = false },
            danger  = true,
        )
    }
}

/** « Vérifier un code »: what it became; a code still free, or a key, can be revoked. */
@Composable
private fun CheckDialog(check: CodeCheck, work: AdminWork?, viewModel: AdminViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    val found = check.state
    when {
        found == null || check.code == null -> BrandDialog(
            title   = "Vérifier un code",
            text    = "Saisissez un code pour savoir ce qu'il est devenu.",
            confirm = "Vérifier" to { viewModel.checkCode(code) },
            dismiss = "Fermer" to viewModel::closeCheck,
            busy    = work == AdminWork.CHECKING,
        ) {
            CodeField(
                code     = code,
                onChange = { code = normalizeCode(it) },
                enabled  = work == null,
                error    = check.error,
                onDone   = { viewModel.checkCode(code) }
            )
        }
        check.confirmRevoke -> BrandDialog(
            title   = "Révoquer ce code ?",
            text    = if (found.status == "key") {
                "Il ne pourra plus ouvrir l'entreprise sur un nouveau téléphone. L'entreprise et son abonnement continuent."
            } else {
                "Il ne pourra plus être utilisé."
            },
            confirm = "Révoquer" to viewModel::revoke,
            dismiss = "Annuler" to viewModel::cancelRevoke,
            danger  = true,
            busy    = work == AdminWork.REVOKING,
        ) {
            BrandCodeText(check.code)
        }
        else -> {
            val revocable = found.status == "unused" || found.status == "key"
            BrandDialog(
                title   = check.code,
                text    = null,
                confirm = if (revocable) "Révoquer" to viewModel::askRevoke else "Fermer" to viewModel::closeCheck,
                dismiss = if (revocable) "Fermer" to viewModel::closeCheck else null,
                danger  = revocable,
            ) {
                val look = statusLook(found.status)
                BrandPill(look.first, look.second, look.third)
                CodeDetails(found)
                check.error?.let { BrandDialogText(it) }
            }
        }
    }
}

@Composable
private fun CodeDetails(state: CodeState) {
    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.xs)) {
        state.batch?.let { Detail("Lot", it) }
        state.plan?.let { p -> Detail("Formule", plan(p) + (state.days?.let { " · ${subscriptionLength(it)}" } ?: "")) }
        state.businessName?.let { Detail("Entreprise", it) }
        state.redeemedAt?.let { Detail("Utilisé le", day(it.atZone(java.time.ZoneId.systemDefault()).toLocalDate())) }
        if (state.status == "unused" || state.status == "expired") {
            Detail("À activer avant le", state.expiresOn?.let(::day) ?: "Sans limite")
        }
        state.revokedAt?.let { Detail("Révoqué le", day(it.atZone(java.time.ZoneId.systemDefault()).toLocalDate())) }
        state.note?.let { Detail("Note", it) }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text("$label : ", fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = BrandSize.Body, color = DsBrand.Slate)
        Text(value, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Body, color = DsBrand.Ink)
    }
}

/** A code's state, as a pill: its words, its colour, its background. */
private fun statusLook(status: String): Triple<String, Color, Color> = when (status) {
    "unused" -> Triple("Libre", DsBrand.Blue, DsBrand.Blue.copy(alpha = 0.1f))
    "key" -> Triple("Clé d'une entreprise", DsColors.Success, DsColors.SuccessLight)
    "renewal" -> Triple("Renouvellement utilisé", DsColors.Success, DsColors.SuccessLight)
    "expired" -> Triple("Expiré", DsColors.Warning, DsColors.WarningLight)
    "revoked" -> Triple("Révoqué", DsColors.Danger, DsColors.DangerLight)
    "malformed" -> Triple("Code invalide", DsColors.TextSecondary, DsColors.SurfaceSunken)
    else -> Triple("Code inconnu", DsColors.TextSecondary, DsColors.SurfaceSunken)
}

private fun plan(id: String): String = if (id == "solo") "DistriGo Solo" else id

private val SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

@Composable
private fun Body(text: String, center: Boolean = false) {
    Text(
        text,
        fontFamily = DsBrand.Font,
        fontWeight = FontWeight.Medium,
        fontSize   = BrandSize.Body,
        lineHeight = BrandSize.BodyLine,
        color      = DsBrand.Slate,
        textAlign  = if (center) TextAlign.Center else TextAlign.Start,
        modifier   = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Small(text: String, center: Boolean = false) {
    Text(
        text,
        fontFamily = DsBrand.Font,
        fontWeight = FontWeight.Medium,
        fontSize   = BrandSize.Small,
        lineHeight = BrandSize.SmallLine,
        color      = DsBrand.Slate,
        textAlign  = if (center) TextAlign.Center else TextAlign.Start,
        modifier   = Modifier.fillMaxWidth()
    )
}

package com.distrigo.app.ui.navigation

import com.distrigo.app.diagnostics.rememberTrackedNavController
import androidx.compose.runtime.Composable
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.ui.clients.ClientFormScreen
import com.distrigo.app.ui.clients.ClientPlacementUiState
import com.distrigo.app.ui.clients.ClientPlacementViewModel
import com.distrigo.app.ui.clients.ClientSelectionViewModel
import com.distrigo.app.ui.clients.ClientViewModel
import com.distrigo.app.ui.clients.ClientsScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.distrigo.app.data.lists.ListKind
import com.distrigo.app.ui.secteurs.QuartierDetailScreen
import com.distrigo.app.ui.secteurs.QuartierDetailViewModel
import com.distrigo.app.ui.secteurs.QuartierFormScreen
import com.distrigo.app.ui.secteurs.QuartierFormViewModel
import com.distrigo.app.ui.secteurs.SecteurDetailScreen
import com.distrigo.app.ui.secteurs.SecteurDetailViewModel
import com.distrigo.app.ui.secteurs.SecteurFormScreen
import com.distrigo.app.ui.secteurs.SecteurFormViewModel
import com.distrigo.app.ui.secteurs.SecteursScreen
import com.distrigo.app.ui.settings.ParametresScreen
import com.distrigo.app.ui.settings.lists.ListEditorScreen
import com.distrigo.app.ui.settings.lists.ListEditorViewModel
import com.distrigo.app.ui.settings.lists.ListsHomeScreen
import com.distrigo.app.ui.settings.data.DataBackupScreen
import com.distrigo.app.ui.settings.data.export.ExportScreen
import com.distrigo.app.ui.settings.incentive.CommissionPolicyScreen
import com.distrigo.app.ui.settings.print.PrinterSelectionScreen
import com.distrigo.app.ui.settings.print.ReceiptAndPrintSettingsScreen
import com.distrigo.app.ui.settings.trash.TrashScreen
import com.distrigo.app.ui.settings.diagnostics.DiagnosticsScreen

private const val RESULT_ADDED_IDS = "added_client_ids"
private const val RESULT_ADDED_MESSAGE = "added_client_message"

/** « 3 clients ajoutés · 1 déplacé ». */
private fun addedMessage(added: Int, moved: Int): String =
    (if (added > 1) "$added clients ajoutés" else "$added client ajouté") +
        (if (moved > 0) " · $moved ${if (moved > 1) "déplacés" else "déplacé"}" else "")

/**
 * Paramètres and the screens under it, each a destination: Back and the stack are the navigation's, a screen
 * left and returned to is composed again (so it reads its data again), and nothing is held in a boolean.
 */
@Composable
fun SettingsNavHost(
    onBack: () -> Unit,
    /** « Compte et connexion »: the account screen, outside Paramètres. */
    onAccount: () -> Unit = {},
    /** A drill-down from a client: opens on that secteur's page, and Back from it leaves through [onBack]. */
    openSecteurId: Int? = null,
    /** The same, on that quartier's page. */
    openQuartierId: Int? = null,
) {
    val navController = rememberTrackedNavController()

    NavHost(
        navController      = navController,
        startDestination   = when {
            openSecteurId != null  -> Screen.SettingsSecteurDetail.route
            openQuartierId != null -> Screen.SettingsQuartierDetail.route
            else                   -> Screen.SettingsHome.route
        },
        route              = Screen.SettingsGraph.route,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(Screen.SettingsHome.route) {
            ParametresScreen(
                onBack       = onBack,
                onAccount    = onAccount,
                onLists      = { navController.navigate(Screen.SettingsLists.route) },
                onReceipt    = { navController.navigate(Screen.SettingsReceiptPrint.route) },
                onCommission = { navController.navigate(Screen.SettingsCommission.route) },
                onExport     = { navController.navigate(Screen.SettingsExport.route) },
                onData       = { navController.navigate(Screen.SettingsData.route) },
                onTrash      = { navController.navigate(Screen.SettingsTrash.route) },
                onDiagnostics = { navController.navigate(Screen.SettingsDiagnostics.route) }
            )
        }
        composable(Screen.SettingsLists.route) {
            ListsHomeScreen(
                onBack = { navController.popBackStack() },
                onOpen = { kind ->
                    // Secteurs have their own screens (quartiers, clients); the other lists the generic editor
                    navController.navigate(if (kind == ListKind.SECTEURS) Screen.SettingsSecteurs.route else Screen.SettingsList.createRoute(kind))
                },
            )
        }
        composable(Screen.SettingsSecteurs.route) {
            SecteursScreen(
                onBack = { navController.popBackStack() },
                onNew  = { navController.navigate(Screen.SettingsSecteurForm.createRoute()) },
                onOpen = { id -> navController.navigate(Screen.SettingsSecteurDetail.createRoute(id)) },
            )
        }
        composable(
            route     = Screen.SettingsSecteurDetail.route,
            // Started on, it has no route to read its id from: the drill-down's id is the default
            arguments = listOf(navArgument(SecteurDetailViewModel.ARG_SECTEUR_ID) {
                type = NavType.LongType; openSecteurId?.let { defaultValue = it.toLong() }
            }),
        ) {
            SecteurDetailScreen(
                onBack         = { navController.popOr(onBack) },
                onEdit         = { id -> navController.navigate(Screen.SettingsSecteurForm.createRoute(id)) },
                onAddQuartier  = { id -> navController.navigate(Screen.SettingsQuartierForm.newIn(id)) },
                onOpenQuartier = { id -> navController.navigate(Screen.SettingsQuartierDetail.createRoute(id)) },
            )
        }
        composable(
            route     = Screen.SettingsQuartierDetail.route,
            arguments = listOf(navArgument(QuartierDetailViewModel.ARG_QUARTIER_ID) {
                type = NavType.LongType; openQuartierId?.let { defaultValue = it.toLong() }
            }),
        ) { entry ->
            // What « Ajouter des clients » sent back, read once: coming back again lights nothing
            val result = remember(entry) {
                entry.savedStateHandle.remove<IntArray>(RESULT_ADDED_IDS)?.toSet().orEmpty() to
                    entry.savedStateHandle.remove<String>(RESULT_ADDED_MESSAGE)
            }
            QuartierDetailScreen(
                onBack        = { navController.popOr(onBack) },
                onEdit        = { id -> navController.navigate(Screen.SettingsQuartierForm.edit(id)) },
                onAddExisting = { id -> navController.navigate(Screen.SettingsClients.select(id)) },
                onAddNew      = { id -> navController.navigate(Screen.SettingsClientForm.inQuartier(id)) },
                added         = result.first,
                addedMessage  = result.second,
            )
        }
        composable(
            route     = Screen.SettingsClients.route,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "browse" },
                navArgument(ClientViewModel.ARG_QUARTIER_ID) { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            val select = entry.arguments?.getString("mode") == Screen.SettingsClients.MODE_SELECT
            val selection: ClientSelectionViewModel? = if (select) hiltViewModel() else null
            ClientsScreen(
                onBack    = { navController.popBackStack() },
                selection = selection,
                onSelectionSaved = { saved ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(RESULT_ADDED_IDS, saved.ids.toIntArray())
                        set(RESULT_ADDED_MESSAGE, addedMessage(saved.added, saved.moved))
                    }
                    navController.popBackStack()
                },
            )
        }
        composable(
            route     = Screen.SettingsClientForm.route,
            arguments = listOf(navArgument(ClientPlacementViewModel.ARG_IN_QUARTIER) { type = NavType.LongType; defaultValue = 0L }),
        ) {
            val placementViewModel: ClientPlacementViewModel = hiltViewModel()
            val formViewModel: ClientViewModel = hiltViewModel()
            when (val state = placementViewModel.state.collectAsState().value) {
                ClientPlacementUiState.Loading -> Unit
                is ClientPlacementUiState.Error -> {
                    // The quartier went away: nothing to fill, back where it was listed, saying why
                    val context = LocalContext.current
                    LaunchedEffect(state) {
                        Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                        navController.popBackStack()
                    }
                }
                is ClientPlacementUiState.Success -> ClientFormScreen(
                    placement = state.placement,
                    viewModel = formViewModel,
                    onBack    = { navController.popBackStack() },
                    onSaved   = { navController.popBackStack() },
                    onAdded   = { newId, name ->
                        navController.previousBackStackEntry?.savedStateHandle?.apply {
                            set(RESULT_ADDED_IDS, intArrayOf(newId))
                            set(RESULT_ADDED_MESSAGE, "Client « $name » ajouté")
                        }
                        navController.popBackStack()
                    },
                )
            }
        }
        composable(
            route     = Screen.SettingsQuartierForm.route,
            arguments = listOf(
                navArgument(QuartierFormViewModel.ARG_SECTEUR_ID) { type = NavType.LongType; defaultValue = 0L },
                navArgument(QuartierFormViewModel.ARG_QUARTIER_ID) { type = NavType.LongType; defaultValue = 0L },
            ),
        ) {
            QuartierFormScreen(
                onBack    = { navController.popBackStack() },
                onSaved   = { navController.popBackStack() },
                // Deleted from its details: they go too, back to its secteur — or out, opened on the quartier
                onDeleted = { if (!navController.popBackStack(Screen.SettingsSecteurDetail.route, inclusive = false)) onBack() },
            )
        }
        composable(
            route     = Screen.SettingsSecteurForm.route,
            arguments = listOf(navArgument(SecteurFormViewModel.ARG_SECTEUR_ID) { type = NavType.LongType; defaultValue = 0L }),
        ) {
            SecteurFormScreen(
                onBack    = { navController.popBackStack() },
                onSaved   = { navController.popBackStack() },
                // Deleted from its details: they go too, back to the list — or out, opened on the secteur
                onDeleted = { if (!navController.popBackStack(Screen.SettingsSecteurs.route, inclusive = false)) onBack() },
            )
        }
        composable(
            route     = Screen.SettingsList.route,
            arguments = listOf(
                navArgument(ListEditorViewModel.ARG_KIND) { type = NavType.StringType },
                navArgument(ListEditorViewModel.ARG_PARENT_ID) { type = NavType.LongType; defaultValue = 0L },
                navArgument(ListEditorViewModel.ARG_PARENT_NAME) { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            ListEditorScreen(
                onBack = { navController.popBackStack() },
                onOpenChildren = { child, parent -> navController.navigate(Screen.SettingsList.createRoute(child, parent.id, parent.name)) },
            )
        }

        composable(Screen.SettingsReceiptPrint.route) {
            ReceiptAndPrintSettingsScreen(
                onBack     = { navController.popBackStack() },
                onPrinters = { navController.navigate(Screen.SettingsPrinters.route) },
            )
        }
        composable(Screen.SettingsPrinters.route) {
            PrinterSelectionScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SettingsCommission.route) {
            CommissionPolicyScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SettingsData.route) {
            DataBackupScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SettingsExport.route) {
            ExportScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SettingsTrash.route) {
            TrashScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SettingsDiagnostics.route) {
            DiagnosticsScreen(onBack = { navController.popBackStack() })
        }
    }
}

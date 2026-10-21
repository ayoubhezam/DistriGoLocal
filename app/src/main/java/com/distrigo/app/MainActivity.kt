package com.distrigo.app

import com.distrigo.app.diagnostics.rememberTrackedNavController
import kotlinx.coroutines.withContext
import com.distrigo.app.ui.common.RestoringScreen
import com.distrigo.app.data.backup.RestoreStartup
import android.graphics.Rect
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.distrigo.app.ui.navigation.DRILL_ID
import com.distrigo.app.ui.navigation.DrillTarget
import com.distrigo.app.ui.navigation.LocalDrillDown
import com.distrigo.app.ui.navigation.drillDown
import androidx.core.view.WindowCompat
import com.distrigo.app.R
import com.distrigo.app.ui.clients.ClientsScreen
import com.distrigo.app.ui.dashboard.DashboardScreen
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsElevation
import com.distrigo.app.ui.designsystem.DsGradients
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.more.MoreScreen
import com.distrigo.app.ui.navigation.Screen
import com.distrigo.app.ui.navigation.navEnterTransition
import com.distrigo.app.ui.navigation.navExitTransition
import com.distrigo.app.ui.navigation.navPopEnterTransition
import com.distrigo.app.ui.navigation.navPopExitTransition
import com.distrigo.app.ui.products.ProductsScreen
import com.distrigo.app.ui.purchases.PurchasesScreen
import com.distrigo.app.ui.suppliers.SuppliersScreen
import com.distrigo.app.ui.theme.DistriGoTheme
import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.data.repository.BusinessSettingsRepository
import com.distrigo.app.ui.format.ProvideMoneyFormat
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.distrigo.app.ui.tournees.TourneesHubScreen
import kotlin.math.abs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.license.holdsLicense
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Lazy, and built on IO below: injection runs inside super.onCreate, on the main thread, and building the
    // database waits for the restore check — which also kept the "restoring" screen from ever showing, as
    // injection blocked before the code that decides to show it.
    @javax.inject.Inject
    lateinit var database: dagger.Lazy<com.distrigo.app.data.local.database.AppDatabase>

    // Lazy for the same reason, and opened on IO: the repository needs that database.
    @javax.inject.Inject
    lateinit var businessSettings: dagger.Lazy<BusinessSettingsRepository>

    // Lazy and read on IO, like the database: the account's initials for every avatar (LocalAccountInitials).
    @javax.inject.Inject
    lateinit var authRepository: dagger.Lazy<com.distrigo.app.data.auth.AuthRepository>

    // Lazy and read on IO too: whether this phone holds a license, for the fresh-install onboarding.
    @javax.inject.Inject
    lateinit var licenseManager: dagger.Lazy<com.distrigo.app.data.license.LicenseManager>

    // Lazy and built on IO: the license while the app is in front (docs/license_l4_checkin.md §5.4).
    @javax.inject.Inject
    lateinit var licenseInFront: dagger.Lazy<com.distrigo.app.data.license.LicenseInFront>

    // Lazy and used on IO: « Réessayer » on the license's banner and sheet asks for a check-in now.
    @javax.inject.Inject
    lateinit var licenseCheckInScheduler: dagger.Lazy<com.distrigo.app.data.license.LicenseCheckInScheduler>

    /** The business's money format, read off the main thread. */
    private val moneyFormats: Flow<MoneyFormat> by lazy {
        flow { emitAll(businessSettings.get().observeMoneyFormat()) }.flowOn(Dispatchers.IO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Declared, not inherited. From API 35 the system enforces edge-to-edge whatever the
        // manifest says — `windowSoftInputMode="adjustResize"` included, which is why the window is
        // never resized for the keyboard and the app must consume the IME inset itself (see the
        // root's imePadding below). Saying so here makes API 34 and below behave the same way,
        // rather than resizing there and not here — which would make that one padding double up.
        // The system-bar icon appearance is set by DistriGoTheme, which runs after this.
        enableEdgeToEdge()
        com.distrigo.app.data.geo.GeoRepository.init(this)
        if (RestoreStartup.inProgress) {
            // A restore is being installed on its own thread: wait for it with a screen rather than a frozen frame.
            setContent { DistriGoTheme { RestoringScreen() } }
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { RestoreStartup.await() }
                showApp()
            }
        } else {
            showApp()
        }
    }

    /** Builds the app's UI, and starts what runs beside it. Only once the database may be opened. */
    private fun showApp() {
        setContent {
            // Every amount on every screen follows the business's "Format des montants" (see
            // LocalMoneyFormatter). Saveable, so a rotation or a recreated activity keeps the format
            // it had instead of showing the default until the row is read again; only a cold start
            // waits for that read, which comes before any screen's own data.
            var moneyFormat by rememberSaveable { mutableStateOf(MoneyFormat.DEFAULT) }
            LaunchedEffect(Unit) { moneyFormats.collect { moneyFormat = it } }
            var accountInitials by rememberSaveable { mutableStateOf<String?>(null) }
            var accountByCode by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) { authRepository.get() }.state.collect { account ->
                    val session = (account as? com.distrigo.app.data.auth.AccountState.SignedIn)?.session
                    accountInitials = session?.initials
                    accountByCode = session?.isAnonymous == true
                    // Whoever has had an account here knows DistriGo: no tour for them again (EntryPolicy)
                    if (session != null) withContext(Dispatchers.IO) { com.distrigo.app.ui.onboarding.OnboardingIntro.markSeen(this@MainActivity) }
                }
            }
            // The onboarding (docs/activation_codes.md), where it opens; required on a fresh install, where
            // Back leaves the app rather than the onboarding.
            val onboardingViewModel: com.distrigo.app.ui.onboarding.OnboardingViewModel = hiltViewModel()
            var onboarding by rememberSaveable { mutableStateOf<com.distrigo.app.ui.onboarding.OnboardingStart?>(null) }
            var onboardingRequired by rememberSaveable { mutableStateOf(false) }
            val openOnboarding: (com.distrigo.app.ui.onboarding.OnboardingStart) -> Unit = { start ->
                onboardingViewModel.reset()
                onboarding = start
            }
            // A sign-in made in Compte's own form that needs a step only the onboarding shows — a company, the
            // seat moved here, a code — or that succeeded: the onboarding opens on it, as it is, without a reset
            val onboardingStatus by onboardingViewModel.status.collectAsState()
            LaunchedEffect(onboardingStatus.followUp) {
                if (onboardingStatus.followUp && onboarding == null) onboarding = com.distrigo.app.ui.onboarding.OnboardingStart.SIGN_IN
            }
            // Only a phone with no account, no license and nothing recorded opens on the onboarding: the welcome tour
            // the first time, Connexion after (EntryPolicy). A phone that has its data (an update, a restore, a
            // sign-out) opens on it, and Compte invites it to sign in.
            LaunchedEffect(Unit) {
                val entry = withContext(Dispatchers.IO) {
                    com.distrigo.app.ui.onboarding.EntryPolicy.atLaunch(
                        account      = authRepository.get().state.first { it != com.distrigo.app.data.auth.AccountState.Loading },
                        holdsLicense = { licenseManager.get().evaluate().holdsLicense },
                        hasData      = { database.get().appMetaDao().hasBusinessData() },
                        introSeen    = { com.distrigo.app.ui.onboarding.OnboardingIntro.seen(this@MainActivity) },
                    )
                }
                if (entry is com.distrigo.app.ui.onboarding.Entry.Onboarding && onboarding == null) {
                    onboardingRequired = entry.required
                    openOnboarding(entry.start)
                }
            }
            DistriGoTheme { ProvideMoneyFormat(moneyFormat) { androidx.compose.runtime.CompositionLocalProvider(
                com.distrigo.app.ui.designsystem.LocalAccountInitials provides accountInitials,
                com.distrigo.app.ui.designsystem.LocalAccountByCode provides accountByCode
            ) {
                // imePadding here is what lifts every form in the app: the root shrinks by the
                // keyboard's height, so each scroll container inside shrinks with it, and Compose's
                // own bring-into-view can finally scroll the focused field up. Padding a screen
                // without shrinking its scroller would move nothing.
                BoxWithConstraints(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                    var hideBottomBar by remember { mutableStateOf(false) }
                    val navController = rememberTrackedNavController()
                    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                    val isTabRoute = currentRoute == Screen.TabDashboard.route ||
                        currentRoute == Screen.TabVentes.route ||
                        currentRoute == Screen.TabProduits.route ||
                        currentRoute == Screen.TabAchats.route

                    // The data belongs to another business than the account's (docs/license_l4_checkin.md §7): said once
                    // per start, and answered in Compte, which says it too. Not over the onboarding.
                    var licenseState by remember { mutableStateOf<com.distrigo.app.data.license.LicenseState>(com.distrigo.app.data.license.LicenseState.Unchecked) }
                    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { licenseManager.get() }.state.collect { licenseState = it } }

                    // The license on the screens (docs/license_l5_enforcement.md §2): the banner under the bars, and the
                    // « Consultation seule » sheet a blocked write opens, hosted once below.
                    val licenseUi = remember(navController) {
                        com.distrigo.app.ui.designsystem.LicenseUi(
                            act = { action -> actOnLicense(action, navController) },
                            onDismissed = { key ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    getSharedPreferences(LICENSE_UI_PREFS, MODE_PRIVATE).edit().putString(KEY_BANNER_DISMISSED, key).apply()
                                }
                            },
                        )
                    }
                    LaunchedEffect(licenseUi) {
                        licenseUi.dismissedKey = withContext(Dispatchers.IO) {
                            getSharedPreferences(LICENSE_UI_PREFS, MODE_PRIVATE).getString(KEY_BANNER_DISMISSED, null)
                        }
                    }
                    LaunchedEffect(licenseState) {
                        // Debug builds can draw another state's screens (LicensePreview); the write gate stays the license's
                        val preview = withContext(Dispatchers.IO) { com.distrigo.app.data.debug.LicensePreview.read(filesDir, licenseState) }
                        licenseUi.policy = com.distrigo.app.data.license.LicensePolicy.of(preview ?: licenseState)
                    }
                    LaunchedEffect(licenseUi) { com.distrigo.app.ui.designsystem.LicenseRefusals.events.collect { licenseUi.explain() } }
                    var otherBusinessTold by rememberSaveable { mutableStateOf(false) }
                    if (licenseState is com.distrigo.app.data.license.LicenseState.OtherBusiness && !otherBusinessTold &&
                        onboarding == null && currentRoute != Screen.Compte.route
                    ) {
                        com.distrigo.app.ui.designsystem.BrandDialog(
                            title   = "Ces données appartiennent à une autre entreprise",
                            text    = "Les données de ce téléphone ont été enregistrées pour une autre entreprise que celle du compte " +
                                "connecté. Dans Compte, gardez-les pour votre entreprise, ou déconnectez-vous.",
                            confirm = "Ouvrir Compte" to {
                                otherBusinessTold = true
                                navController.navigate(Screen.Compte.route) { launchSingleTop = true }
                            },
                            dismiss = "Plus tard" to { otherBusinessTold = true },
                        )
                    }

                    val density = LocalDensity.current
                    val maxDrawerWidthPx = remember(maxWidth, density) {
                        with(density) { maxWidth.toPx() } * PlusDrawerMaxWidthFraction
                    }
                    val flingVelocityPx = remember(density) {
                        with(density) { PlusDrawerFlingVelocity.toPx() }
                    }
                    val drawerOffset = remember { Animatable(0f) }
                    val scope = rememberCoroutineScope()

                    // Drag events are queued here and drained by a single coroutine below, so every
                    // snapTo/animateTo call on drawerOffset happens strictly in order. Firing a
                    // separate scope.launch { snapTo(drawerOffset.value + delta) } per pointer-move
                    // event (the previous approach) let multiple coroutines race to read-modify-write
                    // the same value concurrently, silently dropping most of the dragged distance.
                    val drawerDragEvents = remember { Channel<PlusDrawerDragEvent>(Channel.UNLIMITED) }
                    LaunchedEffect(drawerDragEvents) {
                        for (event in drawerDragEvents) {
                            when (event) {
                                is PlusDrawerDragEvent.Move -> {
                                    val target = (drawerOffset.value + event.deltaPx).coerceIn(0f, maxDrawerWidthPx)
                                    drawerOffset.snapTo(target)
                                }
                                is PlusDrawerDragEvent.End -> {
                                    val shouldOpen = when {
                                        event.velocityPxPerSec > flingVelocityPx  -> true
                                        event.velocityPxPerSec < -flingVelocityPx -> false
                                        else -> drawerOffset.value >= maxDrawerWidthPx * PlusDrawerOpenThresholdFraction
                                    }
                                    drawerOffset.animateTo(
                                        if (shouldOpen) maxDrawerWidthPx else 0f,
                                        tween(PlusDrawerAnimationDurationMs)
                                    )
                                }
                            }
                        }
                    }

                    // pointerInput below runs in a coroutine keyed on Unit (never restarted), so it
                    // must read route state through rememberUpdatedState rather than closing over
                    // isTabRoute directly, or it would keep seeing stale values.
                    val latestIsTabRoute = rememberUpdatedState(isTabRoute)

                    fun openDrawer() {
                        scope.launch { drawerOffset.animateTo(maxDrawerWidthPx, tween(PlusDrawerAnimationDurationMs)) }
                    }
                    fun closeDrawer() {
                        scope.launch { drawerOffset.animateTo(0f, tween(PlusDrawerAnimationDurationMs)) }
                    }

                    // ── Leaving the app: Back on the home tab asks first. Every other tab already
                    // returns to the home tab (navigateToTab pops to it), so this is the only Back that
                    // would otherwise close the app. An open Plus drawer closes on Back instead. ──
                    val drawerOpen = drawerOffset.value > 0f
                    var confirmExit by remember { mutableStateOf(false) }
                    androidx.activity.compose.BackHandler(enabled = drawerOpen) { closeDrawer() }
                    androidx.activity.compose.BackHandler(
                        enabled = !drawerOpen && currentRoute == Screen.TabDashboard.route
                    ) { confirmExit = true }
                    if (confirmExit) {
                        AlertDialog(
                            onDismissRequest = { confirmExit = false },
                            text = { Text("Voulez-vous quitter l'application DistriGo ?", fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary) },
                            confirmButton = {
                                TextButton(onClick = { confirmExit = false; finish() }) {
                                    Text("Oui", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmExit = false }) {
                                    Text("Non", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                                }
                            },
                            containerColor = DsColors.Surface
                        )
                    }

                    // ── Bottom-tab switch: standard "multiple back stacks" pattern —
                    // pop to the graph's start destination (saving each tab's state) before
                    // navigating, then restore the target tab's saved state if it has any. ──
                    fun navigateToTab(route: String) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    // Gesture detector is attached to this ancestor Box (not a separate
                    // full-screen sibling on top) so Scaffold's bottom bar and the drawer's
                    // own menu clicks still receive taps normally — this must be an ancestor
                    // of everything it should let taps pass through to, exactly like the
                    // previously-verified plusEdgeSwipeGestures pattern.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .plusDrawerDragGestures(
                                isEligibleToOpen = { latestIsTabRoute.value },
                                currentOffsetPx  = { drawerOffset.value },
                                onDrag           = { deltaPx -> drawerDragEvents.trySend(PlusDrawerDragEvent.Move(deltaPx)) },
                                onDragEnd        = { velocityPxPerSec -> drawerDragEvents.trySend(PlusDrawerDragEvent.End(velocityPxPerSec)) }
                            )
                    ) {
                    Scaffold(
                        modifier       = Modifier.navigationBarsPadding(),
                        // Nothing painted: the window's own background is this white already
                        // (themes.xml), and every screen paints its own on top. Painting it here too
                        // filled every pixel of every frame a second time. The text colour stays
                        // the one this white gave.
                        containerColor = Color.Transparent,
                        contentColor   = contentColorFor(DsColors.Surface)
                    ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                    // Drill-down: any screen, in any section, opens a record through the root — the
                    // only NavHost that knows every section (see DrillDown).
                    val drill: (DrillTarget) -> Unit = remember(navController) { { target -> drillDown(navController, target) } }
                    CompositionLocalProvider(
                        LocalDrillDown provides drill,
                        com.distrigo.app.ui.designsystem.LocalLicense provides licenseUi
                    ) {
                        NavHost(
                            navController      = navController,
                            startDestination   = Screen.TabDashboard.route,
                            enterTransition    = navEnterTransition,
                            exitTransition     = navExitTransition,
                            popEnterTransition = navPopEnterTransition,
                            popExitTransition  = navPopExitTransition
                        ) {
                            // ── Bottom-tab destinations ──
                            // The menu opens the same Plus drawer the edge swipe does — until now that
                            // swipe was the only way in, with nothing on screen advertising it.
                            // Notifications and profile are visual only for now: the callbacks land
                            // here so the wiring is one edit away once those screens exist.
                            composable(Screen.TabDashboard.route) {
                                com.distrigo.app.ui.navigation.DashboardNavHost(
                                    onFullScreenChange   = { hideBottomBar = it },
                                    onOpenReport         = { report -> navController.navigate(Screen.PlusRapport.createRoute(report)) },
                                    onOpenMenu           = { openDrawer() },
                                    onNotificationsClick = { /* TODO: notifications */ },
                                    onProfileClick       = { navController.navigate(Screen.Compte.route) { launchSingleTop = true } }
                                )
                            }

                            composable(Screen.TabVentes.route) {
                                TourneesHubScreen(
                                    onFullScreenChange = { hideBottomBar = it },
                                    onOpenMenu           = { openDrawer() },
                                    onNotificationsClick = { /* TODO: notifications */ },
                                    onProfileClick       = { navController.navigate(Screen.Compte.route) { launchSingleTop = true } }
                                )
                            }

                            composable(Screen.TabProduits.route) {
                                com.distrigo.app.ui.navigation.ProduitsNavHost(
                                    onFullScreenChange   = { hideBottomBar = it },
                                    onOpenMenu           = { openDrawer() },
                                    onNotificationsClick = { /* TODO: notifications */ },
                                    onProfileClick       = { navController.navigate(Screen.Compte.route) { launchSingleTop = true } }
                                )
                            }

                            composable(Screen.TabAchats.route) {
                                com.distrigo.app.ui.navigation.AchatsNavHost(
                                    onFullScreenChange   = { hideBottomBar = it },
                                    onOpenMenu           = { openDrawer() },
                                    onNotificationsClick = { /* TODO: notifications */ },
                                    onProfileClick       = { navController.navigate(Screen.Compte.route) { launchSingleTop = true } }
                                )
                            }

                            // ── Plus menu items: real destinations, reached from the drawer overlay below ──
                            composable(Screen.PlusClients.route) {
                                com.distrigo.app.ui.navigation.ClientsNavHost(
                                    onFullScreenChange  = { hideBottomBar = it },
                                    onBack              = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.PlusFournisseurs.route) {
                                com.distrigo.app.ui.navigation.SuppliersNavHost(
                                    onFullScreenChange = { hideBottomBar = it },
                                    onNavigateToOrder  = { orderId -> drill(DrillTarget.Bon(orderId)) },
                                    onBack             = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.PlusCharges.route) {
                                com.distrigo.app.ui.navigation.ChargesNavHost(
                                    onFullScreenChange = { hideBottomBar = it },
                                    onBack             = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.PlusPertes.route) {
                                com.distrigo.app.ui.navigation.PertesNavHost(
                                    onFullScreenChange = { hideBottomBar = it },
                                    onBack             = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.PlusInventaire.route) {
                                com.distrigo.app.ui.navigation.InventoryNavHost(
                                    onBack              = { navController.popBackStack() },
                                    onFullScreenChange  = { hideBottomBar = it }
                                )
                            }

                            composable(Screen.PlusRapports.route) {
                                com.distrigo.app.ui.navigation.RapportsNavHost(
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                Screen.PlusRapport.route,
                                arguments = listOf(navArgument("report") { type = NavType.StringType })
                            ) { entry ->
                                com.distrigo.app.ui.navigation.RapportsNavHost(
                                    onBack = { navController.popBackStack() },
                                    open   = com.distrigo.app.ui.navigation.ReportEntry.valueOf(entry.arguments!!.getString("report")!!)
                                )
                            }

                            // The account and this phone's subscription, from the avatar (docs/license_architecture.md §7).
                            composable(Screen.Compte.route) {
                                com.distrigo.app.ui.navigation.AccountNavHost(
                                    onBack           = { navController.popBackStack() },
                                    onOpenOnboarding = openOnboarding
                                )
                            }

                            // « Abonnement » straight from the license's banner or sheet (§8.2); Compte opens it too
                            composable(Screen.Abonnement.route) {
                                com.distrigo.app.ui.navigation.AccountNavHost(
                                    onBack           = { navController.popBackStack() },
                                    onOpenOnboarding = openOnboarding,
                                    start            = Screen.AccountAbonnement.route
                                )
                            }

                            composable(Screen.PlusParametres.route) {
                                com.distrigo.app.ui.navigation.SettingsNavHost(
                                    onBack    = { navController.popBackStack() },
                                    onAccount = { navController.navigate(Screen.Compte.route) { launchSingleTop = true } }
                                )
                            }

                            // ── Drill-down: a record opened from anywhere, its section started on it. Back
                            // from it returns to the screen that opened it. Not tab routes, so the bottom
                            // bar stays hidden, and the hosted section is not asked about it. ──
                            val drillId = listOf(navArgument(DRILL_ID) { type = NavType.IntType })
                            composable(Screen.DrillClient.route, arguments = drillId) { entry ->
                                com.distrigo.app.ui.navigation.ClientsNavHost(
                                    openClientId = entry.arguments!!.getInt(DRILL_ID),
                                    onBack       = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.DrillSupplier.route, arguments = drillId) { entry ->
                                com.distrigo.app.ui.navigation.SuppliersNavHost(
                                    openSupplierId    = entry.arguments!!.getInt(DRILL_ID),
                                    onNavigateToOrder = { orderId -> drill(DrillTarget.Bon(orderId)) },
                                    onBack            = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.DrillPerte.route, arguments = drillId) { entry ->
                                com.distrigo.app.ui.navigation.PertesNavHost(
                                    openPerteId = entry.arguments!!.getInt(DRILL_ID),
                                    onBack      = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.DrillInventaire.route, arguments = drillId) { entry ->
                                com.distrigo.app.ui.navigation.InventoryNavHost(
                                    openSessionId = entry.arguments!!.getInt(DRILL_ID),
                                    onBack        = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.DrillRetoursClient.route, arguments = drillId) { entry ->
                                com.distrigo.app.ui.navigation.ClientsNavHost(
                                    openRetoursOfClientId = entry.arguments!!.getInt(DRILL_ID),
                                    onBack                = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.DrillRetoursFournisseur.route, arguments = drillId) { entry ->
                                com.distrigo.app.ui.navigation.SuppliersNavHost(
                                    openRetoursOfSupplierId = entry.arguments!!.getInt(DRILL_ID),
                                    onNavigateToOrder       = { orderId -> drill(DrillTarget.Bon(orderId)) },
                                    onBack                  = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.DrillProduit.route, arguments = drillId) { entry ->
                                com.distrigo.app.ui.navigation.ProduitsNavHost(
                                    openProductId = entry.arguments!!.getInt(DRILL_ID),
                                    onBack        = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.DrillTournee.route, arguments = drillId) { entry ->
                                com.distrigo.app.ui.navigation.TourneesNavHost(
                                    openTourneeId = entry.arguments!!.getInt(DRILL_ID),
                                    onBack        = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.DrillVente.route, arguments = drillId) { entry ->
                                com.distrigo.app.ui.navigation.VentesNavHost(
                                    openVenteId = entry.arguments!!.getInt(DRILL_ID),
                                    onBack      = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.DrillBon.route, arguments = drillId) { entry ->
                                com.distrigo.app.ui.navigation.AchatsNavHost(
                                    openOrderId = entry.arguments!!.getInt(DRILL_ID),
                                    onBack      = { navController.popBackStack() }
                                )
                            }
                        }
                        com.distrigo.app.ui.designsystem.LicenseBlockSheet(licenseUi)
                    }
                    }
                }

                // ── Edge-to-edge bottom nav: full-width, flush with the screen bottom, overlaying NavHost
                // content directly (Scaffold no longer reserves height for it) so scrolled screen
                // content scrolls up to meet it. ──
                // The root is padded by the keyboard, so the bar would otherwise ride on top of it
                // and take ~48dp from the little room a form has left.
                @OptIn(ExperimentalLayoutApi::class)
                val imeVisible = WindowInsets.isImeVisible
                if (isTabRoute && !hideBottomBar && !imeVisible) {
                    ShiftingBottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate   = { route -> navigateToTab(route) },
                        modifier     = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // ── Plus drawer overlay: hugs the left edge, max 80% width, 1:1 drag-tracked ──
                val openFraction = if (maxDrawerWidthPx > 0f) {
                    (drawerOffset.value / maxDrawerWidthPx).coerceIn(0f, 1f)
                } else 0f

                if (openFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = openFraction * PlusDrawerScrimMaxAlpha))
                            .pointerInput(Unit) {
                                detectTapGestures { closeDrawer() }
                            }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(PlusDrawerMaxWidthFraction)
                        .align(Alignment.CenterStart)
                        .navigationBarsPadding()
                        .graphicsLayer { translationX = drawerOffset.value - maxDrawerWidthPx }
                ) {
                    ModalDrawerSheet(modifier = Modifier.fillMaxSize()) {
                        MoreScreen(onNavigate = { route ->
                            closeDrawer()
                            when (route) {
                                "clients"      -> navController.navigate(Screen.PlusClients.createRoute())
                                "fournisseurs" -> navController.navigate(Screen.PlusFournisseurs.route)
                                "charges"      -> navController.navigate(Screen.PlusCharges.route)
                                "pertes"       -> navController.navigate(Screen.PlusPertes.route)
                                "inventaire"   -> navController.navigate(Screen.PlusInventaire.route)
                                "rapports"     -> navController.navigate(Screen.PlusRapports.route)
                                "parametres"   -> navController.navigate(Screen.PlusParametres.route)
                            }
                        })
                    }
                }

                    } // closes Scaffold-wrapping gesture Box
            }

            // The onboarding: a sibling of the root rather than inside it, so it escapes the status-bar padding
            // and the welcome photo runs edge to edge.
            onboarding?.let { start ->
                com.distrigo.app.ui.onboarding.Onboarding(
                    viewModel  = onboardingViewModel,
                    start      = start,
                    // Closed or done, it starts afresh next time: a past success must not open it again
                    onFinished = {
                        onboarding = null
                        onboardingRequired = false
                        onboardingViewModel.reset()
                    },
                    onClose    = {
                        if (onboardingRequired) {
                            finish()
                        } else {
                            onboarding = null
                            onboardingViewModel.reset()
                        }
                    },
                    onIntroSeen = { lifecycleScope.launch(Dispatchers.IO) { com.distrigo.app.ui.onboarding.OnboardingIntro.markSeen(this@MainActivity) } }
                )
            }
        } } }
    }

        // Gesture-nav devices reserve roughly this same band on the left screen edge for the
        // system's own back gesture, which otherwise swallows our edge-swipe touches before
        // Compose ever sees them. Excluding it hands that band back to the app.
        reservePlusDrawerEdgeFromSystemGestures()

        // Moves any base64 image payloads left in the database out to files. After setContent and
        // on Dispatchers.IO, so it costs the first frame nothing; it does its own no-op check, and
        // a run that never finishes is resumed next launch — see ImageBackfill.
        lifecycleScope.launch {
            val db = withContext(Dispatchers.IO) { database.get() }
            com.distrigo.app.data.image.ImageBackfill.runIfNeeded(this@MainActivity, db)
        }

        // Parses the wilaya/commune file off the main thread. It used to be parsed before
        // setContent; GeoRepository.init above now only records the context.
        lifecycleScope.launch(Dispatchers.IO) {
            com.distrigo.app.data.geo.GeoRepository.preload()
        }

        // While the app is in front: the license read again (at once, every 15 minutes, on a date change) and a
        // check-in when one is due. Stopped when the app leaves the front, started again when it comes back.
        lifecycleScope.launch {
            val inFront = withContext(Dispatchers.IO) { licenseInFront.get() }
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { inFront.whileInFront() }
        }
    }

    /** What the license's banner and sheet buttons do (docs/license_l5_enforcement.md §8.1). */
    private fun actOnLicense(action: com.distrigo.app.data.license.LicensePolicy.Action, navController: androidx.navigation.NavController) {
        when (action) {
            com.distrigo.app.data.license.LicensePolicy.Action.SUBSCRIPTION ->
                navController.navigate(Screen.Abonnement.route) { launchSingleTop = true }
            com.distrigo.app.data.license.LicensePolicy.Action.ACCOUNT ->
                navController.navigate(Screen.Compte.route) { launchSingleTop = true }
            com.distrigo.app.data.license.LicensePolicy.Action.RETRY -> {
                lifecycleScope.launch(Dispatchers.IO) { licenseCheckInScheduler.get().checkInNow() }
                android.widget.Toast.makeText(this, "Vérification de l'abonnement…", android.widget.Toast.LENGTH_SHORT).show()
            }
            com.distrigo.app.data.license.LicensePolicy.Action.DATE_SETTINGS ->
                runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS)) }
        }
    }

    private fun reservePlusDrawerEdgeFromSystemGestures() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val decorView = window.decorView
        val exclusionWidthPx = with(Density(resources.displayMetrics.density)) {
            PlusDrawerEdgeZone.toPx()
        }.toInt()

        fun updateExclusionRects() {
            val height = decorView.height
            if (height <= 0) return
            decorView.systemGestureExclusionRects = listOf(
                Rect(0, 0, exclusionWidthPx, height)
            )
        }

        decorView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateExclusionRects() }
        decorView.post { updateExclusionRects() }
    }
}

// Mirrors NavTransitions.kt's private NavTransitionMs (300); duplicated here rather than exposed
// from that file, since this pass is scoped to leave NavTransitions.kt untouched.
/** The license banner closed for the day (decision 5 of docs/license_l5_enforcement.md): `{day}|{text}`. */
private const val LICENSE_UI_PREFS = "license_ui"
private const val KEY_BANNER_DISMISSED = "banner_dismissed"

private const val ShiftingNavAnimationMs      = 300
private const val ShiftingNavSelectedWeight   = 1.8f
private const val ShiftingNavUnselectedWeight = 1f

@Composable
private fun ShiftingBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // The bar sits behind the system navigation bar, so the gesture handle is drawn over it and
    // has to contrast with the container. Which appearance that needs follows from the container
    // itself, so re-theming the bar carries the handle with it; the theme's own light default is
    // handed back when the bar leaves.
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view) {
            val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
            controller.isAppearanceLightNavigationBars = DsColors.NavBarNeedsDarkSystemIcons
            onDispose { controller.isAppearanceLightNavigationBars = true }
        }
    }

    // The bar is edge-to-edge and flush with the screen bottom, so it paints behind the system
    // navigation bar; its inset is added on top of the clearance here and re-applied as padding
    // on the item Row below, keeping the icons above the gesture handle.
    val systemNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DsSpacing.bottomNavClearance + systemNavInset)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(DsGradients.BottomFade)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Surface(
                modifier        = Modifier.fillMaxWidth(),
                shape           = RectangleShape,
                color           = DsColors.NavBarContainer,
                shadowElevation = DsElevation.none
            ) {
              Column(modifier = Modifier.fillMaxWidth()) {
                // A container that shares the content's own surface cannot lean on a shadow to
                // separate itself from what scrolls up to meet it, so it takes a hairline instead.
                HorizontalDivider(thickness = 1.dp, color = DsColors.NavBarBorder)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = DsSpacing.md)
                        .padding(bottom = systemNavInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShiftingNavItem(
                        selected = currentRoute == Screen.TabDashboard.route,
                        icon     = Icons.Default.Home,
                        label    = stringResource(R.string.nav_dashboard),
                        onClick  = { onNavigate(Screen.TabDashboard.route) }
                    )
                    ShiftingNavItem(
                        selected = currentRoute == Screen.TabVentes.route,
                        icon     = Icons.Default.LocalShipping,
                        label    = stringResource(R.string.nav_ventes),
                        onClick  = { onNavigate(Screen.TabVentes.route) }
                    )
                    ShiftingNavItem(
                        selected = currentRoute == Screen.TabProduits.route,
                        icon     = Icons.Default.ShoppingCart,
                        label    = stringResource(R.string.nav_produits),
                        onClick  = { onNavigate(Screen.TabProduits.route) }
                    )
                    ShiftingNavItem(
                        selected = currentRoute == Screen.TabAchats.route,
                        icon     = Icons.Default.Receipt,
                        label    = stringResource(R.string.nav_achats),
                        onClick  = { onNavigate(Screen.TabAchats.route) }
                    )
                }
              }
            }
        }
    }
}

@Composable
private fun RowScope.ShiftingNavItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val weight by animateFloatAsState(
        targetValue   = if (selected) ShiftingNavSelectedWeight else ShiftingNavUnselectedWeight,
        animationSpec = tween(ShiftingNavAnimationMs),
        label         = "shifting_nav_item_weight"
    )
    val contentColor = if (selected) DsColors.NavBarSelected else DsColors.NavBarUnselected

    Row(
        modifier = Modifier
            .weight(weight)
            .clip(DsShapes.pill)
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onClick() }
            .padding(horizontal = DsSpacing.xs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint     = contentColor,
            modifier = Modifier.size(24.dp)
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(ShiftingNavAnimationMs)) + expandHorizontally(tween(ShiftingNavAnimationMs)),
            exit  = fadeOut(tween(ShiftingNavAnimationMs)) + shrinkHorizontally(tween(ShiftingNavAnimationMs))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(DsSpacing.xs))
                Text(
                    label,
                    color      = contentColor,
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1
                )
            }
        }
    }
}

// ── Plus drawer: hugs the left edge, capped at 80% screen width, dragged 1:1 with the finger,
// scrim synced to open-fraction, snaps open/closed on release by position or fling velocity. ──
private val PlusDrawerEdgeZone              : Dp = 28.dp  // how close to the left edge a touch must start when closed (spec: ~24–32dp)
private val PlusDrawerVerticalBailThreshold : Dp = 32.dp  // vertical travel beyond which we assume a scroll and back off
private val PlusDrawerMinDragDistance       : Dp = 16.dp  // below this, treat it as a tap and let the scrim's own tap-to-close handle it instead
private val PlusDrawerFlingVelocity         : Dp = 800.dp // per second — fast enough swipe forces open/closed regardless of position
private const val PlusDrawerMaxWidthFraction      = 0.8f  // 80% of screen width, max
private const val PlusDrawerOpenThresholdFraction = 0.5f  // 50% of the drawer's own extent == 40% of screen width
private const val PlusDrawerScrimMaxAlpha         = 0.5f  // scrim alpha at fully open (0f..1f)
private const val PlusDrawerAnimationDurationMs   = 300

private sealed class PlusDrawerDragEvent {
    data class Move(val deltaPx: Float) : PlusDrawerDragEvent()
    data class End(val velocityPxPerSec: Float) : PlusDrawerDragEvent()
}

private fun Modifier.plusDrawerDragGestures(
    isEligibleToOpen : () -> Boolean,
    currentOffsetPx  : () -> Float,
    onDrag           : (deltaPx: Float) -> Unit,
    onDragEnd        : (velocityPxPerSec: Float) -> Unit
): Modifier = this.pointerInput(Unit) {
    val edgeZonePx     = PlusDrawerEdgeZone.toPx()
    val verticalBailPx = PlusDrawerVerticalBailThreshold.toPx()
    val minDragPx      = PlusDrawerMinDragDistance.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val offsetAtStart = currentOffsetPx()

        // Closed: only an edge-starting touch may begin opening it. Already open/partial: any
        // touch on the visible drawer sheet itself (x < current offset) is left alone so its own
        // clickable menu rows still work; only touches on the exposed scrim continue the drag.
        val eligible = if (offsetAtStart <= 0f) {
            isEligibleToOpen() && down.position.x <= edgeZonePx
        } else {
            down.position.x >= offsetAtStart
        }
        if (!eligible) return@awaitEachGesture

        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        val alreadyOpen = offsetAtStart > 0f

        var totalDx = 0f
        var totalDy = 0f
        var committed = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            val delta = change.positionChange()
            totalDx += delta.x
            totalDy += delta.y
            velocityTracker.addPosition(change.uptimeMillis, change.position)

            if (!committed) {
                if (alreadyOpen) {
                    // A small amount of real movement (either axis) re-engages the drag — but it
                    // must be proven first, or a plain tap on the scrim (near-zero movement, yet
                    // still forwarded via onDrag) sends tiny snapTo nudges that interrupt/override
                    // the scrim's own closeDrawer() call right after it starts, leaving the drawer
                    // stuck open.
                    if (abs(totalDx) > minDragPx || abs(totalDy) > minDragPx) {
                        committed = true
                        change.consume()
                        onDrag(totalDx)
                    }
                } else {
                    // Closed: require clearly-horizontal movement so vertical scrolls on tab
                    // content aren't hijacked while opening from the edge.
                    if (abs(totalDy) > verticalBailPx && abs(totalDy) > abs(totalDx)) break // vertical wins — let it scroll
                    if (abs(totalDx) > verticalBailPx) {
                        committed = true
                        change.consume()
                        // Catch up with everything accumulated during the "proving it's
                        // horizontal" phase — forwarding only this event's own delta here would
                        // silently discard that whole distance.
                        onDrag(totalDx)
                    }
                }
            } else {
                change.consume()
                onDrag(delta.x)
            }

            if (!change.pressed) break
        }

        // A pure tap on the scrim (negligible movement) is left entirely to the scrim's own
        // detectTapGestures/closeDrawer() — without this check, "already open" gestures are
        // committed from their very first pixel, so a tap's near-zero movement would still send
        // an End event whose position-based decision trivially reaffirms "stay open," racing
        // against and sometimes overriding the scrim's explicit close.
        if (committed && abs(totalDx) > minDragPx) onDragEnd(velocityTracker.calculateVelocity().x)
    }
}

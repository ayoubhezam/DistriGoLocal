package com.distrigo.app

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.distrigo.app.ui.clients.ClientsScreen
import com.distrigo.app.ui.common.PlaceholderScreen
import com.distrigo.app.ui.dashboard.DashboardScreen
import com.distrigo.app.ui.more.MoreScreen
import com.distrigo.app.ui.navigation.Screen
import com.distrigo.app.ui.navigation.navEnterTransition
import com.distrigo.app.ui.navigation.navExitTransition
import com.distrigo.app.ui.navigation.navPopEnterTransition
import com.distrigo.app.ui.navigation.navPopExitTransition
import com.distrigo.app.ui.products.ProductsScreen
import com.distrigo.app.ui.purchases.PurchasesScreen
import com.distrigo.app.ui.suppliers.SuppliersScreen
import com.distrigo.app.ui.tournees.TourneesHubScreen
import kotlin.math.abs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.distrigo.app.data.geo.GeoRepository.init(this)
        setContent {
            MaterialTheme {
                BoxWithConstraints(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    var hideBottomBar by remember { mutableStateOf(false) }
                    val navController = rememberNavController()
                    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                    val isTabRoute = currentRoute == Screen.TabDashboard.route ||
                        currentRoute == Screen.TabVentes.route ||
                        currentRoute == Screen.TabProduits.route ||
                        currentRoute == Screen.TabAchats.route

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
                        bottomBar = {
                            if (isTabRoute && !hideBottomBar) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Transparent)
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(28.dp)),
                                    color         = Color.White,
                                    shadowElevation = 10.dp,
                                    tonalElevation  = 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        BottomNavItem(
                                            selected = currentRoute == Screen.TabDashboard.route,
                                            icon     = Icons.Default.Home,
                                            label    = "Dashboard",
                                            onClick  = { navigateToTab(Screen.TabDashboard.route) }
                                        )
                                        BottomNavItem(
                                            selected = currentRoute == Screen.TabVentes.route,
                                            icon     = Icons.Default.LocalShipping,
                                            label    = "Ventes",
                                            onClick  = { navigateToTab(Screen.TabVentes.route) }
                                        )
                                        BottomNavItem(
                                            selected = currentRoute == Screen.TabProduits.route,
                                            icon     = Icons.Default.ShoppingCart,
                                            label    = "Produits",
                                            onClick  = { navigateToTab(Screen.TabProduits.route) }
                                        )
                                        BottomNavItem(
                                            selected = currentRoute == Screen.TabAchats.route,
                                            icon     = Icons.Default.Receipt,
                                            label    = "Achats",
                                            onClick  = { navigateToTab(Screen.TabAchats.route) }
                                        )
                                        BottomNavItem(
                                            selected = false,
                                            icon     = Icons.Default.Menu,
                                            label    = "Plus",
                                            onClick  = { openDrawer() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        NavHost(
                            navController      = navController,
                            startDestination   = Screen.TabDashboard.route,
                            enterTransition    = navEnterTransition,
                            exitTransition     = navExitTransition,
                            popEnterTransition = navPopEnterTransition,
                            popExitTransition  = navPopExitTransition
                        ) {
                            // ── Bottom-tab destinations ──
                            composable(Screen.TabDashboard.route) {
                                com.distrigo.app.ui.navigation.DashboardNavHost()
                            }

                            composable(Screen.TabVentes.route) {
                                TourneesHubScreen(
                                    onFullScreenChange = { hideBottomBar = it },
                                    onOpenClientDetail = { clientId ->
                                        navController.navigate(Screen.PlusClients.createRoute(clientId))
                                    }
                                )
                            }

                            composable(Screen.TabProduits.route) {
                                com.distrigo.app.ui.navigation.ProduitsNavHost(onFullScreenChange = { hideBottomBar = it })
                            }

                            composable(Screen.TabAchats.route) {
                                com.distrigo.app.ui.navigation.AchatsNavHost(onFullScreenChange = { hideBottomBar = it })
                            }

                            // ── Plus menu items: real destinations, reached from the drawer overlay below ──
                            composable(
                                route     = Screen.PlusClients.route,
                                arguments = listOf(navArgument("clientId") { type = NavType.IntType; defaultValue = -1 })
                            ) { entry ->
                                val clientId = entry.arguments!!.getInt("clientId").takeIf { it != -1 }
                                com.distrigo.app.ui.navigation.ClientsNavHost(
                                    preSelectedClientId = clientId,
                                    onFullScreenChange  = { hideBottomBar = it },
                                    onBack              = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.PlusFournisseurs.route) {
                                com.distrigo.app.ui.navigation.SuppliersNavHost(onFullScreenChange = { hideBottomBar = it })
                            }

                            composable(Screen.PlusCharges.route) {
                                com.distrigo.app.ui.navigation.ChargesNavHost(onFullScreenChange = { hideBottomBar = it })
                            }

                            composable(Screen.PlusPertes.route) {
                                com.distrigo.app.ui.navigation.PertesNavHost(onFullScreenChange = { hideBottomBar = it })
                            }

                            composable(Screen.PlusInventaire.route) {
                                com.distrigo.app.ui.navigation.InventoryNavHost(
                                    onBack              = { navController.popBackStack() },
                                    onFullScreenChange  = { hideBottomBar = it }
                                )
                            }

                            composable(Screen.PlusRapports.route) {
                                PlaceholderScreen(
                                    title  = "Rapports",
                                    icon   = Icons.Default.BarChart,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.PlusParametres.route) {
                                com.distrigo.app.ui.settings.ParametresScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
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
        }
    }

        // Gesture-nav devices reserve roughly this same band on the left screen edge for the
        // system's own back gesture, which otherwise swallows our edge-swipe touches before
        // Compose ever sees them. Excluding it hands that band back to the app.
        reservePlusDrawerEdgeFromSystemGestures()
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

@Composable
private fun BottomNavItem(
    selected : Boolean,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    label    : String,
    onClick  : () -> Unit
) {
    val selectedColor   = Color(0xFF2196F3)
    val unselectedColor = Color(0xFFB0B3B8)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onClick() }
            .padding(horizontal = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint     = if (selected) selectedColor else unselectedColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize   = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (selected) selectedColor else unselectedColor
        )
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

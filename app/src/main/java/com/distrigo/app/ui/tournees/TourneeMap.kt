package com.distrigo.app.ui.tournees

import android.content.ActivityNotFoundException
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import androidx.compose.ui.graphics.toArgb
import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.TourneeClientInfo
import com.distrigo.app.data.model.locationLabel
import com.distrigo.app.data.repository.VisitState
import com.distrigo.app.ui.common.EntityAvatar
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.navigation.DrillTarget
import com.distrigo.app.ui.navigation.LocalDrillDown
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── What the map shows ──

/** A client of a tournée on its map: who, where (when known) and how their visit went. */
data class TourneeMapClient(
    val id: Int,
    val name: String,
    /** Where they are in words: quartier, secteur, commune, or the address. */
    val place: String?,
    val imageUri: String?,
    val latitude: Double?,
    val longitude: Double?,
    val state: VisitState,
) {
    /** A position the map can pin: both coordinates, in range, and not the 0,0 an empty field once saved. */
    val located: Boolean
        get() = latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)
}

/**
 * A tournée's clients as its map shows them, with the Dashboard's rule for their visit: bought on the tournée (with a
 * sale), else marked « visité » (without), else still to visit. Those who bought on it without being on its list come
 * after, with a sale — as the card counts them.
 */
fun tourneeMapClients(onList: List<TourneeClientInfo>, offListBuyers: List<Client>, buyers: Set<Int>): List<TourneeMapClient> {
    fun place(c: Client) = listOfNotNull(c.quartier_name, c.secteur_name, c.commune_name).filter { it.isNotBlank() }
        .joinToString(" · ").ifBlank { c.address?.takeIf { it.isNotBlank() } }
    fun of(c: Client, state: VisitState) =
        TourneeMapClient(c.id, c.name, place(c), c.image_uri, c.latitude, c.longitude, state)
    return onList.map { info ->
        of(info.client, when {
            info.client.id in buyers -> VisitState.WITH_SALE
            info.status == "visite" -> VisitState.WITHOUT_SALE
            else -> VisitState.PENDING
        })
    } + offListBuyers.filter { b -> onList.none { it.client.id == b.id } }.map { of(it, VisitState.WITH_SALE) }
}

/** How the map names and colours a visit: its chip, its pill on the client's sheet. */
internal data class VisitStyle(val chip: String, val pill: String, val color: Color, val pillBg: Color, val pillText: Color)

internal fun styleOf(state: VisitState) = when (state) {
    VisitState.WITH_SALE -> VisitStyle("Avec achat", "Visité · achat", DsColors.Success, DsColors.SuccessLight, DsColors.SuccessDark)
    VisitState.WITHOUT_SALE -> VisitStyle("Sans achat", "Visité · sans achat", DsColors.Warning, DsColors.WarningLight, DsColors.Warning)
    // Grey, as everywhere in the app a client still to visit is (owner's choice, not the prototype's red)
    VisitState.PENDING -> VisitStyle("À visiter", "À visiter", DsColors.TextTertiary, DsColors.SurfaceSunken, DsColors.TextSecondary)
}

private val StateOrder = listOf(VisitState.WITH_SALE, VisitState.WITHOUT_SALE, VisitState.PENDING)

// ── Loading it ──

data class TourneeMapData(val label: String, val clients: List<TourneeMapClient>)

@HiltViewModel
class TourneeMapViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val products: ProductRepository,
) : ViewModel() {
    private val tourneeId: Int = savedState.get<Int>("tourneeId") ?: -1
    private val _data = MutableStateFlow<TourneeMapData?>(null)
    val data: StateFlow<TourneeMapData?> = _data

    init {
        viewModelScope.launch {
            val tournee = runCatching { products.getTournee(tourneeId) }.getOrNull()
            val onList = products.getTourneeClientsWithDetails(tourneeId)
            val buyers = products.clientIdsWithSaleOnTournee(tourneeId)
            val listed = onList.map { it.client.id }.toSet()
            val offList = (buyers - listed).mapNotNull { products.getClientById(it) }
            _data.value = TourneeMapData(tournee?.locationLabel() ?: "", tourneeMapClients(onList, offList, buyers))
        }
    }
}

// ── The screen ──

/**
 * « Carte de la tournée », from the owner's prototype: a pin per client where their position is known, coloured by
 * their visit; chips that show or hide each kind, with their counts; a tap on a pin brings up the client and
 * « Naviguer »; the clients with no position are listed apart, each opening their page to set one.
 *
 * The map is Google's and needs a connection for its tiles; with none, the pins still show, on a blank map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourneeMapScreen(onBack: () -> Unit, viewModel: TourneeMapViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsState()
    val context = LocalContext.current
    val drill = LocalDrillDown.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val clients = data?.clients.orEmpty()
    val located = clients.filter { it.located }
    val unplaced = clients.filterNot { it.located }

    var shown by rememberSaveable { mutableStateOf(StateOrder.map { it.name }.toSet()) }
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showUnplaced by remember { mutableStateOf(false) }
    var mapLoaded by remember { mutableStateOf(false) }
    // Satellite with the street and place names over it (Google's hybrid), as the client's location picker
    var satellite by rememberSaveable { mutableStateOf(false) }
    val selected = located.firstOrNull { it.id == selectedId && it.state.name in shown }
    val pins = remember(data, shown) { located.filter { it.state.name in shown } }

    // Algeria's north, until the clients set it
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(36.3, 3.5), 6f) }
    fun fitAll(animate: Boolean) {
        if (located.isEmpty()) return
        val update = if (located.size == 1) {
            CameraUpdateFactory.newLatLngZoom(LatLng(located[0].latitude!!, located[0].longitude!!), 15f)
        } else {
            val bounds = LatLngBounds.builder().apply { located.forEach { include(LatLng(it.latitude!!, it.longitude!!)) } }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, with(density) { 72.dp.roundToPx() })
        }
        scope.launch { if (animate) camera.animate(update, 600) else camera.move(update) }
    }
    // Opened on its clients: set straight on the camera, which takes it whether or not the map is up yet (waiting
    // for the map's « loaded » left it on the whole country). One client, centred close; several, centred on them,
    // then fitted once the map has its size, which their bounds need.
    LaunchedEffect(data) {
        if (located.isEmpty()) return@LaunchedEffect
        val lat = located.sumOf { it.latitude!! } / located.size
        val lng = located.sumOf { it.longitude!! } / located.size
        camera.position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), if (located.size == 1) 15f else 12f)
    }
    LaunchedEffect(mapLoaded, data) { if (mapLoaded && located.size > 1) fitAll(animate = false) }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title    = "Carte de la tournée",
            subtitle = data?.label?.takeIf { it.isNotBlank() },
            leading  = DsTopBarLeading.Back(onBack)
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (data != null) GoogleMap(
                modifier            = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                // No 3D buildings nor indoor floor plans: what costs most while zooming and rotating, and nothing a
                // tournée needs
                properties          = MapProperties(
                    mapType = if (satellite) MapType.HYBRID else MapType.NORMAL,
                    isBuildingEnabled = false,
                    isIndoorEnabled = false,
                ),
                uiSettings          = MapUiSettings(
                    zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false,
                    indoorLevelPickerEnabled = false,
                ),
                onMapLoaded         = { mapLoaded = true },
                onMapClick          = { selectedId = null },
            ) {
                // The six pins (three visits, plain or picked) drawn once, here where the map is up, and shared by
                // every marker. A composable per marker was drawn to a bitmap of its own, again on every change.
                val icons = remember(density) { pinIcons(density.density) }
                pins.forEach { c ->
                    val isSelected = c.id == selectedId
                    Marker(
                        state   = remember(c.id) { MarkerState(LatLng(c.latitude!!, c.longitude!!)) },
                        icon    = icons.getValue(c.state to isSelected),
                        title   = c.name,
                        zIndex  = if (isSelected) 1f else 0f,
                        onClick = {
                            selectedId = c.id
                            scope.launch { camera.animate(CameraUpdateFactory.newLatLng(it.position), 400) }
                            true
                        },
                    )
                }
            }

            Column(Modifier.fillMaxWidth().padding(DsSpacing.md), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                // The pins' legend, each kind shown or hidden with its count
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StateOrder.forEach { state ->
                        val on = state.name in shown
                        FilterChipPill(
                            label   = styleOf(state).chip,
                            count   = clients.count { it.state == state },
                            color   = styleOf(state).color,
                            on      = on,
                            modifier = Modifier.weight(1f),
                            onClick = { shown = if (on) shown - state.name else shown + state.name },
                        )
                    }
                }
                if (unplaced.isNotEmpty()) {
                    Row(
                        Modifier.clip(DsShapes.pill).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.pill)
                            .clickable(role = Role.Button) { showUnplaced = true }
                            .padding(horizontal = DsSpacing.md, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOff, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (unplaced.size == 1) "1 client sans position" else "${unplaced.size} clients sans position",
                            fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary
                        )
                    }
                }
            }

            // Zoom and « recentrer »
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = DsSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                MapControl(Icons.Default.Layers, if (satellite) "Vue standard" else "Vue satellite", active = satellite) { satellite = !satellite }
                MapControl(Icons.Default.Add, "Zoom avant") { scope.launch { camera.animate(CameraUpdateFactory.zoomIn(), 300) } }
                MapControl(Icons.Default.Remove, "Zoom arrière") { scope.launch { camera.animate(CameraUpdateFactory.zoomOut(), 300) } }
                MapControl(Icons.Default.CenterFocusStrong, "Recentrer sur la tournée") { selectedId = null; fitAll(animate = true) }
            }

            // What to do, until a client is picked
            if (selected == null && data != null) {
                val hint = when {
                    located.isEmpty() -> "Aucun client de cette tournée n'a de position"
                    located.none { it.state.name in shown } -> "Aucun marqueur affiché. Touchez une pastille."
                    else -> "Touchez un marqueur pour voir le client"
                }
                Text(
                    hint,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = DsSpacing.lg)
                        .clip(DsShapes.pill).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.pill)
                        .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
                    fontSize = DsTextSize.caption, color = DsColors.TextSecondary
                )
            }

            // The picked client
            PickedClient(
                selected = selected,
                modifier = Modifier.align(Alignment.BottomCenter),
                onOpen   = { c -> drill(DrillTarget.Client(c.id)) },
                onNavigate = { c ->
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${c.latitude},${c.longitude}"))
                                .apply { setPackage("com.google.android.apps.maps") }
                        )
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "Google Maps n'est pas installé sur cet appareil", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }

    if (showUnplaced) {
        ModalBottomSheet(onDismissRequest = { showUnplaced = false }, containerColor = DsColors.Surface) {
            Text(
                "Clients sans position",
                modifier = Modifier.padding(horizontal = DsSpacing.lg),
                fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary
            )
            Text(
                "Ouvrez un client pour lui donner une position : il apparaîtra sur la carte.",
                modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary
            )
            LazyColumn(Modifier.padding(bottom = DsSpacing.xl)) {
                items(unplaced, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { showUnplaced = false; drill(DrillTarget.Client(c.id)) }
                            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EntityAvatar(name = c.name, imageUri = c.imageUri, size = 36.dp)
                        Spacer(Modifier.width(DsSpacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            c.place?.let { Text(it, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        StatePill(c.state)
                    }
                }
            }
        }
    }
}

/** [selected] sliding up over the map, and away when none is; the last one stays drawn while it slides out. */
@Composable
private fun PickedClient(
    selected: TourneeMapClient?, modifier: Modifier,
    onOpen: (TourneeMapClient) -> Unit, onNavigate: (TourneeMapClient) -> Unit,
) {
    var last by remember { mutableStateOf<TourneeMapClient?>(null) }
    if (selected != null) last = selected
    AnimatedVisibility(
        visible  = selected != null,
        modifier = modifier,
        enter    = slideInVertically { it } + fadeIn(),
        exit     = slideOutVertically { it } + fadeOut(),
    ) {
        last?.let { c -> ClientSheet(client = c, onOpen = { onOpen(c) }, onNavigate = { onNavigate(c) }) }
    }
}

/**
 * The pins, one per visit and per picked or not: a drop in the visit's colour, white-edged, with a check (bought), a
 * dash (no sale) or a dot (to visit); picked, a quarter larger. Drawn once as bitmaps, [density] being the screen's.
 */
internal fun pinIcons(density: Float): Map<Pair<VisitState, Boolean>, BitmapDescriptor> =
    StateOrder.flatMap { state -> listOf(false, true).map { selected -> (state to selected) } }
        .associateWith { (state, selected) -> BitmapDescriptorFactory.fromBitmap(pinBitmap(state, selected, density)) }

private fun pinBitmap(state: VisitState, selected: Boolean, density: Float): Bitmap {
    val scale = if (selected) 1.25f else 1f
    val w = (36 * density * scale).toInt()
    val h = (46 * density * scale).toInt()
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val edge = w * 0.07f
    val r = w * 0.42f - edge / 2
    val cx = w / 2f
    val cy = r + edge
    val tip = h - edge
    val drop = android.graphics.Path().apply {
        moveTo(cx, tip)
        cubicTo(cx, tip, cx - r, cy + r * 0.85f, cx - r, cy)
        arcTo(android.graphics.RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f, false)
        cubicTo(cx + r, cy + r * 0.85f, cx, tip, cx, tip)
        close()
    }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    canvas.drawPath(drop, paint.apply { style = android.graphics.Paint.Style.FILL; color = styleOf(state).color.toArgb() })
    canvas.drawPath(drop, paint.apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = edge; color = android.graphics.Color.WHITE })
    paint.apply { strokeWidth = w * 0.09f; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND }
    when (state) {
        VisitState.WITH_SALE -> canvas.drawPath(android.graphics.Path().apply {
            moveTo(cx - r * 0.42f, cy + r * 0.02f); lineTo(cx - r * 0.1f, cy + r * 0.32f); lineTo(cx + r * 0.45f, cy - r * 0.3f)
        }, paint)
        VisitState.WITHOUT_SALE -> canvas.drawLine(cx - r * 0.42f, cy, cx + r * 0.42f, cy, paint)
        VisitState.PENDING -> canvas.drawCircle(cx, cy, r * 0.32f, paint.apply { style = android.graphics.Paint.Style.FILL })
    }
    return bitmap
}

/** A chip of the legend: the kind's dot, its name and count; off, it greys and strikes through. */
@Composable
private fun FilterChipPill(label: String, count: Int, color: Color, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(DsShapes.pill)
            .background(if (on) DsColors.Surface else DsColors.SurfaceMuted)
            .border(1.dp, DsColors.Border, DsShapes.pill)
            .clickable(role = Role.Switch, onClick = onClick)
            .semantics { contentDescription = "$label, $count"; stateDescription = if (on) "affichés" else "masqués" }
            .padding(horizontal = DsSpacing.sm, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(9.dp).clip(DsShapes.pill).background(if (on) color else Color.Transparent)
                .border(2.dp, color, DsShapes.pill)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold,
            color = if (on) DsColors.TextPrimary else DsColors.TextSecondary,
            textDecoration = if (on) null else TextDecoration.LineThrough,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(4.dp))
        Text("$count", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.TextSecondary)
    }
}

/** A button beside the map; [active] (the satellite view on) fills it navy. */
@Composable
private fun MapControl(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(DsShapes.small).background(if (active) DsColors.PrimaryDark else DsColors.Surface)
            .border(1.dp, if (active) DsColors.PrimaryDark else DsColors.Border, DsShapes.small)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = if (active) Color.White else DsColors.PrimaryDark, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatePill(state: VisitState) {
    val style = styleOf(state)
    Text(
        style.pill,
        modifier = Modifier.clip(DsShapes.pill).background(style.pillBg).padding(horizontal = 10.dp, vertical = 4.dp),
        fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = style.pillText, maxLines = 1
    )
}

/** The picked client over the map: who, where, how the visit went, and « Naviguer »; a tap on them opens their page. */
@Composable
private fun ClientSheet(client: TourneeMapClient, onOpen: () -> Unit, onNavigate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(DsSpacing.md)
            .clip(DsShapes.large).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.md)
    ) {
        Row(
            Modifier.fillMaxWidth().clip(DsShapes.medium).clickable(role = Role.Button, onClickLabel = "Ouvrir la fiche", onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EntityAvatar(name = client.name, imageUri = client.imageUri, size = 42.dp)
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(client.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                client.place?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(it, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.width(DsSpacing.sm))
            StatePill(client.state)
        }
        Spacer(Modifier.height(DsSpacing.md))
        Button(
            onClick  = onNavigate,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
        ) {
            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text("Naviguer", fontWeight = FontWeight.Bold)
        }
    }
}

// ── The list's mini map (phase 3) ──

/** A pin of a tournée's mini map: where, and how the visit went. */
data class TourneePin(val latitude: Double, val longitude: Double, val state: VisitState)

/**
 * The rows of [com.distrigo.app.data.local.dao.TourneeDao.getTourneePins] per tournée, read with the map's rule: bought,
 * else marked « visité », else to visit. A position at 0,0 or out of range (a field once saved empty) is left out.
 */
fun tourneePins(rows: List<com.distrigo.app.data.local.dao.TourneePinRow>): Map<Int, List<TourneePin>> =
    rows.filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 && !(it.latitude == 0.0 && it.longitude == 0.0) }
        .groupBy({ it.tournee_id }) { row ->
            TourneePin(row.latitude, row.longitude, when {
                row.bought -> VisitState.WITH_SALE
                row.status == "visite" -> VisitState.WITHOUT_SALE
                else -> VisitState.PENDING
            })
        }

/**
 * The area a mini map fits: its clients, widened north for a pin rising above its point and a little the other ways. Lite mode leaves the map's padding out of a fit, so the room is
 * made in the area itself.
 */
internal fun miniMapBounds(pins: List<TourneePin>): LatLngBounds {
    val south = pins.minOf { it.latitude }; val north = pins.maxOf { it.latitude }
    val west = pins.minOf { it.longitude }; val east = pins.maxOf { it.longitude }
    val lat = maxOf(north - south, 0.002); val lng = maxOf(east - west, 0.002)
    return LatLngBounds(LatLng(south - lat * 0.2, west - lng * 0.15), LatLng(north + lat * 0.45, east + lng * 0.15))
}

/**
 * A tournée's clients on a small map in its card, from the owner's prototype: Google's lite mode, a still picture of
 * the map with its pins, light in a scrolling list. A tap anywhere on it opens « Carte de la tournée ».
 */
@Composable
fun TourneeMiniMap(pins: List<TourneePin>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(pins.sumOf { it.latitude } / pins.size, pins.sumOf { it.longitude } / pins.size),
            if (pins.size == 1) 14f else 12f
        )
    }
    var loaded by remember { mutableStateOf(false) }
    // Several clients: fitted once the map has its size, which their bounds need
    LaunchedEffect(loaded, pins) {
        if (loaded && pins.size > 1) {
            runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(miniMapBounds(pins), with(density) { 8.dp.roundToPx() })) }
        }
    }
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(124.dp)
                .clip(DsShapes.medium)
                .border(1.dp, DsColors.Border, DsShapes.medium)
                .semantics { contentDescription = "Ouvrir la carte de la tournée" }
        ) {
            GoogleMap(
                modifier                = Modifier.fillMaxSize(),
                cameraPositionState     = camera,
                googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
                properties              = MapProperties(isBuildingEnabled = false, isIndoorEnabled = false),
                uiSettings              = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false),
                onMapLoaded             = { loaded = true },
                // In lite mode a tap would open Google Maps; it opens the tournée's own map instead
                onMapClick              = { onOpen() },
            ) {
                val icons = remember(density) { pinIcons(density.density * 0.7f) }
                pins.forEach { pin ->
                    Marker(
                        state   = remember(pin) { MarkerState(LatLng(pin.latitude, pin.longitude)) },
                        icon    = icons.getValue(pin.state to false),
                        onClick = { onOpen(); true },
                    )
                }
            }
            // « Plein écran »
            Box(
                Modifier.align(Alignment.BottomEnd).padding(DsSpacing.sm).size(30.dp)
                    .clip(DsShapes.pill).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.pill)
                    .clickable(role = Role.Button, onClickLabel = "Ouvrir la carte", onClick = onOpen),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.OpenInFull, contentDescription = null, tint = DsColors.PrimaryDark, modifier = Modifier.size(15.dp))
            }
        }
        // The colours' legend, under the map: over it, it hid the pins and Google's logo
        Row(
            Modifier.padding(top = 6.dp, start = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(VisitState.WITH_SALE to "Achat", VisitState.WITHOUT_SALE to "Sans achat", VisitState.PENDING to "À visiter").forEach { (state, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(DsShapes.pill).background(styleOf(state).color))
                    Spacer(Modifier.width(4.dp))
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

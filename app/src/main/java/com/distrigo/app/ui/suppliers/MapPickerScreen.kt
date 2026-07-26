package com.distrigo.app.ui.suppliers

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.distrigo.app.R
import com.distrigo.app.ui.products.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker as OsmMarker
import com.distrigo.app.ui.common.OfflineMapBadge
import com.distrigo.app.ui.common.rememberIsOnline
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import androidx.compose.material.icons.filled.Layers
import com.google.maps.android.compose.MapType
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsColors
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
@Composable
fun MapPickerScreen(
    initialLat : Double = 36.1901,
    initialLng : Double = 5.4133,
    onLocationPicked : (Double, Double) -> Unit,
    onBack           : () -> Unit
) {
    val context = LocalContext.current
    val isOnline by rememberIsOnline()

    var selectedLat by remember { mutableStateOf(initialLat) }
    var selectedLng by remember { mutableStateOf(initialLng) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(initialLat, initialLng), 15f)
    }
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    val markerState = rememberMarkerState(position = LatLng(initialLat, initialLng))

    // يُبقي دبوس Google Maps متزامنًا مع النقطة المختارة عند النقر على الخريطة
    LaunchedEffect(selectedLat, selectedLng) {
        markerState.position = LatLng(selectedLat, selectedLng)
    }

    Configuration.getInstance().userAgentValue = context.packageName

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Map ──
        if (isOnline) {
            GoogleMap(
                modifier            = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false, mapType = mapType),
                uiSettings          = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
                onMapClick          = { latLng ->
                    selectedLat = latLng.latitude
                    selectedLng = latLng.longitude
                }
            ) {
                Marker(state = markerState, title = "Fournisseur")
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        controller.setCenter(GeoPoint(selectedLat, selectedLng))
                        mapView = this

                        // Marker
                        val marker = OsmMarker(this)
                        marker.position = GeoPoint(selectedLat, selectedLng)
                        marker.setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                        marker.title = "Fournisseur"
                        overlays.add(marker)

                        // Click listener
                        overlays.add(object : org.osmdroid.views.overlay.Overlay() {
                            override fun onSingleTapConfirmed(
                                e: android.view.MotionEvent,
                                mapView: MapView
                            ): Boolean {
                                val projection = mapView.projection
                                val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                                selectedLat = geoPoint.latitude
                                selectedLng = geoPoint.longitude
                                overlays.removeAll { it is OsmMarker }
                                val newMarker = OsmMarker(mapView)
                                newMarker.position = geoPoint
                                newMarker.setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                                newMarker.title = "Fournisseur"
                                overlays.add(newMarker)
                                invalidate()
                                return true
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick  = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text("Choisir l'emplacement", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        if (isOnline) {
            IconButton(
                onClick  = { mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(DsSpacing.md)
                    .clip(RoundedCornerShape(50))
                    .background(DsColors.Surface)
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = if (mapType == MapType.NORMAL) "Vue satellite" else "Vue standard",
                    tint = DsColors.Primary
                )
            }
        }



        // ── Confirm Button ──
        Button(
            onClick  = { onLocationPicked(selectedLat, selectedLng) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            shape  = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Confirmer l'emplacement", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        // ── Coordinates Card ──
        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 80.dp)
                .align(Alignment.BottomCenter),
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
        ) {
            Text(
                text     = "${"%.6f".format(selectedLat)}, ${"%.6f".format(selectedLng)}",
                fontSize = 12.sp,
                color    = TextMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
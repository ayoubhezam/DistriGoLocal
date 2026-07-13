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
import org.osmdroid.views.overlay.Marker

@Composable
fun MapPickerScreen(
    initialLat : Double = 36.1901,
    initialLng : Double = 5.4133,
    onLocationPicked : (Double, Double) -> Unit,
    onBack           : () -> Unit
) {
    val context = LocalContext.current
    var selectedPoint by remember { mutableStateOf(GeoPoint(initialLat, initialLng)) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    Configuration.getInstance().userAgentValue = context.packageName

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Map ──
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    controller.setCenter(selectedPoint)
                    mapView = this

                    // Marker
                    val marker = Marker(this)
                    marker.position = selectedPoint
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
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
                            selectedPoint = geoPoint
                            overlays.removeAll { it is Marker }
                            val newMarker = Marker(mapView)
                            newMarker.position = geoPoint
                            newMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
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
                modifier = Modifier
                    .size(40.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Retour",
                    tint = TextPrimary
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Choisir l'emplacement",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
        }

        // ── Confirm Button ──
        Button(
            onClick  = {
                onLocationPicked(selectedPoint.latitude, selectedPoint.longitude)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            shape  = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Confirmer l'emplacement",
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
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
                text     = "${"%.6f".format(selectedPoint.latitude)}, ${"%.6f".format(selectedPoint.longitude)}",
                fontSize = 12.sp,
                color    = TextMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
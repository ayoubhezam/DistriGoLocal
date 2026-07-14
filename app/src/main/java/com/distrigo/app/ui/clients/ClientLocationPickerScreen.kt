package com.distrigo.app.ui.clients

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Composable
fun ClientLocationPickerScreen(
    initialLatitude  : Double?,
    initialLongitude : Double?,
    onBack           : () -> Unit,
    onLocationSelected: (Double, Double) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val defaultLat = initialLatitude ?: 36.7538
    val defaultLng = initialLongitude ?: 3.0588

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    fun centerOnHighAccuracyGps() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            locationError = "Permission de localisation refusée."
            return
        }
        isLocating = true
        locationError = ""
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationTokenSource = CancellationTokenSource()
        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    isLocating = false
                    if (location != null) {
                        mapViewRef?.controller?.animateTo(GeoPoint(location.latitude, location.longitude))
                    } else {
                        locationError = "Position indisponible. Activez le GPS."
                    }
                }
                .addOnFailureListener {
                    isLocating = false
                    locationError = "Impossible d'obtenir la position."
                }
        } catch (e: SecurityException) {
            isLocating = false
            locationError = "Permission de localisation refusée."
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) centerOnHighAccuracyGps() else locationError = "Permission refusée." }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.xs))
            Text("Position du client", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(17.0)
                        controller.setCenter(GeoPoint(defaultLat, defaultLng))
                        mapViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Icon(
                Icons.Default.MyLocation,
                contentDescription = null,
                tint     = DsColors.Danger,
                modifier = Modifier.align(Alignment.Center).size(36.dp)
            )

            Button(
                onClick  = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) centerOnHighAccuracyGps()
                    else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                enabled  = !isLocating,
                modifier = Modifier.align(Alignment.BottomEnd).padding(DsSpacing.lg),
                shape    = DsShapes.pill,
                colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Surface, contentColor = DsColors.TextPrimary)
            ) {
                if (isLocating) {
                    CircularProgressIndicator(color = DsColors.Primary, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("GPS précis", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (locationError.isNotEmpty()) {
            Text(
                locationError,
                fontSize = DsTextSize.caption,
                color    = DsColors.Danger,
                modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs)
            )
        }

        Text(
            "Faites glisser la carte pour placer le repère exactement sur la porte du commerce.",
            fontSize = DsTextSize.caption,
            color    = DsColors.TextSecondary,
            modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm)
        )

        Button(
            onClick = {
                mapViewRef?.mapCenter?.let { center ->
                    onLocationSelected(center.latitude, center.longitude)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Enregistrer cette position", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}
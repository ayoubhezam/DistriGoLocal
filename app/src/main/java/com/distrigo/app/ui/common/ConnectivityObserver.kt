package com.distrigo.app.ui.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsTextSize

/**
 * يراقب توفر اتصال إنترنت حقيقي (مُتحقق منه من طرف النظام، وليس مجرد اتصال
 * بشبكة بدون إنترنت فعلي) بشكل مستمر طوال بقاء الشاشة على الواجهة.
 * يُستخدم لاختيار محرك الخريطة: Google Maps عند توفر الاتصال،
 * أو osmdroid كخيار احتياطي دون اتصال.
 */
@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val isOnline = remember { mutableStateOf(context.hasValidatedInternet()) }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService<ConnectivityManager>()

        if (connectivityManager == null) {
            onDispose { }
        } else {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    isOnline.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }
                override fun onLost(network: Network) {
                    isOnline.value = context.hasValidatedInternet()
                }
            }

            connectivityManager.registerNetworkCallback(request, callback)
            onDispose { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }

    return isOnline
}

private fun Context.hasValidatedInternet(): Boolean {
    val cm = getSystemService<ConnectivityManager>() ?: return false
    val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/** شارة صغيرة تُعلم المستخدم أن الخريطة تعمل حاليًا بدون اتصال (osmdroid). */
@Composable
fun OfflineMapBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(DsColors.WarningLight)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CloudOff, contentDescription = null, tint = DsColors.Warning, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text("Mode hors ligne", fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Warning)
    }
}
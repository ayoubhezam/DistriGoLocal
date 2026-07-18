package com.distrigo.app.ui.pertes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object PerteIconMapper {
    fun iconFor(key: String): ImageVector = when (key) {
        "broken_image"   -> Icons.Default.BrokenImage   // Casse
        "event_busy"     -> Icons.Default.EventBusy     // Péremption
        "report"         -> Icons.Default.Report        // Vol
        "local_shipping" -> Icons.Default.LocalShipping  // Perte de transport
        "card_giftcard"  -> Icons.Default.CardGiftcard   // Don
        "category"       -> Icons.Default.Category       // Autre
        else             -> Icons.Default.Inventory2
    }

    fun colorFor(hex: String): Color = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFFF04438)
    }
}
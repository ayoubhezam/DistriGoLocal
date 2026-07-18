package com.distrigo.app.ui.charges

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object ChargeIconMapper {
    fun iconFor(key: String): ImageVector = when (key) {
        "directions_car"     -> Icons.Default.DirectionsCar
        "local_gas_station"  -> Icons.Default.LocalGasStation
        "trip_origin"        -> Icons.Default.TripOrigin
        "build"               -> Icons.Default.Build
        "opacity"             -> Icons.Default.Opacity        // Vidange
        "shield"              -> Icons.Default.Shield
        "people"              -> Icons.Default.People
        "payments"            -> Icons.Default.Payments
        "card_giftcard"       -> Icons.Default.CardGiftcard
        "school"              -> Icons.Default.School
        "apartment"           -> Icons.Default.Apartment
        "home"                -> Icons.Default.Home
        "bolt"                -> Icons.Default.Bolt
        "wifi"                -> Icons.Default.Wifi
        "inventory"           -> Icons.Default.Inventory
        "local_shipping"      -> Icons.Default.LocalShipping
        "toll"                -> Icons.Default.Toll
        "local_parking"       -> Icons.Default.LocalParking
        "inventory_2"         -> Icons.Default.Inventory2
        "shopping_cart"       -> Icons.Default.ShoppingCart
        "cleaning_services"   -> Icons.Default.CleaningServices
        "category"            -> Icons.Default.Category
        "more_horiz"          -> Icons.Default.MoreHoriz
        "warning"             -> Icons.Default.Warning
        else                  -> Icons.Default.Receipt
    }

    fun colorFor(hex: String): Color = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF5B6EF5)
    }
}
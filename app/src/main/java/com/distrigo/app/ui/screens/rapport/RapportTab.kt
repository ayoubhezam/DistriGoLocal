// package com.distrigo.app.ui.screens.rapport

package com.distrigo.app.ui.screens.rapport

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * التبويبات الأربعة داخل شاشة "Rapport des tournées".
 * ترتيبها هنا يحدد ترتيب ظهورها في TabRow و HorizontalPager.
 */
enum class RapportTab(val label: String, val icon: ImageVector) {
    TABLEAU_DE_BORD("Tableau de bord", Icons.Default.Dashboard),
    VENTES("Ventes", Icons.Default.Receipt),
    CLIENTS("Clients", Icons.Default.Person),
    PRODUITS("Produits", Icons.Default.Inventory2)
}

/**
 * الفترة الزمنية المختارة، تُستخدم في تبويبَي Tableau de bord و Ventes فقط
 * (تبويبا Clients و Produits يعتمدان على اختيار كيان لا فترة).
 */
enum class ReportPeriod(val label: String) {
    AUJOURD_HUI("Aujourd'hui"),
    SEMAINE("Semaine"),
    MOIS("Mois"),
    PERSONNALISEE("Personnalisé")
}

/** معلومات اتجاه المقارنة بالفترة السابقة، تُستخدم في TrendPill */
data class TrendInfo(
    val percentage: Int,
    val isPositive: Boolean
)
package com.distrigo.app.ui.navigation

/**
 * نقطة مركزية واحدة لكل مسارات التطبيق — كل مرحلة هجرة قادمة تُضيف وجهاتها هنا فقط.
 * لا تُعدَّل وجهة قديمة عند إضافة جديدة؛ فقط إضافة.
 */
sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
}
package com.distrigo.app.ui.common

import com.distrigo.app.data.model.Quantity

/** A quantity as every screen, receipt and message shows it: "2", "0.5", "1.25". See [Quantity.format]. */
fun formatQty(v: Double): String = Quantity.format(v)

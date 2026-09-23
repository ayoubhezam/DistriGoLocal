package com.distrigo.app.ui.settings.print

import com.distrigo.app.ui.components.ReceiptData
import com.distrigo.app.ui.components.ReceiptLineItem

/**
 * The receipt the preview draws. Hardcoded on purpose — the preview answers "what will my paper look
 * like", which needs no sale to exist and must work on a phone whose database is empty.
 *
 * Chosen to exercise what actually breaks a layout rather than to look tidy:
 * - a product name longer than either paper's name column, so wrapping shows;
 * - **an Arabic name**, which is the whole reason the receipt is drawn rather than typed: it has to
 *   come out joined, in contextual forms, running right to left, with its Latin figures still in
 *   reading order beside it. Nothing else on this receipt can go wrong so invisibly — unshaped
 *   Arabic still looks like Arabic to someone who does not read it;
 * - a name with accents, so a wrong code page is visible at a glance;
 * - a quantity with decimals beside whole ones;
 * - a part payment, so the Payé / Reste rows and the signature line appear;
 * - a five-figure total, which is where a too-narrow amount column would start cutting digits.
 *
 * [businessName] and [businessPhone] are replaced with the real ones before rendering, so the preview
 * shows the user their own header while the items stay fake.
 */
internal val SAMPLE_RECEIPT = ReceiptData(
    documentTitle   = "Vente N° 27",
    referenceNumber = "V-6DED-000027",
    partyLabel      = "Client",
    partyName       = "Ahmed Benali",
    dateLabel       = "vendredi 12/09/2026",
    timeLabel       = "14:32",
    items = listOf(
        ReceiptLineItem(
            name = "Lait Candia demi-écrémé 1L", quantity = 24.0, unitLabel = "pièce",
            unitPrice = 95.0, totalPrice = 2280.0, nbColis = 2.0, unitePerColis = 12,
        ),
        ReceiptLineItem(
            name = "Huile Elio 5L", quantity = 6.0, unitLabel = "carton",
            unitPrice = 1450.0, totalPrice = 8700.0, nbColis = 6.0,
        ),
        ReceiptLineItem(
            name = "Café Tchin-Tchin 250g", quantity = 12.5, unitLabel = "kg",
            unitPrice = 340.0, totalPrice = 4250.0,
        ),
        ReceiptLineItem(
            name = "بطاطا محلية طازجة", quantity = 30.0, unitLabel = "kg",
            unitPrice = 80.0, totalPrice = 2400.0,
        ),
    ),
    total         = 17630.0,
    paid          = 10000.0,
    note          = "Livraison prévue jeudi matin.",
    businessName  = "DISTRIGO",
    businessPhone = "0555 12 34 56",
    performedBy   = "Youcef",
    clientType    = "Détail",
    clientSecteur = "Alger-Centre",
)

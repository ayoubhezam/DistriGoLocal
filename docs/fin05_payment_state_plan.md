# FIN-05 — Paid, partial or unpaid, decided once

**Status:** D1–D4 approved as recommended by the owner on 2026-11-09; D3 confirmed by « test data only », so no v65
migration. The advance is « تسبيق » in Arabic everywhere (glossary row 175). Implemented the same day. A separate item from FIN-01, which covers only
the live « Montant payé » block of the three forms (Dépôt vente, Tournée vente, Achats). Finding: FIN-05 in
`docs/audit/2026-11-09-technical-and-logic-audit.md`.

Paths are relative to `app/src/main/java/com/distrigo/app/`.

## 1. The problem

Document totals are `Double` sums of quantity × price, written unrounded, and most screens decide « Payée »,
« Partielle » or « Impayée » by comparing them exactly with the amount paid.

- 3 × 150,15 is stored as 450.45000000000005. « Tout réglé » fills 450.45, so the saved sale shows « Reste 0,00 DA »
  in red instead of the PAYÉ stamp, and the « Partielle » filters list it.
- 2,675 × 3 is 8.024999999999999; « Tout réglé » fills 8.02 and leaves a real 0,005 DA. Since FIN-01 the cart's price
  field keeps two decimals, but older prices and 3-decimal quantities (carton, kg) still give line totals with more.
- Three rules coexist: exact (most lists and details), half-centime (Dashboard « Activité récente », the receipts'
  « Reste »), and per-screen variants for the edges. A sale with a total of 0 (free samples) is « Payée » on the
  Dashboard but « Impayée » in Ventes, in the client ledger and in the tournée.

Balances are not affected: `Amount.owing` / `settled` already read them to the half centime (2026-10-17).

## 2. Where the state is decided today

| Where | What it decides | Rule today |
|---|---|---|
| `data/local/dao/VenteDao.kt:165–167, 196–198` | Client ledger filter (count and page) | exact |
| `data/local/dao/PurchaseDao.kt:121–123, 152–154` | Supplier ledger filter (count and page) | exact |
| `data/local/paging/VenteListQuery.kt:71–73` | Ventes list « Filtres » | exact |
| `data/local/paging/PurchaseOrderListQuery.kt:96–98` | Achats list « Filtres » | exact |
| `data/local/dao/ReportDao.kt:325–326` | Dashboard « Activité récente » filter | half centime |
| `data/local/dao/ReportDao.kt:410, 427, 440` | Debt ageing: which documents still carry credit | exact (`total > montant_paye`) |
| `data/repository/DashboardReport.kt:62–66` | Dashboard sale badge | half centime; total 0 → Payée |
| `ui/ventes/VentesScreen.kt:1114–1118` | Ventes row ribbon | exact; total 0 → Impayée |
| `ui/ventes/VentesScreen.kt:957–985` | Sale detail: « Reste » or PAYÉ stamp | exact (`reste > 0`) |
| `ui/purchases/PurchasesScreen.kt:725–729` | Achats row ribbon | exact |
| `ui/purchases/PurchaseOrderDetailScreen.kt:364–392` | Bon detail: « Reste » or PAYÉ stamp | exact |
| `ui/clients/FactureRow.kt:48–53` | Client ledger row | exact; total 0 → Impayée |
| `ui/suppliers/AchatRow.kt:48–53` | Supplier ledger row | exact |
| `ui/tournees/TourneesScreen.kt:628–633` | Tournée sales filter (in memory) | exact |
| `ui/tournees/TourneesScreen.kt:1679–1687` | Tournée sale row | exact |
| `data/print/lang/ThermalLayout.kt:99–101`, `ui/components/ReceiptPdfGenerator.kt:178–180`, `ui/components/ShareOptionsSheet.kt:158–159` | Receipt « Reste » / « Payé » lines | « Reste » half centime; « Payé » shown on `balance != total` (exact) |

Where totals are written unrounded:

| Where | What |
|---|---|
| `data/ProductRepository.kt:1276, 1319, 1328` | `createVente`: total, movement `total_value`, line `total_price` |
| `data/ProductRepository.kt:1374, 1396, 1406` | `updateVente`: the same |
| `data/ProductRepository.kt:932, 961` | `createPurchaseOrder`: total, line `total_cost` |
| `data/ProductRepository.kt:1054, 1073` | `updatePurchaseOrder`: the same |
| `data/repository/RetourClientRepository.kt:133, 145`, `data/repository/RetourFournisseurRepository.kt:132, 145` | Return totals (FIN-02 and FIN-03 change these values too) |
| `data/ProductRepository.kt:1532–1561, 1904–1933` | Payments written as given (`PaymentDialog` reads them with `toDoubleOrNull`) |
| `ui/navigation/VenteFormNavGraph.kt:680`, `TourneeVenteFormNavGraph.kt:597`, `PurchaseFormNavGraph.kt:1085` | The forms' totals, shown on « Confirmer · X DA » |

## 3. The rule

One function, `PaymentState.of(total, paid)`, in `data/model/PaymentState.kt`, on top of `Amount`. With
`d = total − paid`:

| State | Condition | Same as |
|---|---|---|
| Payée | `d < 0.005` (settled, paid ahead, or nothing to pay) | `!Amount.owing(d)` |
| Impayée | `d ≥ 0.005` and `paid < 0.005` | |
| Partielle | `d ≥ 0.005` and `paid ≥ 0.005` | |

The three conditions cover every row exactly once. The SQL filters use the same half centime, written once as a
constant beside `Amount.HALF_CENTIME`, with a test that the two agree:

- PAYEE: `total - montant_paye < 0.005`
- IMPAYEE: `total - montant_paye >= 0.005 AND montant_paye < 0.005`
- PARTIELLE: `total - montant_paye >= 0.005 AND montant_paye >= 0.005`

Examples: 450.45000000000005 paid 450.45 → Payée. Total 0 → Payée (D2). 8.024999999999999 (3 × 2,675 saved before FIN-05) paid 8.02 → Payée; from FIN-05 that
line is saved as 8,03.
1 500,50 paid 150 050 → Payée, and an advance (D4).

## 4. Steps

**F5a — The rule, once.** Move `PaymentState` out of `DashboardReport.kt` into `data/model`, add `of(total, paid)`
and the three SQL fragments. JVM tests: a boundary table (the examples above, 0.004 / 0.005 either side, total 0,
paid above total).

**F5b — The SQL filters.** `VenteDao` (2 queries), `PurchaseDao` (2), `VenteListQuery`, `PurchaseOrderListQuery` and
`ReportDao.recentSales` use the fragments. The debt-ageing conditions (`ReportDao.kt:410, 427, 440`) become
`total - montant_paye >= 0.005`. Update `VenteListSqlTest` and `PurchaseOrderListSqlTest`; add boundary rows to an
instrumented test (extend `LiveLedgerTest` and `DashboardReportTest`) so the filters are checked on SQLite itself.

**F5c — The screens.** The nine Kotlin sites in §2 call `PaymentState.of`. The sale and bon details show « Reste »
only when it is owed, the PAYÉ stamp otherwise; the receipts' « Payé » line test uses `Amount.settled` instead of
`!=`. The tournée's in-memory filter uses the same function as its row ribbon.

**F5d — Write exact amounts.** Two helpers in `Amount`: a line total (`normalize(quantity × price)`) and a document
total, used by the repositories in §2 and by the three forms' totals, so « Confirmer · X DA » is what is saved.
Payments are normalized in `add/updateClientPayment` and `add/updateSupplierPayment`, and `PaymentDialog` reads its
field with `Amount.sanitizeInput` / `Amount.parse`, as the forms now do. D1 decides how lines and totals are rounded.

**F5e — Rows already saved (D3).** Recommended: leave them; the rule in §3 reads them correctly. The alternative is a
v65 migration that rounds stored totals and recomputes every balance. It would bump `version` / `updated_at` on most
documents through the change-tracking triggers, unless they are dropped for it (see CLAUDE.md, Database).

**F5f — Check on the phone, in French and Arabic.** A sale of 3 × 150,15 paid with « Tout réglé »: detail, Ventes
ribbon, « Partielle » / « Payée » filters, client ledger, receipt. A sale of total 0. A bon paid 0,004 short. The
tournée list with its payment filter.

## 5. Decisions for the owner

| # | Question | Recommended |
|---|---|---|
| D1 | Round each line to the centime and add them up, or round only the document total? | Each line: the lines a receipt prints add up to its total. |
| D2 | A document whose total is 0: « Payée » or « Impayée »? | « Payée », as the Dashboard already shows it. |
| D3 | Round the totals already saved (migration v65), or leave them? | Leave them: the rule tolerates them, and no document's `version` moves. |
| D4 | A saved document paid above its total: show « Avance : X DA » on its detail? | Yes, on the detail only; the list ribbon stays « Payée ». Needs one string (FR + AR). |

## 6. Not in this item

- REP-02: payments made later are not allocated to sales, so a client who settles a balance still has « Impayée »
  sales. A separate decision.
- FIN-02 and FIN-03 change how return totals are valued; they use the F5d helpers once those exist.
- Ordering: start after FIN-01 is committed. F5d changes the same three form files.

Size: about 15 files, no schema change unless D3 chooses the migration. Tests: one new JVM file, two updated, one
instrumented test extended.

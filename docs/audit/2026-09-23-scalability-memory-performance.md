# Scalability, Memory & Performance Audit — 2026-09-23

**What was read:** `main` at `6c3f6cd` — 382 Kotlin files (~67k lines), schema v54 with 43 tables, and every file under `data/print` and `ui/settings/print`. No code was changed.

**Baseline:** the C1–C9 findings of the Scalability Audit of 2026-09-08 (commit `e79cb7a`, schema v34). That report is not in the repo; it was recovered from the audit page saved in an earlier session's transcript, and each item was checked against the current code.

**Measured vs estimated:**
- **Measured:** the stock-ledger trigger cost. The exact v54 table definitions, indexes and trigger SQL were run on a laptop (SQLite, in memory). A mid-range phone is expected to be about 3–5× slower; that factor is an estimate.
- **Estimated:** the print-engine figures, worked out from the code and the paper profiles. They were not profiled on a device.

## Scorecard (2026-09-08 → now)

| Area | Then | Now | Why it moved |
|---|---|---|---|
| Database scalability | 2 | **6** | 81 indexes (was 2); totals, tournées and previews now computed in SQL. Stock triggers are the new ceiling. |
| Memory safety | 3 | **7** | No more base64 photos, no always-on table flows. Print preview and logo are the new spikes. |
| Main-thread discipline | 4 | **6** | 32 `withContext` sites (was 0). **Printing a receipt draws it on the main thread.** |
| Compose performance | 5 | **6.5** | Filters memoized and lists keyed. Whole-table subscriptions remain. |
| Architecture scalability | 4 | **5** | Paging still exists only on the two ledgers. `ProductRepository` grew to 1,916 lines. |

---

## 1. Are C1–C9 really fixed?

| # | Status | Gaps in the fix |
|---|---|---|
| **C1** Indexes | ✅ 81 indexes on 43 tables | (a) `WHERE (:p IS NULL OR col = :p)` stops SQLite using the index, because the query plan is chosen once, whatever value is passed. So `getVentesWithDetails(clientId)` (`VenteDao.kt:62`) and `StockMovementDao.filtered` scan the whole table. (b) The stock trigger's `SUM` queries use `stock_movements(product_id, created_at)` but still have to read every movement row; see **H1**. (c) Still 0 foreign keys. |
| **C2** Images in rows | ✅ | Columns now hold ~70-byte references. The four copied `*_image_uri` columns remain but only hold those references. `ReceiptRasterizer.logo` is the one new decode that isn't downscaled (**P4**). |
| **C3** Whole tables in flows | 🟡 | `Eagerly` is gone (0 hits). But there are **50 `collectAsState` subscriptions to the whole products/clients/suppliers tables across 23 files**. `observeProducts()` (`ProductRepository.kt:255`) groups barcodes and maps the whole catalogue **on Main** (no `flowOn`). Each sale writes `products.stock` through the trigger, so it re-emits the entire catalogue to every open screen that watches it. |
| **C4** Balance recalculation | ✅ | One SQL `UPDATE` per client or supplier, served by the indexes. **The same problem — cost growing with history inside the write lock — has moved to the stock triggers (H1).** |
| **C5** Tournées | ✅ | Done with one aggregate query (`getAllTourneeSummaries`). |
| **C6** Filter/sort | 🟡 Tier 1 only | Search is still in memory. 5 `LaunchedEffect(products)` still restart on every catalogue emission: `VenteFormNavGraph.kt:235,454`, `TourneeVenteFormNavGraph.kt:221,441`, `PurchaseFormNavGraph.kt:535`. `MouvementsScreen.kt:123-124` sums every movement in composition, without `remember`. |
| **C7** Main thread | ✅ for what it covered, ⚠️ one regression | Rapports removed, PDF on IO, geo file on IO. **The new print path draws the receipt on Main (P1).** |
| **C8** Client row decode | ✅ | — |
| **C9** Detail previews | ✅ | SQL totals, `LIMIT 4`, returns counted with `GROUP BY`. The "Voir tout" returns list still loads all of one party's returns, as decided. |

**High-risk items from the 2026-09-08 report that are still open:**
- The Achats and Chargements lists still make 1 + 2N queries: `getPurchaseOrders` at `ProductRepository.kt:789` loads every bon with all its lines. The Chargements lists are at `:1448` and `:1527`.
- `observeVenteIds()` and `observeOrderStatuses()` still read every sale or bon id.
- Mouvements has no `LIMIT`.
- Paging still exists only on the two ledgers.
- R8 is still off.

**The drafts badge comment is wrong about why the query can't be bounded.** `VenteDraftDao.kt:60` says a bounded query would stop the badge updating live. But Room re-runs a query when any table named in it changes, subqueries included. So `SELECT id FROM ventes WHERE id IN (SELECT source_vente_id FROM vente_drafts)` would still update live, and it only returns the ids of sales that have drafts.

---

## 2. The new print engine

**Leaks: none found.**
- Every row's bitmap is freed in a `finally` (`CanvasReceiptRenderer.kt:148`).
- The transports close their sockets in `finally`.
- The Bluetooth scanner unregisters its listener when the scan stops.
- The view models hold only the application context.
- Memory at any moment is capped by the tallest single row, as the design intended.

The problems are about which thread does the work, doing the work twice, and the size of the logo.

### P1 — High. Printing draws the receipt on the main thread

- `ReceiptPrinter.kt:64` calls `gate.transportFor(target).send(target.id, bytes(paper))`. `bytes(paper)` runs **before** `send` switches to the IO thread.
- The caller is `viewModelScope.launch` (`ReceiptPrintViewModel.kt:74`), which runs on Main.
- So on the UI thread it decodes the logo file (up to 1024², 4 MB), dithers it, builds ~4 text layouts per item and ~50 bitmaps.
- The preview does this off Main (`rasterize`, `Dispatchers.Default`). Printing doesn't. The "Impression…" spinner freezes while it runs.
- Fix before Phase 5: every new printer language will produce its bytes at this same line.

### P2 — Medium. Every print draws the receipt twice

- The sheet draws the rows for the preview, then `print()` draws them all again from the `ReceiptData`, including decoding and dithering the logo again.
- The settings screen draws a third time on every emission.
- Passing the preview's result to `print()` removes one full pass, including ~13 MB of logo work.

### P3 — Medium. The preview converts rows to images on Main, in composition, for every row

- In `ThermalReceiptPreview.kt:98-117`, each row calls `toImageBitmap()` inside `remember`, in a scrolling `Column`, so every row is built at once.
- Each 1-bit dot becomes a 32-bit pixel plus a 32-bit array entry: about **4.6 KB per dot row** at 80 mm, half kept and half garbage.

| Receipt | Dot rows | Images kept | Garbage | `isBlack` calls on Main |
|---|---|---|---|---|
| Sample receipt (4 items) | ~1,200 | 2.8 MB | 2.8 MB | 0.7 M |
| 30 items | ~2,900 | 6.7 MB | 6.7 MB | 1.7 M |
| 100 items | ~7,400 | 17 MB | 17 MB | 4.3 M |

- Fix: build the images in the view model on Default, use `ALPHA_8` (1 byte per dot, not 4), and show the rows in a `LazyColumn`.

### P4 — Medium. The logo is decoded at full size and has no height limit

- `BitmapFactory.decodeFile` runs with no downsampling (`ReceiptRasterizer.kt:37`), and the bitmap is never recycled. Then `fromBitmap` allocates a full-size pixel array and a full-size grey array.
- That's about **13 MB of short-lived memory per logo draw**, repeated for the preview, the print and the settings screen.
- The logo is always scaled to the full printable width, so a square logo takes **576 dots = 72 mm of paper and 41 KB**. A tall logo takes more.
- Fix: cache the logo raster per (file, paper width), decode it with downsampling, and cap its height (e.g. ≤ 20 mm).

### P5 — Low. Per-row memory churn

- Each row allocates a new bitmap and a new pixel array of the same size (76–150 KB each; the array lands in the large-object space). That's ~4.6 KB of garbage per dot row, ~5.5 MB for a 20-item receipt, and one or two GCs.
- It's safe. Reusing one buffer sized to the tallest row would bring it to near zero.
- `paintFor` also creates a new `TextPaint` for every row.

### P6 — Low. The ~40 KB receipt size in the docs is too optimistic (estimate)

| 80 mm receipt | Dot rows | Bytes sent |
|---|---|---|
| Sample receipt, no logo | ~1,200 | ~86 KB |
| Sample receipt, square logo | ~1,776 | ~128 KB |
| 30 items | ~2,900 | ~210 KB |

- Most of this comes from 26-dot text, 3-line product names in the 30% name column, and the 120-dot tear-off.
- The tear-off is sent as 8.6 KB of blank image. The `ESC J` feed command would do the same in 3 bytes.
- At the ~70 KB/s link measured on the device, plus 4 ms pauses every 1 KB, a 210 KB receipt takes ~3–4 s. That's still less than the print head's own paper time, so it's fine, but budget for it.

### P7 — Low. Small leftovers

- The settings preview (`PrintSettingsViewModel.kt:157`) re-draws on any settings write. It needs `distinctUntilChanged()` on (paper, business) and `mapLatest`.
- `benchmarkPaperMm` builds the full 40 KB test pattern inside composition just to read its height (`PrinterSelectionScreen.kt:511`).
- A `Failed` print status carries over into the next receipt sheet opened on the same screen.

### P8 — Medium. The sale detail loads the whole catalogue and client list for one receipt

- `VentesScreen.kt:705-716` collects all products and all clients to look up one client and the per-colis counts.
- It also rebuilds `receiptPackSizes` over the whole catalogue after every sale.

**Thread safety.** Drawing on Default is safe: `StaticLayout` and `Canvas` on a private bitmap are fine off the main thread. The transports' `Thread.sleep` pauses hold one IO thread for the length of a print, which is acceptable.

---

## 3. The new baseline

### High

**H1 — Stock triggers slow down every stock write as a product's history grows.**

Each movement row re-sums that product's entire history: 3 subqueries. The guard trigger on `products` then runs the same 3 again in its `WHEN`, so every sale line costs **6 full history scans**.

Measured with a 30-line sale, where each of the 30 products already had H movements:

| Movements per product | v54 as shipped | Guard trigger removed | Covering index* |
|---|---|---|---|
| 1,000 | 134 ms | 142 ms | **44 ms** |
| 5,000 | **1,276 ms** | 558 ms | **185 ms** |
| 20,000 | **4,559 ms** | 2,261 ms | **566 ms** |

\* Covering index: `stock_movements(product_id, emplacement, direction, quantity)`.

- Times are from a laptop. Expect roughly 3–5× on a mid-range phone, which puts a 30-line bon on a best-seller with 5k movements at **~4–6 s inside the write lock**.
- Editing a vente deletes and re-inserts its movements, which roughly doubles the cost. Imports and inventory apply work the same way.
- **This replaces C4 as the top write-path risk.**
- Fixes, from cheapest: add the covering index (7–8×), plus a matching one on `chargement_items`. Make the guard trigger cheaper. Long term, switch to delta triggers with a periodic reconcile.

**H2 — P1: printing draws the receipt on Main.**

**H3 — One sale re-sends the whole catalogue to every open screen.**
- A sale updates `products.stock`, which re-emits the entire catalogue, re-mapped on Main, to every subscriber (50 sites).
- It also restarts the five `LaunchedEffect(products)`, each doing cart × catalogue work.
- At 10k products that's roughly 10k objects plus a barcode `groupBy` per emission (estimate). It grows with the catalogue and lands on the forms reps use most.

**H4 — History lists that load everything.**
- Achats: 1 + 2N queries, loading every line of every bon.
- Chargements and sessions: two levels of N+1.
- Ventes: one query, but the whole table.
- Mouvements: no `LIMIT`, and the `IS NULL OR` pattern blocks index use.
- Inventory history.
- None of these screens is paged.

### Medium

- **M1** — P2 + P4: every print draws the receipt twice, ~13 MB of logo work per draw, and the logo height is uncapped.
- **M2** — P3: the preview's images are built on Main, at 32 bits per 1-bit dot.
- **M3** — The drafts badges read every sale id and every bon's status, and re-run on each write to those tables. The fix is the subquery in §1.
- **M4** — `(:p IS NULL OR col = :p)` blocks index use in `getVentesWithDetails` and the movement filters.
- **M5** — Eight form view-model sites load the whole catalogue or client table and then `.find{}` one row: `getProducts()`/`getClients()`/`getSuppliers()` in the Vente, TournéeVente, Purchase, Chargement and Retour sessions.
- **M6** — P8: the sale detail collects the whole catalogue and client list for one receipt.
- **M7** — Backups:
  - `DatabaseSnapshot.take` holds the **write lock while copying the whole database file** (`:74-78`). That's ~0.5–2 s of blocked sales at 100 MB (estimate).
  - `DataFingerprint` hashes every row of every table on each automatic backup, allocating one string per cell.
- **M8** — R8/minify is still off (`app/build.gradle.kts:34`).

### Low

- **L1** — P5: per-row bitmap and array churn.
- **L2** — The tear-off is sent as a blank image instead of a feed command.
- **L3** — P7: the settings preview re-draws too often, the test pattern is built in composition, and a failed status carries over.
- **L4** — The network printer scan holds 48 of the 64 IO threads for ~2 s.
- **L5** — Still 0 foreign keys, no crash/ANR reporting, and no StrictMode in debug builds. ANRs still can't be measured in the field.
- **L6** — `ProductRepository` is 1,916 lines, with 59 `Map<String, Any>` signatures.
- **L7** — `CanvasReceiptRenderer` has no test. Nothing catches a regression in row height or receipt size.

---

## 4. Before Phase 5 (TSPL/CPCL)

1. **Fix P1.** Produce the bytes off Main. TSPL and CPCL will produce theirs at the same line.
2. **Draw once, reuse everywhere.** Pass the preview's result to `print()`, and cache the logo raster (P2/P4). TSPL `BITMAP` and CPCL `EG` need the total height declared up front. The current list of rows gives you that without stitching one tall bitmap; keep it that way.
3. **Cap the logo height and send blank space as a feed command** (P4/L2). On label stock, paper is the label.
4. **In parallel, off the print track:** H1's covering index is a one-migration change and gives about a 7× speed-up.

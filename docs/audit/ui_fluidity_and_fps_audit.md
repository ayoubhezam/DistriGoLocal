# UI Fluidity & Frame-Rate Audit — 2026-10-07

**What was read:** `main` at `3ac4e16` (Dashboard step 1): 465 Kotlin files (~80k lines), 214 of them UI (~54k lines).
Toolchain: Compose BOM 2024.09.00 (Compose 1.7, Material 3 1.3), Kotlin 2.0.21 (strong skipping on by default), Coil 2.5,
Paging 3.3.6, Navigation 2.8.4. No code was changed.

**Measured vs estimated:** the code was read first, with the phone unplugged, so the findings below started as estimates.
Each finding is marked **verified** (the whole code path was read) or **likely** (strong evidence, not confirmed on the
phone). The same evening, Step 0 measured the app on the phone: see the next section. Where a measurement contradicts an
estimate, the finding says so.

**Target device:** Galaxy M34 (SM-M346B): Exynos 1280, 120 Hz screen. At 120 Hz a new frame is due every **8.3 ms**; at
60 Hz, every 16.7 ms. Work that fits at 60 Hz can still drop frames at 120.

---

## Step 0: measured on the phone (same evening)

**Setup.** The same scripted walk ran twice on the Galaxy M34 at 120 Hz, with the stress data on it (~10k products, ~40k
sales, ~1,500 clients):
1. On the installed **debug** build.
2. On the new **`benchmark`** build: release code, not debuggable, compiled with its baseline profile the way a Play install
   ends up (`cmd package compile -m speed-profile`).

**How to read it.**
- The numbers come from `dumpsys gfxinfo`, reset before each step.
- *Janky* is Android's own count of frames that missed their display deadline.
- *Worst* is the histogram bucket of the slowest frame.
- Steps that render about 40 frames move by about 2.5 % per frame, so treat them as rough.

| Step | Debug: janky / worst | Benchmark: janky / worst | Verdict |
|---|---|---|---|
| Cold start (`TotalTime`, 3 runs) | 1.8–2.0 s | **0.37–0.40 s** | 5× faster |
| Produits fling (4 down, 4 up) | 26.2 % / 150 ms | **0.4 % / 27 ms** | Smooth |
| Dépôt Vente fling | 12.5 % / 61 ms | **0.3 % / 27 ms** | Smooth |
| Client detail, tab swipes | 4.6 % / 46 ms | **2.4 % / 34 ms** | Smooth |
| Product detail, tab swipes | 2.3 % / 57 ms | **1.4 % / 32 ms** | Smooth |
| Product form, tab swipes | 28.6 % / 150 ms | **3.4 % / 61 ms** | Acceptable; 3 frames over 50 ms |
| Open Dépôt Vente | 9.8 % / 250 ms | **5.1 % / 48 ms** | A hitch on the first frames |
| Open a sale and back (×3) | 10.7 % / 150 ms | **5.8 % / 57 ms** | A hitch on the first frames |
| Drawer → Clients | 6.7 % / 150 ms | **6.7 % / 44 ms** | A hitch on the first frames |
| Open a client's detail | 6.7 % / 150 ms | **7.1 % / 105 ms** | The heaviest screen to open |
| Open the Ventes report | 5.0 % / 57 ms | **7.7 % / 46 ms** | A hitch on the first frames |
| **Typing in the clients search** | 59.1 % / 77 ms | **29.6 % / 30 ms** | **Janky in release too** |
| **Filtres → Client dropdown (Dépôt Vente)** | 75 % / **1,400 ms** | **28 % / 300 ms** | **A freeze in release too** |

**What it changes:**
1. **The debug build is most of what you felt** (C1 confirmed, the biggest single factor). The lists go from 12–26 % janky
   to under 0.5 %, and cold start is 5× faster. The product form swipe drops from 29 % to 3 %.
2. **Two hotspots survive a release build:** the Client dropdown (C2, a 300 ms freeze) and typing in the clients search
   (M2, 30 % of frames late). These are now the top code fixes.
3. **Opening a screen drops 2–5 frames at its start.** The worst frame is 44–105 ms; the client detail is the heaviest.
   That is M5 plus each screen's first composition, which is what R8 and the baseline profile (Step 1) target. Trace it
   with Perfetto before changing code for it.
4. **Not confirmed:**
   - C4: the Dépôt Vente fling is 0.3 % janky in the benchmark build. The pending rows' extra work fits inside the frame,
     so C4 drops to **Low**: its fixes become clean-ups, not priorities.
   - C3: the product detail swipe is smooth too (1.4 %). Only the product form swipe keeps a few long frames.
5. **Not measured yet:** the sale and purchase pickers (C5), changing a report's filters, the keyboard animation (M4).
   Add them to the walk before working on them.

**Revised order of work:**
1. Step 1: R8 + Baseline Profile.
2. Step 2: the dropdowns.
3. Step 7: search off Main (moved up).
4. Screen openings, the client detail first, traced before being fixed.
5. The product form's half of Step 3.

The rest is polish.

---

## Step 1: R8 + Baseline Profile (measured 2026-10-08)

**What changed:**
- **R8 in release (and benchmark):** shrinking and resource shrinking, no renaming (`-dontobfuscate`).
  - The APK goes from 43.3 MB to 26.7 MB, in one dex.
  - The six classes Gson reads by field name are kept whole (`proguard-rules.pro`): the four draft lines and the geo file.
    The minified dex was checked field by field.
  - On the phone: the wilayas and their communes list correctly, and nothing crashed.
- **An app Baseline Profile** (`app/src/main/baselineProfiles/baseline-prof.txt`):
  - Recorded on the phone by the new `:baselineprofile` module, on the non-minified build. The walk is read-only.
  - The module is installed and run by hand, never through Gradle's connected tasks, which uninstall the app.
  - It holds 31,875 rules, 6,045 of them the app's own. After R8's rewrite, 17,431 remain; the rest belonged to
    methods R8 inlined into their callers.

**Measured** with the same walk on the benchmark build, now R8 + profile, against Step 0's benchmark build (janky /
worst frame):

| Step | Step 0 benchmark | R8 + profile |
|---|---|---|
| Cold start | 0.37–0.40 s | **0.28–0.31 s** |
| Produits fling | 0.4 % / 27 ms | 0.8 % / **19 ms** |
| Dépôt Vente fling | 0.3 % / 27 ms | 0.5 % / **21 ms** |
| Open Dépôt Vente | 5.1 % / 48 ms | 5.0 % / **40 ms** |
| Open a sale and back (×3) | 5.8 % / 57 ms | **3.4 %** / 53 ms |
| Filtres → Client | 28 % / 300 ms | **7.2 % / 69 ms** (Step 2's picker) |
| Drawer → Clients | 6.7 % / 44 ms | 5.5 % / **34 ms** |
| Open a client's detail | 7.1 % / 105 ms | 10.3 % / **48 ms** |
| Client tab swipes | 2.4 % / 34 ms | 2.0 % / **18 ms** |
| Product detail tab swipes | 1.4 % / 32 ms | 1.4 % / **18 ms** |
| Product form tab swipes | 3.4 % / 61 ms | **2.2 % / 36 ms** |
| Typing in the clients search | 29.6 % / 30 ms | 31.5 % / 30 ms |
| Open the Ventes report | 7.7 % / 46 ms | not measured: the walk was stopped first by low memory on the PC |

**What it changes:**
- **Faster everywhere.** Cold start is a quarter faster, and the slowest frame of every screen opening and swipe
  roughly halves. No step has a frame over 69 ms.
- **The product form swipe is on target** (2.2 %, nothing over 50 ms) without touching its code. The Step 3 rewrite of
  its pager reads is now a clean-up.
- **Typing in the clients search is unchanged at 31 %.** It is main-thread filtering of ~1,500 clients, which no build
  setting fixes: Step 7 is next.

**Against the definition of done:**
- Flings and swipes: on target.
- Openings: on target except a sale's page (53 ms) and the client picker (69 ms, its second sheet).
- No freezes left.

---

## Screen openings: traced (2026-10-08)

**How it was traced.** `ScreenOpeningBenchmark` (`:baselineprofile`) opened each slow screen five times on the
phone, recording a Perfetto trace per opening. It ran on the `tracing` build: release, R8, profileable, with Compose
composition tracing.

**Caveats.**
- Tracing slows frames, so the share of late frames in these traces is not comparable to the walk. What the traces
  show reliably is what the slowest frames are made of.
- UiAutomator keeps accessibility on, which adds 2–6 ms of semantics work per opening that a normal user doesn't pay.

**Composition is never the large part.** The screens' own composables cost about 1–3 ms in a slow frame, with
tracing on. The time goes to window creation, measure/layout, recording the draw, text layout and the render thread.

| Opening | Slowest main-thread frame (traced) | What fills it |
|---|---|---|
| **Client picker** (Dépôt Vente → Filtres → Client) | 64–82 ms | **A second window being created**: binder calls to the window manager 17–21 ms, window relayout 4–7 ms, the new window's first traversal 7–15 ms, its measure 4–6 ms. With three windows up, the render thread swaps three surfaces (`eglSwapBuffers` 57 ms per opening, against 21–27 elsewhere), and in two of five runs nearly every frame was late. |
| **Client detail** | 28–45 ms (first frame) | Measure/layout 7–15 ms, recording the draw of the whole screen 6–10 ms, text layout 2–3 ms, applying changes 2 ms; `ClientDetailScreen`'s composition ~3 ms. The screen is one non-lazy scrolling column, so all of it is measured and recorded in frame 1, below the fold included. |
| **Ventes report** | 22–40 ms (first frames) | Measure/layout 6–16 ms and **102 text layouts per opening** (2.5–4.3 ms per frame): `FitText` settling in passes, and `AnimatedFigure`'s hidden measuring copy (M6). Composition ~1 ms per card. |
| **A sale's page** | 13–23 ms (first frame) | Measure 4–9 ms, draw 3–4 ms, four ViewModels created ~1 ms each. The other late frames are the render thread drawing both screens through the slide: 19 % of its frames were render-thread-bound, the most of the four. |

**Across all four:**
- The render thread spends 24–28 ms per opening filling full-screen rectangles (`FillRectOp`): backgrounds painted
  over one another. It also draws text atlases and rounded corners.
- The main thread waited on the render thread (`postAndWait`) for 45–120 ms per opening.

**Proposed fixes, by certainty:**
1. The client picker's list inside the filter sheet, not a second window. **Done 2026-10-09:**
   - `SearchableSelectList` replaces the filter sheet's content in Dépôt Vente, Achats, the tournée and Mouvements.
   - `FilterSheet` owns Back: from the list back to the filters, then closed. Material's sheet takes Back first on
     Android 13+ and would close it all, so its own Back is turned off. The cost: no predictive-back preview on
     these four sheets.
   - Measured on the R8 + profile build, the list now opens in 2 frames. Slowest frame: Dépôt Vente 30 ms (was
     61), Achats 28 (65), tournée 30 (73), Mouvements 34 (77).
   - On the phone: the back arrow and the system Back return to the filters, which stay scrolled where they were;
     a chosen client fills the field; "Tous les clients" clears it.
2. One-pass `FitText`, and `AnimatedFigure` without the hidden copy (M6).
3. Overdraw: check with the overdraw debug view, then drop the duplicated full-screen backgrounds.
4. A lighter first frame for the client detail and the sale page. Lower certainty: trace again after 1–3.

---

## Executive summary

### Rating: **6 / 10**: good bones, five hotspots, and a test build that makes everything look worse

> **After Step 0:** measured in a release-like build, the code rates **7 / 10**. The lists and swipes are smooth there;
> what remains is one freeze, janky typing in the clients search, and a hitch when a screen opens. The build on the
> phone stays at 2.

The foundations are right. Every long list is paged and keyed, photos are files read by Coil, the Base64 payloads are gone
from the scroll path, and the report animations are scoped to the text they change. The heavy feel comes from three places:

1. **The build on the phone.** It is the debug APK: debuggable, no R8, no baseline profile, StrictMode on. Every frame costs
   more than it would in a release build, and every hotspot below is amplified.
2. **A few places that redo a whole screen every frame, or build hundreds of items at once.** The tab swipe in the product
   form and the product detail re-runs the whole screen on every frame. The filter dropdowns build every client in one frame.
3. **Rows that carry more than they show.** Each pending sale row carries a hidden swipe band and its own coroutine. Each row
   parses a date and builds a date formatter. Each photo row checks the disk on the main thread and blinks its placeholder.

| Area | Score | In one line |
|---|---|---|
| Build & runtime config | **2** | Debug APK on the phone; R8 off (open since the 2026-09-23 audit, M8); no app baseline profile |
| Lazy lists | **7** | Paged and keyed everywhere; pending-sale rows are heavy; picker rows cannot skip |
| Recomposition discipline | **5.5** | Search text read at the top of large screens; whole client table re-sent on every write |
| Phase discipline (composition → layout → draw) | **5** | Charts are exemplary; pagers, tab underlines and the drawer read per-frame values in composition |
| Images | **6.5** | Coil + file store done right; a disk check per row on Main; a one-frame placeholder blink |
| Animations & transitions | **6** | Animations are restrained; 300 ms slides everywhere, tab switches included; FitText takes several frames to settle |
| **Overall** | **6** | The 2026-09-23 audit scored Compose performance 6.5; this deeper pass lands a little lower |

### Answers to the five questions

| Question | Short answer | Where |
|---|---|---|
| 1. Recomposition churn | Real, but not from missing `@Stable`. Strong skipping is on, so unstable params are compared by identity. The churn comes from state read too high and lambdas rebuilt each pass: `itemKey` (paging lists), search text, whole-table flows. | C5, M2, M3, M8 |
| 2. Phase reads | Charts and the drawer's translation defer correctly. The pagers do not: the product form and product detail read the swipe offset in their own body. Two `offset {}` / `graphicsLayer {}` lambdas *look* deferred but receive a value computed in composition. | C3, M4, M7 |
| 3. Lazy layouts | Keys: yes (93 sites; 5 small unkeyed lists). `contentType`: only on header/row lists. Heavy per-row work: date parsing, a swipe state and coroutine, a disk check, `groupBy` inside the list builder. | C4, C5, M9 |
| 4. Base64 images | **Already fixed** (2026-09-23, C2). Rows hold `img:<hash>`; nothing decodes Base64 while scrolling unless a row escaped the backfill. Today's image cost is different: a disk check per row on Main, and Coil's size waiting for the first draw (placeholder blink, two compositions). | M1 |
| 5. Animations & transitions | Navigation: 300 ms slides for every move, including bottom-tab switches. Each incoming screen composes in frame 1 with no profile and fills in mid-slide. The product form's page effect animates a 16 dp shadow and an off-screen alpha on two full-screen layers. | C3, M5, M6, M7 |

### Do these three first

1. **Measure on a release-like build** (Step 0), then turn on R8 and add a Baseline Profile (Step 1). This changes every number below.
2. **Replace the four big dropdowns** with the lazy, searchable sheet the app already has (Step 2). They are the only freezes
   that last hundreds of milliseconds.
3. **Read the swipe offset in layout/draw** in the product form and product detail (Step 3). The swipe stays exactly as it
   looks; it just stops costing a full screen per frame.

---

## Critical frame drops (the jank makers)

### C1 — The phone runs a build that makes every frame more expensive *(verified)*

| Evidence | |
|---|---|
| What is installed | `app-debug.apk`, 55.8 MB, built today 14:46, **93 MB of dex** (`material-icons-extended` and every library unshrunk) |
| Release build | `isMinifyEnabled = false` (`app/build.gradle.kts:34`); no signing config, so it has never been installed on the phone |
| Profiles | No baseline-profile module. The release APK carries only the libraries' merged profile (`assets/dexopt/baseline.prof`, 12.7 KB). The debug APK carries none. |
| Debug extras | `DebugStrictMode` (`diagnostics/DebugStrictMode.kt:36-41`): `detectAll()` + `penaltyLog()`, so every disk touch on Main captures a stack trace and writes a log line |

**Why it drops frames.** In a debuggable app, ART does not use profile-guided ahead-of-time code. Much of Compose then runs
JIT-compiled or interpreted. Without R8 there is no inlining, class merging or dead-code removal. Each screen's first
composition is the slowest path of all, and it lands in frame 1 of every navigation slide. So does each new row during a
fling. Compose performance is commonly 2× or more slower in a debug build (an estimate; Step 0 measures it on this phone).

**What this means for the report.** Part of what you feel is not what a release build would feel. The hotspots below are
real either way: a debug build just makes them visible sooner.

**Fix:** Steps 0 and 1 of the roadmap. Add a `benchmark` build type, R8 with keep rules for the Gson models, and a Baseline
Profile generated from a scripted walk.

---

### C2 — Filter dropdowns that build every client in one frame *(verified; measured)*

> **Measured:** opening the Dépôt Vente Client dropdown takes **1,400 ms** in one frame on the debug build and
> **300 ms** on the benchmark build. Confirmed, and still the top code fix.
>
> **Fixed (Step 2, 2026-10-07):** the four dropdowns now open `SearchableSelectSheet`: a lazy list with a search, a
> "Tous les …" row and a check on the current choice. Measured on the benchmark build, the worst frame when the picker
> opens:
> - Dépôt Vente → Client: **61 ms** (was 300 ms); janky 28 % → 8.6 %.
> - Achats → Fournisseur: 65 ms.
> - Tournée → Client: 73 ms.
> - Mouvements → Avec → Client: 77 ms.
>
> No freeze is left. Each still has two frames over 50 ms, as the second sheet's window appears; showing the list inside
> the filter sheet itself would remove them. Typing in the picker's search is 20 % janky, with no frame over 32 ms.

`DropdownMenu` and `ExposedDropdownMenu` are not lazy. Their content is a scrolling `Column` in a popup, so opening one
composes, measures and lays out **every** item before the first frame of the menu.

| Where | What it lists | Size on the test phone |
|---|---|---|
| `ui/ventes/VentesScreen.kt:325` (filter sheet → Client; the choices come from `:94-95`) | Every client with a dépôt sale | ~1,500 |
| `ui/mouvements/MovementFiltersSheet.kt:170` | Every client or supplier who moved this product | Hundreds for a best-seller |
| `ui/purchases/PurchasesScreen.kt:369` | Every supplier with a bon | Tens to hundreds |
| `ui/tournees/TourneesScreen.kt:1589` | Clients of one tournée | ≤ 100 |

About 1,500 `DropdownMenuItem`s at roughly ten layout nodes each means ~15,000 nodes built in one frame. That is a freeze of
**hundreds of milliseconds** (estimate), every time the menu opens.

**Fix:** use `SearchableSelectSheet` (`ui/common/SearchableSelectSheet.kt`: lazy, searchable, already in the app) or
`ClientSearchPicker`. Add `key = { it.id }` to its `items(filtered)` (`SearchableSelectSheet.kt:65`, currently unkeyed).

---

### C3 — Tab swipes that redo a whole screen on every frame *(verified; measured milder)*

> **Measured (benchmark build):** product form swipes are 3.4 % janky, with 3 frames over 50 ms. The product detail
> (1.4 %) and client detail (2.4 %) swipes are smooth. The per-frame work described below is real, but it fits in the
> frame everywhere except the product form. Fix the form first; the rest is clean-up.

The swipe between tabs is a gesture you value (see *keep-swipe-tabs*). None of this removes it; it changes **where** the
swipe position is read.

**Product form** (`ui/products/ProductFormScreen.kt`)
- `:631` reads `pagerState.currentPageOffsetFraction` directly in `ProductFormScreen`'s body. The `Column` and `Box` around
  it are inline, so the restart scope is the whole ~1,200-line function. Every frame of a swipe re-runs it: every `remember`,
  every dialog flag, the header, the pill and the `HorizontalPager` call.
- `:671-693` re-tints both labels each frame, and flips their font weight mid-swipe (a text re-layout).
- `:705-716`: each page computes its offset **in its own composition**, then hands the result to `graphicsLayer {}`. The
  lambda therefore defers nothing, and both visible pages recompose every frame. The layer also animates
  `shadowElevation` 0 → 16 dp and `alpha` 1 → 0.97 on two full-screen pages. Any alpha below 1 makes Compose render the
  page into an off-screen buffer first. That is two full-screen buffers plus a large shadow, every frame (GPU fill cost).

**Product detail** (`ui/products/ProductDetailScreen.kt`)
- `:255-256` read the same offset in `ProductDetailScreen`'s body: the whole screen recomposes every frame of a swipe.
- `:386` `DetailTabs` is a `BoxWithConstraints` (a subcomposition), recomposed every frame.
- `:413` `.offset(x = tabWidth * position)` is the non-lambda form, so the underline is re-laid out each frame.

**Client and supplier details**: `ui/common/ElasticUnderlineTabRow.kt`
- Lighter: `:47` reads the offset inside the tab row's own scope, so only the row recomposes per frame. Its labels are
  re-tinted and change weight at 0.5.
- `:96`: `offset { IntOffset(indicatorLeftPx…) }` captures a value already computed in composition. It looks deferred and
  is not.

**The pattern, and the fix:**

```kotlin
// Not deferred: the state is read in composition; the lambda only receives the result.
val absOffset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction).absoluteValue
Modifier.graphicsLayer { alpha = lerp(1f, 0.97f, absOffset) }

// Deferred: the read happens inside the layer block, at draw time. Nothing recomposes.
Modifier.graphicsLayer {
    val off = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction).absoluteValue.coerceAtMost(1f)
    alpha = lerp(1f, 0.97f, off)
    compositingStrategy = CompositingStrategy.ModulateAlpha // no off-screen buffer
    // and no animated shadowElevation: a constant one, or none
}
```

- Underlines: pass `position: () -> Float` and read it inside `Modifier.offset {}` / `layout {}` / `drawBehind {}`.
- Label colour: `BasicText(color = { lerp(…, position()) })`. A `ColorProducer` is read at draw time.
- Font weight: `remember { derivedStateOf { abs(position() - index) < 0.5f } }`. It recomposes once per swipe, not once per frame.
- Tab titles: `remember { listOf(…) }`. `ClientDetailScreen.kt:307` and `ProductDetailScreen.kt:254` build a new list on
  every recomposition. The compiler reports show `tabs: List<String>` is unstable, so the tab row never skips.

---

### C4 — Heavy rows in Dépôt Vente, Achats and a tournée's sales *(code verified; measured: not a frame-dropper, now Low)*

> **Measured:** the Dépôt Vente fling is 12.5 % janky on the debug build and **0.3 %** on the benchmark build. In a
> release build these rows fit inside the frame, so the fixes below are clean-ups, not priorities.

These are the lists flung most often. Every **pending** row is wrapped in `SwipeToConfirm` (`ui/common/SwipeToConfirm.kt`),
used at `VentesScreen.kt:599`, `PurchasesScreen.kt:647` and `TourneesScreen.kt:794`.

| Per pending row | Where | Cost |
|---|---|---|
| A `SwipeToDismissBoxState` (anchored-draggable state) | `SwipeToConfirm.kt:69` | Allocated when the row enters the screen |
| **A coroutine** (`LaunchedEffect` + `snapshotFlow`) | `:81-85` | Started when the row appears, cancelled when it leaves. A fling through 50 rows starts and cancels 50 coroutines. |
| The green "Livré" band (Row + Icon + Text + clip + background) | `:91-104` | Composed, laid out **and drawn under every pending row at rest**: one hidden row-sized layer per row |

Inside, `VenteCard` (`VentesScreen.kt:1121`) adds per-row work:
- `formatOrderTime` (`PurchasesScreen.kt:901-908`, called at `VentesScreen.kt:1172`) parses the ISO instant, looks up the
  zone, and **builds a new `DateTimeFormatter` from its pattern** on every composition of every row.
- Day headers do the same with a French full-date pattern plus `LocalDate.now()` (`PurchasesScreen.kt:883-899`).
- The avatar splits and joins the client's name for initials (`EntityAvatar.kt:34`), and pays the image costs in **M1**.
- The status ribbon is a rotated text layer.

A fling brings 1–3 new rows per frame. On a debug build this is likely the difference between holding 120 Hz and dropping
frames (estimate). A release build shrinks it but does not remove it.

**Fix**
- Compose the band only while the row moves: `if (state.dismissDirection != SwipeToDismissBoxValue.Settled)` inside
  `backgroundContent`.
- Re-arm the once-per-swipe guard with a `DisposableEffect` inside that band (`onDispose { fired = false }`), so a row at
  rest runs no coroutine. Keep the guard: Material 3 1.3 calls `confirmValueChange` on every drag frame. Test only on
  undoable records.
- Hoist the formatters to top-level `val`s (`HH:mm`, `Africa/Algiers`, the French day pattern). Better: build each row's
  strings once in `PagingData.map {}`, which already runs off Main.
- Give `contentType` separate values for swipeable and plain rows.

---

### C5 — The sale and purchase pickers re-run every visible row on each interaction *(verified)*

This is the screen reps use most: step 2 "Produits" of Vente dépôt, Tournée vente and Achat.

- `ui/navigation/VenteFormNavGraph.kt:236-247` reads the search, cart, count, stock policy and filters at the top of the
  destination. Any change re-runs the whole destination.
- `:340` `pagedProducts.itemKey { it.id }` **returns a new lambda on every call**. The list's builder therefore changes, the
  `LazyColumn` rebuilds its item provider, and every visible row's item lambda runs again.
- `:348-439`: the row is written inline in `items {}`, so it cannot skip as a unit. `:342` scans the cart for every row.
- Triggers: each keystroke (before and after the 300 ms debounce), each add or remove, each page load, each count update.
- The same code is in `TourneeVenteFormNavGraph.kt:300` and `PurchaseFormNavGraph.kt:602`.

**Fix**

```kotlin
val productKey = remember(pagedProducts) { pagedProducts.itemKey { it.id } }   // one lambda for the screen's life
val cartIds    = remember(cartItems) { cartItems.mapTo(HashSet()) { it.product.id } }
items(count = pagedProducts.itemCount, key = productKey, contentType = { "product" }) { index ->
    val product = pagedProducts[index] ?: return@items
    PickerProductRow(product, inCart = product.id in cartIds, depotEmpty = …, onAdd = …, onRemove = …)
}
```

One shared `PickerProductRow` serves all three forms. Move the search-text read into the search field's own small composable,
so the destination does not recompose on each key. `ProductsScreen.kt:668` has the same `itemKey` pattern. There the rows
are already extracted (`ProductCard`), so they skip and the cost is smaller.

---

## Medium UI risks

### M1 — Images: the Base64 follow-up *(verified; the blink is likely)*

**Where Base64 stands.** It is gone from the scroll path, fixed by the 2026-09-23 audit's C2:
- A photo is a file named by its SHA-256 (`data/image/ImageStore.kt`), and a row holds `img:<hash>` (~70 bytes).
- `ImageBackfill` rewrote the old `data:image/jpeg;base64,…` rows at startup.
- Every image is drawn by `EntityImage` (`ui/common/EntityImage.kt`) through Coil. Coil decodes at display size, off the
  main thread, with a memory cache shared across screens.
- The legacy branch (`EntityImage.kt:71`) still decodes Base64 **in composition**, but only for a row the backfill has not
  reached. That should be none; it is a fallback, not a cost.

**What a photo row costs today** (Produits 36 dp, sale and picker rows 42 dp, grid 110 dp), each time it scrolls in:

1. **A disk check on Main**: `remember(ref) { … ?.takeIf { it.isFile } }` (`EntityImage.kt:68-74`). Tens of microseconds.
   In debug, StrictMode flags it as a disk read and records a stack trace each time, likely 0.1–1 ms per row.
2. **A new `ImageRequest` on every recomposition** (`:114-116`); it is never remembered.
3. **No size on the request.** `rememberAsyncImagePainter` without a size waits for the painter's first *draw* to learn
   it. So even a photo already in the memory cache is looked up only after frame 1. Frame 1 shows the initials or cart
   icon; frame 2 the photo. That is the blink on fast scroll.
4. **`painter.state` read in composition** (`:132`): the row's image recomposes again when the load succeeds.

**The Compose strategy:**
- Keep Coil and the file store.
- Take the disk check out of composition. Hand the `File` straight to Coil: a missing file becomes `State.Error`, which
  already shows the same placeholder.
- Tell Coil the size up front. Either `.size(px)` from the dp the caller already knows, or `AsyncImage`, which takes it from
  the layout constraints before the first draw. A cached photo then appears in the row's first frame: no blink, one
  composition.
- Remember the request: `remember(model, px) { ImageRequest.Builder(context).data(model).size(px).build() }`.
- Keep the placeholder decision out of composition. Draw the placeholder underneath and let the photo paint over it.
  Where `ContentScale.Fit` leaves margins (grid cards), hide the placeholder with
  `Modifier.drawWithContent { if (painter.state !is AsyncImagePainter.State.Success) drawContent() }`. That is a
  draw-phase read: no recomposition.
- Coil's defaults are right for this app: crossfade off, hardware bitmaps, memory cache ~25 % of the app's heap.
  No custom `ImageLoader` is needed.

---

### M2 — Typing in a search box does a screen's worth of work per keystroke *(verified; measured: now Critical)*

> **Measured:** typing in the clients search is 59 % janky on the debug build and **30 %** on the benchmark build
> (median 18 ms per frame). It is the only everyday interaction still janky in a release build, so it moves up
> beside C2.
>
> **Clients search fixed (Step 7, 2026-10-08).** The search text and the filtered list moved to `ClientViewModel`
> (`listSearch`, `shownClients`). The list is worked out on `Dispatchers.Default` once typing pauses (300 ms, as in
> Produits). Only the search box reads the text, so a key no longer recomposes the whole screen. Leaving the list still
> clears the search. Measured on the R8 + profile build, typing one key at a time:
>
> | | Before | After |
> |---|---|---|
> | "client" typed and erased: median / 95th / slowest frame | 11 / 21 / 25 ms | **10 / 15 / 21 ms** |
> | "client": slow UI-thread frames | 12 | **3** |
> | "1234" typed and erased (the rows change every key): 95th / slowest frame | 30 / 32 ms | **16 / 16 ms** |
> | Keyboard sliding open: slowest frame | 28 ms | 22 ms |
>
> The share of late frames while typing stayed near 45 %, and it doesn't measure the search. With the keyboard up and
> nothing typed, the blinking cursor's own frames miss their deadline too: 7 of 12 on Clients and **12 of 12 on
> Produits**. Every one is a slow draw-command issue on the render thread, with the UI thread never slow. Frames that
> come only now and then, with the keyboard's window on screen, miss by a few milliseconds whatever the app does. One
> refresh (8 ms) late is not visible. For typing, read the frame times and the slow UI-thread count instead.
>
> **Debtors and Produits fixed (Step 7, 2026-10-08):**
> - **Debtors** ("Voir tout" under Créances et dettes): `DebtReportViewModel.debtorList` searches (debounced 300 ms)
>   and sorts on `Dispatchers.Default`; a new order applies at once.
>   - The name sort makes a `Collator` per call, since one shared across threads is unsafe, and compares collation
>     keys, one per name.
>   - The count, the "Aucun résultat" line and the scroll to the top follow the list shown.
>   - Only `DebtorSearchField` reads the keys. `DebtorListQueryTest` still passes (French order, accents, case).
> - **Produits:** only `ProductSearchField` reads the search text, and the list's key function is remembered.
>   `itemKey` made a new one each recomposition, which rebuilt the list's items.
>
> On the phone, R8 + profile build:
> - Debtors: 1,512 sorted by name; "12" gives "45 sur 1512", same-name debtors keep the biggest debt first, and
>   erasing gives back 1,512. Typing, keyboard opening included: 5 slow UI-thread frames, 95th / slowest 19 / 28 ms.
> - Produits: 15,159; "cevital" gives 720; erasing gives back 15,159. Typing seven keys, keyboard included: 3 slow
>   UI-thread frames, 18 / 24 ms.
>
> No before-measurement was taken for these two screens.

| Where | What runs per keystroke, on Main |
|---|---|
| `ui/products/ProductsScreen.kt:580` | `viewModel.searchQuery` is read in the screen's top scope, so the ~670-line body re-runs. `:668` then rebuilds `itemKey`, which re-invokes every visible row (they skip; see C5). |
| `ui/clients/ClientsScreen.kt:66` and `:157` | `filterClients` over the **whole client table** (~1,500 on the test phone), no debounce |
| `ui/rapports/DebtReportScreen.kt:388-390` with `DebtorListQuery.kt:17,30-31` | Filters, then for "Nom A→Z / Z→A" sorts with a French `Collator`. About 10–15k collator comparisons for 1,000 debtors: likely 5–20 ms per keystroke (estimate). |

**Fix:** debounce and compute on `Dispatchers.Default` in the ViewModel, or page Clients the way Produits is paged (the H4
pattern). Compute collation keys once per report, not per comparison. Read the search text only inside the search field's
own composable.

### M3 — The whole client and supplier tables, converted on Main, after every write *(verified)*

- `observeClients()` (`data/ProductRepository.kt:1574-1575`) and `observeSuppliers()` (`:801-802`) `map` in the collector's
  context. `ClientViewModel.clients` (`ui/clients/ClientViewModel.kt:43-46`) collects in `viewModelScope`, which is Main.
- Every balance change (each sale, each payment) re-sends **all** clients, rebuilt into ~1,500 `Client` objects on the main
  thread.
- Consumers then `.find {}` one row: `ClientDetailScreen.kt:90-91`; `ClientsNavHost.kt:98, 120, 216, 297`; the sale detail
  at `VentesScreen.kt:704-707`; the same pattern in `SuppliersNavHost.kt`. Because a new list arrives, the ~810-line client
  detail recomposes in full after any client write anywhere.
- Still open from the 2026-09-23 audit (M5/M6).

**Fix:** `.flowOn(Dispatchers.Default)` on both flows. Give the detail, the form and the sale detail an `observeClient(id)`
or `observeSupplier(id)`.

### M4 — The app shell recomposes per frame during the drawer and the keyboard *(drawer verified; keyboard likely)*

- **Drawer:** `MainActivity.kt:216` (`drawerOpen`) and `:466-474` (`openFraction`, scrim colour) read `drawerOffset.value`
  in the root's composition. The root content re-runs on every drag event and every animation frame of the Plus drawer.
  The drawer's own movement is correct: a `graphicsLayer` lambda at `:487`.
- **Keyboard:** the root is a `BoxWithConstraints` (`:154`), used only for `maxWidth` (`:164-166`), with `imePadding()` on
  it. A keyboard animation changes its constraints every frame, and `BoxWithConstraints` subcomposes its content again
  whenever it is measured with new constraints. So the whole root is likely re-run on every frame of every keyboard open
  and close. The screen's own re-layout per frame is inherent to `imePadding()` and fine.

**Fix**
- `val drawerOpen by remember { derivedStateOf { drawerOffset.value > 0f } }`.
- Draw the scrim with `Modifier.drawBehind { drawRect(Color.Black, alpha = …drawerOffset.value…) }`.
- Replace the root `BoxWithConstraints` with a `Box`, and take the width from `LocalConfiguration.current.screenWidthDp`.

### M5 — Transitions: a 300 ms slide for everything, tab switches included *(code verified; cost likely)*

- `ui/navigation/NavTransitions.kt:11-24` is used by the root `NavHost` (`MainActivity.kt:277-284`) and by every section's
  nested `NavHost`, up to three levels deep (root → `TourneesHubScreen.kt:48` → `VentesNavHost` / `TourneesNavHost`).
- **Bottom-tab switches** (`MainActivity.kt:243-251`) slide the whole screen. For 300 ms both tabs are composed and drawn.
- The incoming screen composes in frame 1, on the slowest code path (C1). Its flows start empty (`stateIn(…, emptyList())`),
  so content arrives mid-slide and the screen re-lays out while it moves.
- The bottom bar animates each item's `weight` in composition (`MainActivity.kt:650-660`). That re-lays out the bar every
  frame of the same 300 ms.

**Fix:** a ~150 ms fade, or no animation, for tab ↔ tab, keeping the slide for push and pop. Reserve each screen's final
height with skeletons rather than spinners. The Baseline Profile (Step 1) targets exactly this first frame.

### M6 — `FitText` takes several frames to settle *(verified)*

- `ui/common/FitText.kt:51-71` hides the text until it settles. Each shrink step writes state during layout, which
  recomposes on the next frame: up to five hidden frames.
- Even a text that fits costs one extra recomposition (`settled` flips, `overflow` changes).
- On a report, a dozen of these settle during the opening slide.
- In the full debtors list (`DebtReportScreen.kt:360`, list at `:435`) each amount appears one frame after its row.

**Fix:** measure once with `rememberTextMeasurer` in a layout modifier and pick the scale in the same pass. Or, after a
Compose upgrade, use `BasicText(autoSize = …)`, which `FitText`'s own comment says arrived after this version.

### M7 — Small per-frame composition reads during animations *(verified)*

| Where | Per frame | Fix |
|---|---|---|
| `ui/rapports/Figures.kt:109` (`AnimatedFigure`) | Recomposing is by design, but `widest` re-formats three amounts and builds a list every frame | `remember(from, to) { … }` |
| `ui/rapports/DistributionSection.kt:292-301` (`StackedBar`) | `fillMaxWidth(bar)` re-lays out each bar per frame | One `drawBehind` reading both animated values |
| `ui/common/CartSelectionComponents.kt:121,170` ("Ma sélection" card) | The chevron's `.rotate(chevronRotation)` reads in composition, so the whole card recomposes for ~300 ms | `graphicsLayer { rotationZ = rotation.value }` |

### M8 — Unstable models are compared by identity *(verified; impact likely)*

- Strong skipping is on (Kotlin 2.0.21), so missing `@Stable` / `@Immutable` does **not** stop skipping. It changes the
  comparison: an unstable parameter skips only if it is the *same instance*.
- `Product` (`data/model/Product.kt:29`, `barcodes: List<String>`), `Vente` (`Vente.kt:15`, `items`), `Tournee`,
  `PurchaseOrder` and the drafts are all unstable for that reason.
- Any write to a watched table reloads the page and hands every visible row a new instance, so every visible row
  recomposes even when its data did not change. Minor alone; it multiplies C4.

**Fix:** small row UI models built in `PagingData.map` (`@Immutable data class VenteRowUi(…)`, strings pre-formatted). Or mark
the entities `@Immutable`: they are all `val`, and nothing mutates their lists. Enable the Compose compiler reports (Step 0)
before deciding which.

### M9 — Grouping inside the `LazyColumn` builder *(verified)*

`ChargeHistoryScreen.kt:255` and `PerteHistoryScreen.kt:185` run `list.groupBy { BusinessDates.localDay(…) }` over the
whole list, on Main, each time the builder runs. The test phone holds 1,500 charges and 400 pertes. **Fix:** group in the
ViewModel (already off Main), and pass a flat list of header and row items.

### M10 — Flows keep running in the background *(verified)*

259 `collectAsState()` calls in 60 files, and no `collectAsStateWithLifecycle()`. A screen left open in the background keeps
collecting and recomposing on every write: automatic backups, Dashboard reloads. These are not foreground frame drops, but
they waste CPU and battery. **Fix:** add `androidx.lifecycle:lifecycle-runtime-compose` and switch, screen by screen.

### Low (noted for the record)

- `Card` elevation shadows per product row (`ProductsScreen.kt:770, 883`): cheap on the RenderThread, fine.
- The scanner's analyzer runs on the main executor (`ui/scanner/BarcodeScannerScreen.kt:119`). ML Kit detects off Main;
  a background executor would still be cleaner.
- Unkeyed small lists: `CategoriesScreen.kt:260`, `SearchableSelectSheet.kt:65`, `PurchaseFormNavGraph.kt:396`.
- `EntityAvatar` clips twice (`EntityAvatar.kt:41, 48`).
- The product filter's price min/max are not debounced (`ProductViewModel.kt:394-409`): one paging reload per keystroke
  behind the sheet.
- Compose BOM **2024.09.00** (Compose 1.7). Later releases improved lazy-list prefetch and added text auto-size. Upgrade
  after Step 0's baseline numbers exist, and re-check the swipe-once guard.

---

## What is already smooth

- **Paging where it matters:** Ventes, Achats, Produits, every product picker, Mouvements, inventory history. Keyset queries
  and table-watching `PagingSource`s (the 2026-09-23 H4 item is closed).
- **Keys:** on 93 lazy item sites. `contentType` on the header/row lists: `VentesScreen.kt:581-590`, `PurchasesScreen.kt:629`,
  `MouvementsScreen.kt:177`, `InventoryHistoryScreen.kt:104`.
- **Extracted rows in Produits** (`ProductCard`, `ProductGridCard`), which skip when their product is unchanged.
- **Photos out of the database**, with Coil downsampling to the view size and caching across screens. No full-size decode
  anywhere in composition.
- **Deferred reads done right:**
  - The drawer's translation is set in a `graphicsLayer` lambda (`MainActivity.kt:487`).
  - The Dashboard's week chart reads its animated heights inside the `Canvas` draw (`DashboardScreen.kt:289-293`, through
    `glidingValues`, `Figures.kt:136-150`).
  - The commune bars use `progress = { rate }` (`DistributionSection.kt:321-324`).
- **Restrained animation:** only on a change, never on first display, lists or forms. Tabular digits so counting amounts
  don't shake. No infinite transitions, no blur.
- **Cheap theming:** design tokens are constants (`object DsColors`). `LocalMoneyFormatter` and `LocalDrillDown` are static
  locals provided once, and the drill lambda is remembered.
- **Search and filters:** a 300 ms debounce on Produits and the pickers. Filters memoized with `remember(keys)`. The five
  `LaunchedEffect(products)` restarts from the last audit are gone.
- **Dashboard:** blocks load apart, debounced 400 ms, in a keyed `LazyColumn`.
- **No custom fonts** to load, no large `rememberSaveable` state, strong skipping on.
- **Freeze detection:** `MainThreadWatchdog` and debug StrictMode catch multi-second stalls. Frame drops need Step 0.

---

## Roadmap to 60 FPS (and 120)

**Definition of done**, on the M34 in the `benchmark` build, for each step of the walk:
- flings and swipes: ≤ 2 % janky frames (Android's deadline-missed count in `dumpsys gfxinfo`);
- typing: no slow UI-thread frame per key, and no frame over 2 refreshes (16.7 ms at 120 Hz). Not the janky count: with
  the keyboard up it counts the cursor's blinks, late on every screen (see M2);
- opening a screen: no frame over 50 ms;
- no frame over 100 ms anywhere: no freezes.

*(Corrected after Step 0. The first version also asked for a 95th-percentile frame time under 8.3 ms. That is the wrong
yardstick: gfxinfo times a frame from its vsync until the GPU finishes, and with buffering a 10–12 ms frame still meets its
deadline. The benchmark flings above show a median of 10–11 ms with 0.3 % janky.)*

| Step | What | Files | Effort | Expected gain | Verify with |
|---|---|---|---|---|---|
| **0** ✅ | **Done 2026-10-07; results at the top.** **Measure on a build that represents users.** A `benchmark` build type (below). Run a fixed walk: cold start → Produits fling → Dépôt Vente fling → open a sale → back → client detail tab swipes → product form tab swipes → Ventes filter → Client dropdown → type in Clients search → open a report. Record `gfxinfo` per walk. Turn on Compose compiler reports. | `app/build.gradle.kts` | ½ day | Real numbers; shows how much was the debug build | The baseline table |
| **1** ✅ | **Done 2026-10-08; results at the top.** **R8 + Baseline Profile.** Minify and shrink resources; keep rules for the Gson-serialized classes (drafts' `items_json`, the wilaya file). Re-test backup, restore, import and print. `androidx.baselineprofile` plugin + a macrobenchmark module generating the profile from Step 0's walk. | build files, `proguard-rules.pro`, new `:baselineprofile` module | 1–2 days | The largest single gain, everywhere: first frames of slides, every new list row, cold start | Same walk, compare |
| **2** ✅ | **Done 2026-10-07; see C2.** **The dropdown freezes** (C2) → `SearchableSelectSheet` | `VentesScreen`, `MovementFiltersSheet`, `PurchasesScreen`, `TourneesScreen` | ½ day | Removes the only multi-hundred-ms freezes | Longest frame on opening the filter |
| **3** | **Tab swipes** (C3): offset read in layout/draw; no animated shadow, no off-screen alpha | `ProductFormScreen`, `ProductDetailScreen`, `ElasticUnderlineTabRow` | ½–1 day | The swipe costs only the pager's own work per frame | Layout Inspector recomposition counts stay flat during a swipe |
| **4** | **Pending-sale rows** (C4): band only while dragging, no coroutine per row, dates formatted once, `contentType` | `SwipeToConfirm`, `VentesScreen`, `PurchasesScreen`, `TourneesScreen`, paging row mapping | 1 day | Cheaper rows in the most-flung lists | Janky % on the Dépôt Vente / Achats fling |
| **5** | **Pickers** (C5): remembered `itemKey`, one shared `PickerProductRow`, cart id set, `contentType` | `VenteFormNavGraph`, `TourneeVenteFormNavGraph`, `PurchaseFormNavGraph` | 1 day | Typing and adding products stop re-running every visible row | Recomposition counts while typing |
| **6** | **Images** (M1): no disk check in composition, size up front, remembered request, placeholder hidden in draw | `EntityImage.kt`, `EntityAvatar.kt` | ½ day | No blink, one composition per photo row | Slow-motion screen capture of a fling |
| **7** ✅ | **Done 2026-10-08: Clients, debtors, Produits (see M2).** **Search off Main** (M2): Clients and Debtors debounced and computed on Default; Produits search read isolated | `ClientsScreen`/VM, `DebtReportViewModel`, `DebtorListQuery`, `ProductsScreen` | 1 day | Smooth typing on 1,500 clients | Frame times while typing |
| **8** | **Client/supplier flows** (M3): `flowOn(Default)`, by-id observation in detail, form and sale detail | `ProductRepository`, `ClientsNavHost`, `SuppliersNavHost`, `VentesScreen` | ½ day | No full-table work on Main after a sale or payment | Main-thread time after a payment (Perfetto) |
| **9** | **Shell** (M4, M5): drawer via `derivedStateOf` + `drawBehind` scrim; no root `BoxWithConstraints`; fade for tab ↔ tab | `MainActivity`, `NavTransitions` | ½ day | Smooth drawer, keyboard and tab switch | Janky % on drawer drag and tab switches |
| **10** | **Animation polish** (M6, M7, M9): one-pass FitText, `AnimatedFigure`'s `widest` remembered, `StackedBar` in draw, chevron in `graphicsLayer`, history grouping in the VM | `FitText`, `Figures`, `DistributionSection`, `CartSelectionComponents`, Charges/Pertes VMs | 1 day | Reports open and change filters without a hitch | Report walk |
| **11** | **Hygiene** (M8, M10): lifecycle-aware collection; row UI models; Compose BOM upgrade, re-verifying the swipe-once guard | across `ui/` | ongoing | Less background work; future-proofing | — |

**Why this order:** Steps 0–1 move every number, so later gains are measured against the right baseline. Steps 2–5 are the
spots felt most often (filters, swipes, the main lists, the sale form). Steps 6–11 are polish.

### Step 0, concretely

```kotlin
// app/build.gradle.kts, android { buildTypes { … } }
create("benchmark") {
    initWith(getByName("release"))
    signingConfig = signingConfigs.getByName("debug") // installs over the debug build, keeps the sandbox data
    matchingFallbacks += listOf("release")
    isDebuggable = false
}

// Compose compiler reports: which composables skip, which params are unstable
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}
```

```bash
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
adb shell dumpsys gfxinfo com.distrigo.app reset
# … one walk on the phone …
adb shell dumpsys gfxinfo com.distrigo.app | grep -E "Total frames|Janky frames|percentile"
```

`BuildConfig.DEBUG` is false in this build, so the debug-only StrictMode and the stress-data buttons are off. The stress data
already in the database stays. Install with `adb install -r` only: never `connectedAndroidTest`, which uninstalls the app.

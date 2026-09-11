# Dépôt Vente — Brouillons (Phases 1–13)

**Date:** 2026-09-03 · last updated 2026-09-11
**Scope:** the Draft/Brouillon feature for Dépôt Vente, plus the shared machinery extracted out of Achats to support it — and, since Phase 12, the two further flows built on that machinery: Tournée Vente and Stock camion. All four flows now have Brouillons.
**Status:** Complete and merged to `main`. Phase 5 (verification) found two defects; Phase 6 fixed them. Phase 7 fixed the two cosmetic card bugs Phase 5 had left open. Phase 8 closed the last untested item on the list — the sheet beyond four drafts — and found the sheet had been opening at the wrong height all along. Phase 9 added filter and multi-selection. Phase 10 closed the last untested path — a draft referencing a deleted product — and found six defects behind it, none of them a data risk and all of them the app misinforming the user. Phase 11 finally exercised the sheet's scroll guard, by font scale rather than by screen size, and closed the last open item that was ever testable here. Phases 12 and 13 took the shared machinery to its third and fourth consumers — Tournée Vente and Stock camion — which is what it was extracted for; between them they needed one new parameter on one shared component, and no change to the autosave at all.

| Phase | Commit | State |
|---|---|---|
| 1 — shared draft machinery | `43cc4f4` | merged |
| 2 — Vente draft storage | `a65a712` | merged |
| 3 — Vente form session | `f8e8709` | merged |
| 4 — Brouillons surfaced in the UI | `96788f6` | merged |
| 5 — full device verification | — | run; two defects found |
| 6 — both defects fixed | `0008048`, `a090fbd` | merged |
| 7 — card cosmetics fixed | `34fd840`, `6652bf9` | merged |
| 8 — sheet height and meta line | `fd0545d` | merged |
| 9 — filter and multi-selection | `2437778`, `c2bc82d` | merged |
| 10 — the deleted-product block | `5fb56c9` | merged |
| 11 — the sheet's scroll, at font scale 2.0 | `f914290` | merged |
| 12 — Brouillons for Tournée Vente | `dc94aab` | merged |
| 13 — Brouillons for Stock camion | `71a6a42`, `30dbe8f`, `b26f99e`, `0ca531b` | merged |

---

## 0. Summary

Dépôt Vente now keeps a Brouillon of an unfinished sale, exactly as Achats does for a bon: autosaved while you work, restored silently after a process kill, resumable from a FAB sheet or its own screen, deleted inside the same transaction that commits the sale, and guarded by a conflict check when the underlying vente moved.

The three genuinely repeated pieces — the fingerprint hashing core, the autosave loop, and the Brouillon card/sheet/dialog UI — were extracted first, so Chargement and Tournée-Vente can reuse them rather than becoming a third and fourth copy.

---

## 1. Decisions taken

| # | Decision | Consequence |
|---|---|---|
| 1 | **Delivered ventes stay editable.** Only `DELETED` blocks. | `resolveBaseState` has no `RECEIVED` branch; the list query asks *which ventes still exist* rather than reading a status. Justified because `ProductRepository.updateVente` reverses every old line's stock before applying the new ones, so a delivered vente is still safely editable. |
| 2 | **`ClientsNavHost` "Nouvelle facture" is in scope** — drafts can be created and resumed there, but Brouillons are listed only under Ventes. | No code needed in `ClientsNavHost`: the client page offers no edit entry, so there is no second edit path to gate. Drafts started there are ordinary new-vente drafts and appear in the Ventes Brouillons list. |
| 3 | **Do the generic refactor now** rather than duplicating and consolidating later. | Phase 1. Achats was re-verified end to end afterwards to prove no behaviour changed. |
| 4 | **Separate `vente_drafts` table**, not a shared one with `purchase_drafts`. | A bon carries a supplier and a colis/expiry breakdown per line; a vente carries a client and the name of whoever made the sale. One wide table would be half-null in both directions, and would have meant migrating a table that had shipped hours earlier. |

---

## 2. Phase 1 — the shared machinery (`43cc4f4`)

Net **−322 lines** in Achats-specific code. No behaviour change.

* **`DraftFingerprint`** reduced to the hashing core: `of(canonical: String)` (SHA-256) and `num(v: Double)` (`Locale.ROOT`), plus the rule every domain's canonical builder must follow. The Achats canonical string moved to **`PurchaseFingerprint`** *character for character*, so fingerprints already recorded against a bon stay valid.
* **`DraftAutosave` / `DraftAutosaveHost`** own the `combine → drop(1) → filter { armed } → debounce(500) → persist` loop, the `ON_STOP` flush, and the dirty rule — fingerprint against base for an edit, emptiness for a new record, and delete the row again when an edit is undone back to its original values.
* **`DraftComponents.kt`** holds `DraftCardUi`, `DraftRow`, `DraftsSheet`, `DraftConflictDialog` and a generic `draftResumeAction<D>`, with `DraftSheetCopy` / `DraftConflictCopy` carrying the words.
* **Stale KDoc fixed** on `DraftFingerprint`: it still described the pre-F14 prefill discarding `nb_colis`/`unite_par_colis`/`has_expiry`.

### Achats regression after the refactor — all PASS

| # | Scenario | Result |
|---|---|---|
| R1 | Untouched edit of bon #11 → **no draft** | PASS |
| R2 | Dirty the note → draft with `source_order_id`, base fingerprint recorded | PASS |
| R2b | Undo back to the original → **draft deleted again** | PASS |
| R3 | Back out → `am force-stop` → relaunch → resume | PASS — silent, note and `2 colis × 12 = 24 pièces` restored |
| R4 | Brouillons screen: new-purchase card + delete dialog | PASS |
| R5 | FAB drafts sheet | PASS — renders identically |
| R6 | Commit an edit → draft deleted | PASS |
| R7 | Bon mutated underneath its draft → **"Le bon #11 a changé"** | PASS |
| R7b | "Repartir du bon actuel" → draft deleted, form re-prefilled from the current bon | PASS |

---

## 3. Phase 2 — the storage layer (`a65a712`)

`VenteDraftEntity`, `VenteDraftDao`, `VenteDraft` models, `VenteFingerprint`, `VenteDraftRepository`, migration 33→34 with exported schema. `DraftBlock`/`DraftBaseState` moved out of `PurchaseDraft.kt` into `DraftState.kt` and reworded for any flow.

### Three fingerprint details that are not obvious

1. **`user_name` is hashed, and the committed side is always empty.** "Effectué par" is written onto the **stock movements**, not onto the `ventes` row, so a committed vente has nothing to prefill it back from. Both sides agreeing on `""` is what matters: an untouched edit stays clean, typing a name is a real change, and a resume gives it back.
2. **The cart line's product snapshot is excluded.** `VenteCartItem` carries a `Product` whose stock has this sale's own deduction re-added for display, and that number moves whenever anything else in the app touches stock. Hashing it would make every parked draft read as conflicted the moment an unrelated sale went through. Only `product_id`, `quantity`, `unit_price` are hashed.
3. **`originalReservedQty` is not persisted.** It is derived from the committed vente, which may have moved while the draft sat unsaved, so a resume re-derives it rather than trusting a stale copy.

### Migration verification

* Migration SQL **diffed programmatically** against Room's generated `34.json` — table and index match byte for byte. Identity hash `4001e9baa761802df6de9e9945fc3356` recorded in the KDoc.
* Live database backed up before installing.
* Run against the real database: `user_version` 33 → 34, both objects created, **no Room validation error and no crash** — a clean open is itself Room confirming the result matches what it expects.
* **Data intact:** orders 13, ventes 21, products 34, clients 18 — identical to the pre-migration inventory.
* **Unique index checked, not assumed:** two new-vente drafts with a null `source_vente_id` coexist; a second edit draft for the same vente fails with `UNIQUE constraint failed`.

---

## 4. Phase 3 — the form session (`f8e8709`)

`VenteFormSessionViewModel`, scoped to the form graph's back stack entry, owns the five form fields and the session lifecycle. Three pieces moved out of the graph:

1. **The duplicated entry logic.** `resetVenteForm()` behind a `rememberSaveable("initialized")` flag plus two client-resolution effects existed **twice** — once on the client step, once again on the products step because `skipClientStep` means the client step is never entered. That flag survived process death while the `VenteViewModel` it described did not, so after a kill it claimed setup was done against a form that had been reset.
2. **The edit prefill**, which ran off whichever destination composed first — not guaranteed by a process-death restore — and which must stay in step with the fingerprint's mirror of it.
3. **Re-deriving `originalReservedQty`** from the committed vente. The session publishes `editSource` so the products and cart steps re-apply it the same way when they re-sync.

`createVente`/`updateVente` take an optional `draftId` and delete it as the **final statement inside their transaction**, as the Achats commit path already does — so a failure anywhere in the insert, the stock deltas or the balance recalculation rolls back and leaves the unsaved work intact.

### Results — all PASS

| # | Scenario | Result |
|---|---|---|
| D1 | Empty new form → no draft; pick a client → draft created (`source_vente_id` NULL) | PASS |
| D2 | Add a product → 1 item, 80.00, line JSON in `VenteDraftLine` shape | PASS |
| D3 | Type a note → carried into the draft | PASS |
| D4 | **Real process death** (`am kill`, pid gone) → relaunch | PASS — same step (Produits · 2/3), client in the header, product selected, "Ma sélection 1 — 80.00 DA", silent |
| D6 | Confirm the sale → vente created, stock 8→7, **draft deleted in the same transaction** | PASS |
| D7 | Reopen it as an untouched edit → **no draft** | PASS — fingerprint symmetry holds, including the empty `user_name` |
| D8 | Dirty the note → draft with `source_vente_id` and a base fingerprint | PASS |
| D8b | Undo back to the original → draft deleted again | PASS |
| D9 | Back out of the graph → draft persists; the vente on disk unchanged | PASS |

---

## 5. Phase 4 — Brouillons in the UI (`96788f6`)

`VenteBrouillonComponents.kt` (card title/meta, blocked label, sheet and conflict copy, `venteDraftResumeAction`) and `VenteBrouillonsScreen.kt`, both thin over the Phase-1 generics. `VenteViewModel` gained the Room-observed `drafts`/`draftCount`, `draftForVente`, `resolveDraftBase`, `deleteDraft`. `VentesScreen` gained the chip and the FAB sheet. `VentesNavHost` gained `openVenteFormAction`, the `editVenteAction` gate and the `VentesBrouillons` destination.

### Results — all PASS

| # | Scenario | Result |
|---|---|---|
| P1 | Pick a client → draft created; empty form creates none | PASS |
| P2 | "Brouillons (1)" chip appears beside "7 ventes" | PASS |
| P3 | Chip → Brouillons screen, new-vente card (client as title, "0 produit · à l'instant") | PASS |
| P4 | Tap the card → form opens with the client restored, **no second draft** | PASS |
| P5 | FAB → sheet with Vente wording ("…commencez une nouvelle vente") | PASS |
| P6a | Edit vente #17, bump a quantity → edit draft (`source_vente_id = 17`, 900.00) | PASS |
| P6b | "Modifier la vente" again → **resumes** that draft, no second row | PASS — the gate works |
| P7 | Vente #17 mutated underneath → **"La vente #17 a changé"**, both options | PASS |
| P8a | Blocked draft in the list → danger style, **OBSOLÈTE** badge, "Vente supprimée" | PASS |
| P8b | Resume it → **"Vente introuvable"** dialog | PASS |
| P9 | Both drafts deleted through the UI | PASS |

---

## 5b. Phase 5 — full device verification

Run against `main` after a clean rebuild. Covers what Phases 3–4 had not exercised end to end.

| # | Scenario | Result |
|---|---|---|
| A1 | Achats list after the Phase 3–4 changes (shared `SessionPhase`, `ProductRepository` signatures) | PASS — 13 bons, chip, all data intact |
| A2 | Achats untouched edit → no new draft | PASS — session still correct |
| B1 | Client page → "Nouvelle facture" → opens at Produits with the client preselected | PASS |
| B1b | Add a product on that entry → draft written (`last_step = clients_vente_form_products`) | PASS |
| B2 | Type, then Home immediately → **ON_STOP flush** writes the draft | PASS |
| B4 | Resume a draft → confirm the sale → vente created, **draft deleted in the same transaction** | PASS |
| B6 | CHANGED → **"Repartir de la vente actuelle"** → draft deleted, form re-prefilled from the *current* vente (50.00, empty note) | PASS |
| B5 | **End-to-end DELETED**: edit → back out → delete the vente → open Brouillons | **FAIL — see Defect 1** |
| B5b | Resume that draft anyway → "Vente introuvable" dialog | PASS — the gate is not stale |

### Defect 1 — the block badge did not refresh when the source record changed

Deleting a vente left its edit draft rendering as a **normal** edit card: no OBSOLÈTE badge, no "Vente supprimée". Leaving and re-entering the screen did not fix it (the ViewModel is graph-scoped and `stateIn(WhileSubscribed(5_000))` keeps the upstream alive).

**Cause.** `observeDrafts()` observed `dao.observeAll()`, a Room query on `vente_drafts` only. `existingVenteIds(...)` was called inside `.map { }`, so it was not part of the observed query — Room's invalidation tracker never saw a change to `ventes` and the flow never re-emitted. `PurchaseDraftRepository.observeDrafts()` had the identical structure, so Achats carried the same latent defect.

**Severity: cosmetic, not a data risk.** `resolveBaseState` is a fresh suspend query at resume time, so the gate still blocked correctly — verified: the stale-badged draft still raised "Vente introuvable" on resume (B5b). Nothing could be overwritten; the list just told the truth late.

**Note on how it was missed:** Phase 4's P8a "passed" because it simulated the deletion by patching the database and **restarting the app** — a fresh collection recomputes the badge. Only the end-to-end path, with the app running, exposes it. This is exactly the gap a simulated divergence leaves.

**Fix.** Both DAOs gained an *observed*, unfiltered query — `observeVenteIds()` / `observeOrderStatuses()` — and each repository now `combine`s it with `observeAll()` instead of doing a suspend lookup inside the mapping. Unfiltered rather than `WHERE id IN (:ids)` is the point: Room re-runs a Flow only when a table that query itself reads is written.

**Verified live**, with the app running and no restart: with a draft on bon #11, marking the bon received flipped its card to OBSOLÈTE / "Bon déjà réceptionné"; reopening the bon flipped it straight back to a normal edit card.

### Defect 2 — a Brouillon was created before anything about the bon had been entered

**The reported scenario:** ACHATS → Nouveau bon → select a supplier → press Back. No product, no quantity, no note, no amount. A Brouillon was created anyway.

**Reproduced:** `purchase_drafts` went 0 → 1 (`item_count 0`, `total 0.0`, empty note, empty `montant_paye`) **the instant the supplier was selected** — before Back was involved at all.

**Cause.** `DraftSnapshot.isEmpty` was `supplierId == null && lines.isEmpty()`, so a chosen supplier alone made the snapshot non-empty. Selecting one writes `_formSupplier`, the autosave `combine` emits, the session is already armed, and `persist` reads `dirty = !isEmpty` → INSERT.

**Fix.** The supplier is no longer counted. Choosing one is the *precondition* for entering anything about the bon — the form refuses to go further without it — not content in itself:

```kotlin
val isEmpty get() = lines.isEmpty() && note.isBlank() && montantPaye.isBlank()
```

The same rule was then applied to `VenteDraftSnapshot`, with one deliberate difference: **`userName` does count there.** "Effectué par" is written onto the stock movements rather than onto the `ventes` row, so a draft is the only place it can survive — unlike the client, which the form re-derives on every entry.

**A second, independent contributor**, found while investigating: the `ON_STOP` flush would write a form nobody had touched since arming — that is how arriving from a *supplier page* and backing straight out left an empty draft. `DraftAutosave` now tracks `touched`, set on the first armed emission and checked by `flush()`. Being armed is not the same as having something to save: a session can be armed over a form populated *before* arming — an edit prefill, a hydrated draft, or a record chosen on the page the form was opened from — and none of that is the user entering anything.

**Verified on both flows** — supplier/client selected for real (screenshots confirm the green check and an enabled "Suivant"):

| Check | Achats | Dépôt Vente |
|---|---|---|
| Select supplier / client only | 0 drafts | 0 drafts |
| Press Back | 0 drafts | 0 drafts |
| Add a product | 1 draft | 1 draft |
| Remove it again | 0 drafts | 0 drafts |

### Correction to this section's earlier text

An earlier version of this document reported Defect 2 as "the preselected client/supplier is inconsistent between the two flows", and attributed it to `preselectClient` running before `arm()` in Vente and the graph applying `supplierIdArg` after arming in Achats.

**That diagnosis was wrong, and the evidence behind it was unsound.** The comparison sampled two different moments — Vente was checked *while still in the form*, Achats *after backing out* — so it was never a like-for-like test. Moving the Achats preselect before `arm()` did not fix the reported behaviour, which is what exposed the error. The real causes are the two above: an emptiness rule that counted the supplier, and a flush that wrote untouched forms.

The entry path that actually mattered — ACHATS → Nouveau bon → select a supplier — was never exercised in Phase 5 at all. The path that was tested, "Nouvel achat" from a supplier page, reaches the form a different way and hid the emptiness rule behind the flush.

---

## 6. Disclosures

* **P8 used a simulated divergence.** For the `DELETED` case a draft was repointed at a nonexistent vente (`source_vente_id = 9999`) rather than deleting a real sale. It exercises the same `resolveBaseState` branch and the same badge query, but it is not an end-to-end delete.
* **R7 also used a simulated divergence** (bon mutated directly in SQLite), because the app offers no UI route to change a bon that already has a pending edit draft — which is the situation the dialog exists for.
* **A correction to an earlier claim.** `last_step` recording the picker route was reported as a Phase 4 bug. It is not: `last_step` is written but **never read for navigation** in either flow — a resume always opens at the graph's start destination. The comments that promised otherwise (and that called it "the deepest step reached", when backing out overwrites it with a shallower one) were corrected instead. Resume-to-step navigation was **not** built.

---

## 7. Open items

1. ~~Bidi rendering in the card meta line.~~ **Fixed in `34fd840`** — see §9 below.
2. ~~Title truncation when a blocked draft also has a price.~~ **Fixed in `6652bf9`** — see §9 below.
3. ~~Sheet with more than 4 drafts.~~ **Tested and fixed in `fd0545d`** — the overflow link was correct; the height the sheet opened at was not. See §10 below.
4. ~~`missingProductIds` blocking.~~ **Tested and fixed in `5fb56c9`** — the path worked on Achats but could not be cleared, and did not exist at all on Dépôt Vente. Six defects in total; see §12 below.
5. ~~The sheet's `verticalScroll` has never run.~~ **Exercised and confirmed in Phase 11** — no device here is short enough, but `font_scale` 2.0 reaches the same condition. The scroll works; the test also found the sheet's action label clipped at that scale, fixed in `f914290`. See §13.
6. ~~The Chargement and Tournée-Vente draft flows.~~ **Both built** — Tournée Vente in Phase 12 (`dc94aab`, §14), Stock camion in Phase 13 (`b26f99e` and friends, §15). Each carries its own unverified items, listed in its section rather than here.
7. **Not started, deferred:** Notifications, WorkManager.

---

## 8. Device state after testing

Everything restored to the pre-test baseline:

| | |
|---|---|
| `vente_drafts` | 0 |
| `purchase_drafts` | 0 |
| ventes | 21 |
| purchase orders | 13 |
| product 31 stock | 8.0 |
| vente #17 | total 750, `montant_paye` 0.0, note empty, status `pending` — fully restored |

Test records created during the run (vente #22, and the Achats test bon's mutations) were removed or reverted through the app's own paths where possible. No crash lines in logcat; screen timeout restored.

---

## 9. Phase 7 — the two cosmetic card bugs

Both were found during Phase 5, recorded unfixed, and fixed here. Both are cosmetic: no draft was ever lost or misapplied by either.

### Bidi reordering in the meta line (`34fd840`)

A meta line joins a name to French text with `·` separators. Those separators are bidi-*neutral*, so the algorithm resolved them against the strongest neighbour — the RTL name — rather than against the LTR paragraph, and pulled the digit after the separator into the name's run:

| | |
|---|---|
| intended | `جلاال · 4 produits · il y a 3 h` |
| rendered | `4 · جلاال produits · il y a 3 h` |

The count was torn away from the noun it counts. Most clients and suppliers in this database have Arabic names, so this was the *normal* rendering, not an edge case.

The fix wraps the name in isolate marks — `U+2068` FIRST STRONG ISOLATE and `U+2069` POP DIRECTIONAL ISOLATE — via a new `ui/common/BidiText.kt`. The name's own direction is still resolved from its first strong character, so it renders right to left internally; it just presents itself to the surrounding run as one neutral object and stops moving anything else. Both marks are zero-width, so no width changes.

Applied in both flows' `cardMeta`, and in the name-only `cardTitle` branch as well — that string is interpolated into a French sentence in the delete confirmation dialog.

### The OBSOLÈTE badge eating the record number (`6652bf9`)

An edit draft's title ends in the record number, which is the only thing that distinguishes two of them. On a blocked draft that *also* had a price, it was ellipsised away.

The width budget on the 360dp screen this was found on:

| | |
|---|---|
| screen | 360dp |
| less screen padding (16 × 2) and card padding (12 × 2) | 304dp |
| less the leading icon (38) and two 12dp gaps | — |
| less the trailing price (~72) and delete button (48) | **122dp for the text column** |
| less the "OBSOLÈTE" pill and its spacer | **62dp for the title** |

62dp is about 18 characters over two lines, against a 22-character title — which is exactly the observed `Modification ·…`. The two-line title added in Phase 4 could not save it, because the pill narrowed both lines.

The pill was removed rather than relocated. It was the fourth element on the card carrying the same information: the leading glyph is already a red warning triangle in a red container, and the line beneath already names the *cause* — "Bon déjà réceptionné", "Vente supprimée" — which says more than "OBSOLÈTE" did. That line now carries the state alone, in semibold. Relocating the pill to the meta line was considered and rejected: at 122dp it would have truncated the reason instead, trading one lost string for another.

### Verification

On device, against the worst case in each flow:

| Case | Result |
|---|---|
| Achats draft on **received** bon #14, 1 200,00 DA | Title `Modification · bon #14` in full; `Bon déjà réceptionné` — the longest reason string — untruncated |
| Dépôt Vente draft on **deleted** vente #99, 1 250,00 DA | Title `Modification · vente #99` in full; `Vente supprimée` untruncated |
| Achats draft on bon #13, supplier بوجمعة | `بوجمعة · 3 produits · il y …` — correct order |
| Dépôt Vente draft on vente #5, client جلاال | `جلاال · 4 produits · il y a…` — correct order |

Checked in both surfaces that render a card: the Brouillons screen (narrow column — price and delete button present) and the FAB sheet (wide column — chevron only). In the sheet both titles fit on one line and the meta line renders in full.

**Device state:** the four test drafts were inserted directly into a copy of the database, and the pre-test snapshot was restored afterwards. Back to `vente_drafts` 0, `purchase_drafts` 0, ventes 21, orders 13. No crash lines in logcat; screen timeout restored.

---

## 10. Phase 8 — the sheet at full height, and the meta line (`fd0545d`)

Open item 3 — the sheet beyond four drafts — became testable once 6, then 10, real drafts existed. The overflow link itself was correct. The sheet around it was not.

### What the test found

With 6 Achats drafts the preview capped at `MAX_SHEET_ROWS` = 4 as designed, "Voir tout (6)" appeared, tapping it opened the full Brouillons screen, and all 6 were reachable and resumable there. Navigation and ordering were sound.

But the sheet opened at Material3's partial detent, which is exactly half the screen — 390dp of the 780dp measured here — while the content runs past 500dp once four rows are in it. The fourth row was cut through the middle, and both "Voir tout" and "Commencer un nouveau bon" sat below the fold with nothing on screen to say they were there. Every action that was not "resume one of the first three drafts" needed a drag first.

### A correction to my own framing

I first recorded this as a problem that starts at **4 or more** drafts, and it was accepted and specified as one. It is not. **Three rows already overflow the detent.** Half the screen was never the right size for this sheet — it was wrong at three and merely more obviously wrong at four.

So the fix is not a taller detent for long lists but `skipPartiallyExpanded` for every list: the sheet opens at its content height whatever the count. The height still has a ceiling, because the content is capped at `MAX_SHEET_ROWS` rows however long the draft list grows.

### The meta line

Separately, the Brouillons screen passed the amount into `DraftRow`'s trailing slot, where it spanned both lines and so narrowed the meta line on **every** row. Against a four-figure total, "3 produits · il y a 3 min" no longer fit.

The amount moved onto the title line, which has two lines and room to spare, leaving the meta line the whole column. The title takes `Modifier.weight(1f)` — filling the row rather than hugging its text — so every row's amount lands on the same right edge. An earlier attempt used `weight(1f, fill = false)` and produced ragged amounts.

That leaves a blocked draft's title 102dp over two lines against a 22-character worst case, against the 62dp Phase 7's pill had left it.

### Verification, and what was not verified

Verified on device against 10 Achats drafts, including a five-figure 77 040,00 DA: the sheet opens complete with no drag, all ten rows show their meta line in full, and the list scrolls to the last card.

**Not re-verified on device:** Dépôt Vente, which shares `DraftRow` but had no drafts to show at the time, and the blocked-draft title, which no current draft produces. Both are arithmetic-safe by the budget above.

**A new untested path was added, deliberately.** The `verticalScroll` on the sheet's Column covers what the row cap does not — a screen short enough, or an accessibility font scale large enough, that even the capped content overflows. It exists so the fix does not trade "must drag" for "cannot reach". On this hardware it has never run, and cannot be made to. Recorded as open item 5.

---

## 11. Phase 9 — filter and multi-selection (`2437778`, `c2bc82d`)

Both Brouillons screens gained a filter strip and a selection mode. As in Phase 1 the machinery was written generic and put in `ui/common` beside the draft card and sheet — `DraftFiltering.kt` and `DraftSelection.kt` — so Achats and Dépôt Vente share it rather than becoming a fourth and fifth copy. The flows differ only in wording ("Fournisseur" vs "Client") and in which facts a draft reports through `DraftFilterFacts`.

### Filter

A "Filtres ▾" pill under the top bar opens the same "Filtres avancés" sheet the Achats and Ventes lists already use — segmented rows for the closed sets, a dropdown for the open one, Réinitialiser / Appliquer at the foot with the resulting count on the button. Three axes:

| Axis | Values |
|---|---|
| type | tous / nouveaux / modifications |
| état | tous / actifs / obsolètes |
| fournisseur *or* client | tous, then the names actually present |

Two decisions worth recording:

* **The name picker offers only names present among the drafts.** Listing every supplier in the database would be mostly dead options that filter to nothing.
* **Filtering is by name, not by id.** The name is what a draft stores, and a draft may carry no id at all before its first step is done.

`DraftFilterState` saves through a `listSaver`, so a filter survives rotation and process death.

### Selection

"Sélectionner" swaps the top bar for a contextual one (count, close) and docks a bulk action bar at the bottom. A row's leading glyph becomes a checkbox, a tap ticks instead of resuming, and the per-row delete button goes — the destructive control is never left adjacent to the one that merely opens something. `DraftRow` carries all of this on one nullable parameter, `selected: Boolean?`: non-null puts the row in selection mode, null is the ordinary row that every other caller gets.

"Supprimer" sits at the far end of the bar from "Tout sélectionner", those being the two actions most costly to confuse, and the count rides on the button label so it always states its own blast radius. Back leaves selection before it leaves the screen.

### The two dialogs

Deleting some of the drafts is an ordinary destructive confirm. Deleting *all* of them gets a heavier one — warning glyph, its own title, and "Tout supprimer" rather than "Supprimer" — because that is the case with nothing left to recover from, and it is one mis-tap away from "Tout sélectionner". Its wording says "de cette liste", which stays honest when a filter is applied.

### Correctness

The selection is intersected with what is on screen on every pass:

```kotlin
val shown        = remember(drafts, filter) { drafts.filter { filter.accepts(it.filterFacts()) } }
val visibleIds   = remember(shown) { shown.map { it.id }.toSet() }
val effectiveSel = remember(selected, visibleIds) { selected intersect visibleIds }
```

so a draft that vanishes underneath it — committed elsewhere, or deleted — can neither be deleted by a stale id nor leave the count outrunning the list.

The bulk delete is one statement, so Room invalidates the table once and the whole selection goes or none of it does:

```kotlin
@Query("DELETE FROM purchase_drafts WHERE id IN (:ids)")
suspend fun deleteByIds(ids: List<Int>)
```

**Nothing about persistence, navigation or ordering moved.** Filter and selection are screen state, the drafts flow is untouched, and both are gone with the screen.

### Verification

On device, against 10 Achats and 10 Dépôt Vente drafts:

| Check | Result |
|---|---|
| Filter by type | 0 of 10 — empty state renders |
| Filter by supplier | 3 of 10 |
| Tout sélectionner | every row ticked, label flips to Tout désélectionner |
| Partial confirm dialog | ordinary register, count in the title |
| Full confirm dialog | warning glyph, "Tout supprimer" |
| Real bulk delete | exactly the 2 selected rows removed, no others |
| Back while selecting | leaves selection, stays on the screen |
| Per-flow wording | "Fournisseur" on Achats, "Client" on Dépôt Vente |

Test data restored afterwards.

### A process note (`c2bc82d`)

The two DAOs were edited by a script that did not preserve line endings and flipped them to CRLF, turning a 9-line addition to each into a 74-line whole-file diff. The repo stores LF and `core.autocrlf` is `true`, so any rewrite that ignores endings will do this again. Corrected in a separate `style:` commit; the net change across the two commits is exactly the new method.

---

## 12. Phase 10 — the deleted-product block (`5fb56c9`)

Open item 4 — a draft referencing a since-deleted product — was the last untested path on the feature. Testing it end to end found **six** defects, two of them substantive. Nothing was ever corrupted by any of them; every one was a matter of the app telling the user the wrong thing, or nothing.

### How it was tested

Throwaway products (`ZZ_ACHAT`, `ZZ_VENTE`) were created through the UI, put into a draft alongside a real product, and then deleted through the app's own delete path — no simulated divergence, unlike Phase 5's P8. Each draft was then resumed, corrected, and **completed**, and the resulting records were removed afterwards.

### Defect 1 — Achats told the user to remove the line, then ignored them

The block itself was right: the line survived under its stored name, the red status line named the cause, the confirm button was dead, and tapping it created nothing. Then the user did the one thing the message asked — and the button stayed dead.

**Cause.** `_missingProductIds` was written only by `hydrate()` and cleared only by `resetForm()` / `onCommitted()`. `setFormCartItems` never touched it, so removing the offending line left its id in the set forever.

**Severity: no data risk, but a dead end.** The draft on disk was already correct — 1 line, 80 DA, no missing product — while the form stayed un-saveable. The only escape was to leave the form entirely and resume the draft, which nothing on screen suggested. Proven rather than inferred: after the removal the button was still grey; after backing out and resuming the same draft it was green.

**Fix.** Both session ViewModels prune the set in `setFormCartItems`:

```kotlin
private fun pruneMissingProducts(presentIds: Collection<Int>) {
    val missing = _missingProductIds.value
    if (missing.isEmpty()) return
    _missingProductIds.value = missing intersect presentIds.toSet()
}
```

Pruning only, never adding. Discovering that a product is missing takes a live catalogue lookup, which is `hydrate`'s job; this only lets the block lift.

### Defect 2 — Dépôt Vente had no gate at all

`VenteFormSessionViewModel` computed `missingProductIds` exactly as Achats did, and **nothing read it**. The confirm button stayed enabled.

Pressing it threw `IllegalStateException("Produit introuvable: …")` from inside `createVente`'s `db.withTransaction`. Verified on device: ventes stayed at 21, the draft survived, no stock movements were written. The transaction is atomic, so this was never a data risk — but the button appeared to do nothing whatsoever, because of Defect 4 below.

**Fix.** The same block Achats has: `missingProductIds.isNotEmpty()` guards `doSave`, and `hasMissingProducts` disables the button.

### Defect 3 — Dépôt Vente reported the wrong problem

A deleted product's placeholder carries stock 0, so the rupture branch fired and the cart line read **"Rupture — dépassement de 1 carton"** — telling the user to restock something the catalogue no longer holds. Advice that cannot be followed, on the one line that most needed to be understood.

**Fix.** The missing state takes precedence over the stock state, in the wording Achats already used, and the stock progress bar goes with it — there is no stock to report on.

### Defect 4 — the Validation step gave no reason

Achats disabled the button and said nothing about why; the only marker was back on the cart step. Dépôt Vente's `saveError` banner existed but was the **last item of the validation LazyColumn**, which on a full screen sits below the fold *behind* the pinned confirm button — a failed save changed nothing the user could see.

**Fix.** A shared `CartBlockingBanner`, docked between the scrolling form and the button so it cannot be scrolled away from the control it explains. Both flows use it for the block; Dépôt Vente's save error moves into the same slot. It carries a **"Corriger"** action, which is what makes it actionable rather than merely informative.

**A per-flow difference, deliberately.** "Corriger" must land on the cart, and the two graphs leave different stacks behind them: the Achats cart's "Suivant" does `popUpTo(products)`, which pops the cart off, so Achats navigates to the cart route; the Vente cart is still the previous destination, so Vente pops back. Each graph passes what actually reaches the cart rather than sharing one assumption that is wrong in one of them.

### Defect 5 — the instruction was truncated

`CartStatusLine` was `maxLines = 1`, which suits a stock reading ("Reste 5 carton") but cut *"Produit supprimé — retirez cette ligne pour continuer"* at **"…pour co…"** — losing precisely the half that says what to do.

**Fix.** `CartStatusLine` takes a `maxLines` parameter defaulting to 1. Only the missing-product callers pass 2, so every other status line in all three cart screens renders unchanged.

### Defect 6 — deleting a product left the Produits tab blank

Found incidentally while setting the test up, and unrelated to drafts.

**Cause.** Two dialogs asked the same question — one owned by `ProductDetailScreen`, one by `ProduitsNavHost` — and both paths popped: the nav host's confirm button called `popBackStack()`, while the `product == null` branch below it popped as well once the row vanished. Two pops took two destinations off the stack.

**Fix.** The duplicate dialog is gone. The surviving one names the product (which is what the removed one contributed) and keeps the supplier-unlink warning (which is what it contributed). `onDelete` now only deletes; the null branch does the single pop.

### Verification

On device, against both flows, with a real product alongside the deleted one so the healthy path stayed observable:

| Check | Achats | Dépôt Vente |
|---|---|---|
| Line kept, named from the draft, total preserved | 280,00 DA | 370,00 DA |
| Status line | "Produit supprimé…" in full, two lines | same (was "Rupture — dépassement") |
| Healthy line unaffected | "Stock 10 → 11 carton" | "Reste 9 carton" + bar |
| Confirm button | disabled | disabled (was enabled) |
| Reason docked above the button | yes | yes |
| "Corriger" opens the cart | yes | yes |
| Removing the line re-enables the button **without leaving the form** | yes | yes |
| Draft completes | bon #15, 80,00 DA | vente #24, 120,00 DA |
| Draft deleted in the commit transaction | yes | yes |
| Product deletion lands on the Produits list | one dialog, naming the product, no blank screen | — |

### Device state after testing

Everything created during the run was removed through the app's own paths — both test products, both drafts, and both completed records:

| | |
|---|---|
| products | 34 |
| ventes | 21 |
| purchase orders | 13 |
| `purchase_drafts` / `vente_drafts` | 0 / 0 |
| product 34 stock | 10.0 (deducted then restored by deleting the vente) |
| supplier AHMED | 360,00 DA |
| client سوسن | 120,00 DA |

No stock movements left referencing the test products, no `price_history` rows for them, and no DistriGo crash lines in logcat. Screen timeout restored.

---

## 13. Phase 11 — the sheet's scroll, exercised at last (`f914290`)

Open item 5 — the `verticalScroll` added in `fd0545d` for a screen or font scale the row cap does not cover — had never run. No device here is short enough to trigger it, but a **font scale** reaches the same condition from the other side, and `adb shell settings put system font_scale` sets one past anything the Settings UI offers.

### The measurement

Five Achats drafts, so the sheet renders its maximum content: `MAX_SHEET_ROWS` = 4 rows, plus "Voir tout (5)", the divider and "Commencer un nouveau bon".

| `font_scale` | Result |
|---|---|
| 1.0 | Fits with room to spare — about 553dp of content on a 780dp screen. No scroll. |
| 1.3 — Android's standard maximum | Sheet nearly fills the screen. Still fits, still no scroll. |
| 2.0 — accessibility extreme | **Overflows.** The fourth row sits at the screen edge; "Voir tout (5)", the divider and the action are all below the fold. |

At 2.0 the guard did exactly its job: swiping up scrolled the content, brought both hidden controls into reach, and stopped at the end — a second swipe changed nothing. Tapping the action *reachable only through that scroll* opened a new bon.

So the scroll is not dead code. Without it, at 2.0 the sheet would trade `fd0545d`'s "must drag" for "cannot reach" — precisely the failure it was written against, and precisely what could not be demonstrated when it was written.

**Two data points, not a sweep.** Where between 1.3 and 2.0 the overflow actually begins was not established, and does not need to be: the guard covers whatever is past it.

### What the test found: the action's label was clipped

At 2.0 the sheet's only action read **"Commencer un"**. `OutlinedButton` carried `Modifier.height(50.dp)` — a fixed height, so the label had no second line to wrap onto and was cut instead.

**Fix.** `heightIn(min = 50.dp)`. The button keeps its 50dp touch target at every ordinary scale and grows to fit the label when it has to; the label gains `TextAlign.Center` so a wrapped second line is centred too. Verified at 2.0 — "Commencer un / nouveau bon" over two centred lines, still tappable, still opens a new bon — and at 1.0, where the sheet renders pixel-identically to before the change.

### A wider observation, deliberately not acted on

The same fixed-height clipping appears elsewhere at 2.0: the purchase form's top bar reads "Nou…" and its supplier button "Choisir un". This looks like an app-wide pattern rather than anything about the sheet, but it was only seen on the screens this test happened to cross, and no attempt was made to survey it. Recorded as an observation, not a finding.

### Device state after testing

Five drafts were created through the UI and removed afterwards with the multi-select bulk delete from Phase 9 — which incidentally exercised "Tout sélectionner", the heavier all-of-them confirmation, and a real `DELETE … WHERE id IN` of five rows. Back to 34 products, 21 ventes, 13 orders, both draft tables empty. `font_scale` restored to 1.0, screen timeout restored, no DistriGo crash lines.

---

## 14. Phase 12 — Brouillons for Tournée Vente (`dc94aab`)

The third flow, and the first real test of whether Phase 1's extraction was sized right. `DraftAutosave`, `DraftRow`, `DraftsSheet`, `DraftFiltering` and `DraftSelection` were reused **unchanged**; one shared component gained one parameter (below). What is new is one table, one repository, one session ViewModel and one screen.

### Scoped to a tournée, not global

A van sale belongs to the round it was made on. `tournee_vente_drafts` carries a **non-null** `tournee_id`, every read is "the drafts of *this* tournée", and the Brouillons chip sits on the tournée detail beside its bons count. There is no global list of van-sale drafts, because there is no screen on which one would mean anything.

Deleting a tournée now drops its drafts first. There is no foreign key to cascade through, so without that the rows would outlive the only screen that could ever reach them.

### Thinner than the other two, on purpose

The tournée form is create-only: no `venteId` argument, no `updateVente` path into it. With no committed record a draft could be an unsaved edit *of*, five things simply do not exist here:

| | Dépôt Vente | Tournée Vente |
|---|---|---|
| `baseFingerprint` | the source vente's | always `null` |
| `isEdit` | true for an edit session | constantly `false` |
| Conflict dialog | yes | none — nothing to conflict with |
| OBSOLÈTE / BLOQUÉ badge | yes | none |
| Resume gate | asks first | goes straight through |

`isEdit == false` is what makes the shared autosave take its new-record path, where emptiness alone decides whether a row is written. Nothing had to be added to `DraftAutosave` to get that; the branch was already there, unused by anything until now.

### Two blocks, and the second has no analogue elsewhere

1. **A product deleted from the catalogue** — the same block Achats and Dépôt Vente grew in Phase 10, in the same words.
2. **A camion whose stock moved.** The stepper enforces a live ceiling, so a cart can only exceed what the van holds on a **resumed** draft: the goods were sold to somebody else in between. This is the only one of the four flows where the resource a draft claims can be spent behind its back and still be worth reporting — Achats draws on a supplier, Dépôt Vente on the dépôt, and neither has a stepper bounded by a figure that drifts.

Both are decided at `hydrate`, marked on the cart line, and reported by the `CartBlockingBanner` docked against the confirm button.

Two details that matter more than they look:

* **The stepper's ceiling is lifted while a line is over it.** Clamping would silently rewrite a quantity the user entered — the same class of mistake as Phase 10's Defect 3, telling the user something untrue about their own line.
* **Lowering the quantity lifts the block**, via `reviseOverStock()`, without the line having to go. The deleted-product block can only be cured by removing the line; this one has a better cure, and offering only the worse one would have repeated Phase 10's Defect 1 in a new place.

### One shared component changed: `showRecordAxes`

`DraftFilterSheet` gained a flag, default `true`, that hides "Type de brouillon" and "État". In a list where every draft is new and active, both axes return either everything or nothing whatever the user picks. A control that cannot partition its list is not a filter, it is a trap. Tournée Vente passes `false` and keeps the party axis, which does partition.

### The commit path

`createVente` now takes `tourneeDraftId` alongside `draftId` — two parameters, not one, because they name rows in two different tables and a single id would have to be told which. At most one is ever set: a sale is composed in one form. Both deletes happen **inside the sale's own transaction**, so a failure anywhere above leaves the work to resume.

### Migration 34 → 35

Additive — one `CREATE TABLE`, one index, no existing table touched. Both statements were diffed against Room's generated `35.json` before being run on live data.

### Verification

Run in two sittings; the first stopped mid-flow because the phone was in use, and the commit was made with that gap recorded rather than papered over.

| Check | Result |
|---|---|
| Migration on the live database | `user_version` 35, no data loss — 24 ventes, 18 clients, 34 products, 19 tournée clients intact |
| A sale left mid-form | draft written; chip on the tournée detail; card reads "manafaa · 1 produit · à l'instant · 120,00 DA" |
| Resume hydrates | manafaa selected, "mini book hippone" marked in-cart, *Ma sélection · 1 · 120,00 DA* |
| Cart line on resume | "Disponible : 8 carton", neutral tone — no false block, since 1 ≤ 8 |
| Commit | vente **#28** created (manafaa, 120,00, source `camion`, tournée 1); `tournee_vente_drafts` → 0 in the same transaction; camion stock 8 → 7 |
| After commit | Brouillons screen shows its empty state, chip gone from the detail |

### Device state after testing

Test vente #28 was removed through the app's own path afterwards:

| | |
|---|---|
| ventes | 24 |
| all three draft tables | 0 / 0 / 0 |
| products / clients | 34 / 18 |
| camion stock (mini book hippone) | 8.0, restored |
| client manafaa balance | 3 890,00 DA, restored |

No DistriGo crash lines; screen timeout restored.

### Not verified

* **Both blocks are code-only.** Triggering either needs a draft plus a catalogue or stock change made behind its back, which means a throwaway product or moving real truck stock. Not done — deliberately, rather than churn live data again without asking.
* **Bulk delete and the filter** on this screen are unexercised: one draft cannot demonstrate either.

---

## 15. Phase 13 — Brouillons for Stock camion, and a crash-proof single-product edit (`71a6a42`, `30dbe8f`, `b26f99e`, `0ca531b`)

The fourth and last flow. Again `DraftAutosave`, `DraftRow`, `DraftsSheet` and `DraftSelection` were reused unchanged; again what is new is one table, one repository, one session ViewModel and one screen. Four consumers in, the Phase 1 extraction has needed exactly one new parameter (`showRecordAxes`, Phase 12) across the three flows built on it.

### The screen it needed first (`71a6a42`, `30dbe8f`)

"Modifier" on a Stock camion product used to mount the whole `ChargementNavHost` for one row: the catalogue, then a jump to the cart with `popUpTo(products, inclusive = false)` — deliberately leaving the catalogue on the back stack. Two screens to change one number, and a Back that landed on a list nobody asked to see.

`ChargementProduitScreen` is the card on its own: the product, its stepper, the dépôt/camion previews it already computes, "Effectué par" and the note. No list, no cart, nothing behind it. `30dbe8f` then lifted the chargement product list's search field out of the `LazyColumn`, where it had been the first item and scrolled away exactly when a list long enough to need it started moving.

### A line stores the target, not the delta

`target_camion` — "15 in the truck" — and the delta is computed against the truck **at save time**. So a draft says what you wanted the truck to hold, and if the truck reached that figure by itself while the draft sat unsaved, the save writes nothing and the button says so by being disabled.

This is the one flow where a draft's meaning is deliberately re-evaluated against the present rather than replayed from the past, and it is why there is no camion-drift block here of the kind Phase 12 needed: drift does not invalidate the intent, it satisfies it.

### One table, two kinds, and `single_product_id` is the difference

| `single_product_id` | What the row is | Listed | Counted | Resumable from the sheet |
|---|---|---|---|---|
| `null` | an ordinary Brouillon from "Nouveau chargement" | yes | yes | yes |
| set | the single-product "Modifier" card's private editing state | **no** | **no** | reopened silently, never offered |

The second kind is the answer to the constraint this phase was given: a dialog only protects against Back, while a crash, a flat battery and a swipe-away ask nothing first. So the edit is written to disk **as it is made**, restored silently when the same card is reopened — as a process-death return does in the other three flows, because the user never chose to abandon it — finalised by "Enregistrer le mouvement" inside the movement's own transaction, and thrown away by "Quitter", which is the one moment the user has said the changes are not wanted.

Durable, not visible. That the two never mix is guaranteed by the query, not by the screen: `observeDrafts` and `observeCount` both carry `WHERE single_product_id IS NULL`.

**The unique index is on a nullable column, and that is the design.** It makes "at most one pending edit per product" a fact about the database rather than a promise in a ViewModel, while SQLite's treatment of NULLs as distinct lets any number of ordinary Brouillons coexist in the same table.

### No filter bar

All three axes the shared `DraftFilterState` offers need something this flow has not: type and état need a source record to edit and a block to be in, and the party axis needs a supplier or a client. A stock movement has neither. Phase 12 hid two axes; here the whole bar goes.

### One bug caught before it shipped

`discardProductDraft` was first launched on the screen's `rememberCoroutineScope`. The screen calls it and immediately navigates away — which leaves the scope cancelled and the delete never run, leaving behind exactly the pending edit the user had just asked to throw away. Moved to `viewModelScope`.

### Migration 35 → 36

Additive, and diffed against Room's generated `36.json` before being run on live data.

### Verification

| Check | Result |
|---|---|
| Migration on the live database | `user_version` 36, no data loss — 43 products, 6 chargements, 25 ventes |
| Edit survives a kill | "cafe dozia" edited, `am force-stop`, reopened — back at 4, **no dialog**, and no Brouillon anywhere in the list or the count |
| "Quitter" discards it | row removed, stock untouched |
| Ordinary Brouillon | chip shown, sheet opens, resumes with its line intact |
| Commit | draft deleted inside the movement's own transaction; chargement **#7** created |

Database restored from a byte-exact backup and re-migrated afterwards.

### Two cleanups after the fact (`0ca531b`)

* **The multi-product cart's save button** was enabled whenever the cart was non-empty, but `save()` writes only lines whose target differs from what the truck holds — so an untouched cart gave a live button that wrote nothing and navigated nowhere, which reads as a failure rather than as a no-op. It now uses `cartItems.any { it.targetCamion != it.product.camion_stock }`, derived from the cart rather than tracked separately so it cannot drift from what the save would do. The save logic itself is unchanged. Verified grey → blue → grey across a `+1` and back.
* **`correctionChargementId` removed.** A parameter for editing an existing movement that no caller ever passed, and that could not have worked: `ProductRepository` has no `updateChargement`, so a "correction" would have written a second movement on top of the first. Gone with it: the detail load, the cart pre-fill, the "Correction du mouvement #n" title and note, and the `ChargementViewModel` the products step held only to read `selectedChargement`. `loadChargementDetail` and `selectedChargement` are left on the ViewModel — plain repository accessors, not dead branches.

### Device state after testing

Everything created during the runs came out through the app's own paths. The cart test's Brouillon was removed by emptying the cart with "Vider", which let the autosave delete the row itself — the empty-snapshot path, exercised incidentally. `chargement_drafts` back to 0, no movement written, screen timeout restored. `testDebugUnitTest` and `assembleDebug` both pass.

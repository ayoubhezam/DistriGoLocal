# Achats — "Rouvrir le bon" navigation & session investigation

**Date:** 2026-09-01
**Scope:** Achats purchase-form graph only. No Draft-architecture changes proposed.
**Status:** analysis complete; fix proposed, **not yet implemented**.

---

## 0. Summary

Three separate defects, not one:

| # | Defect | Verified? |
|---|---|---|
| **A** | Step 1 auto-advances to Step 2 whenever the supplier is known, and re-fires every time the user comes *back* to Step 1 → the user is trapped in Step 2. | Code-confirmed; matches the reported symptom. Device repro still owed. |
| **B** | `reopenOrder` never refreshes `selectedOrder`, so after a successful reopen the detail screen still believes the bon is `received`. | Code-confirmed |
| **C** | `PurchaseOrderDetailScreen` uses `selectedOrder ?: order` with **no id guard**, so it can render the *previously opened* bon's status. | Code-confirmed |

**B** and **C** together are the answer to "why do some closed bons show *Modifier* and others *Rouvrir le bon*". The *rule* is intentional business logic. Which rule you actually see is not reliable.

---

## 1. Question 6 first — "Modifier" vs "Rouvrir le bon"

### The rule is intentional

`PurchaseOrderDetailScreen.kt:48`

```kotlin
val isReceived = displayOrder.status == "received"
```

`PurchaseOrderDetailScreen.kt:192-210`

```kotlin
if (isReceived) {
    DropdownMenuItem(text = { Text("Rouvrir le bon") }, onClick = { showReopenDialog = true })
} else {
    DropdownMenuItem(text = { Text("Modifier") },       onClick = { onEdit() })
}
```

This is correct and deliberate business logic:

* `status = "pending"` → **Modifier**. Nothing has moved; edit directly.
* `status = "received"` → **Rouvrir le bon**. Reception already wrote stock (`receivePurchaseOrder`, `ProductRepository.kt:578`), so the bon must be reopened — which reverses stock and deletes the stock movements — before its lines can change.

So the two actions are not arbitrary. **Do not change the rule.**

### But which one you see is unreliable — two independent state bugs

#### Defect C — no id guard on the detail screen

`PurchaseOrderDetailScreen.kt:47`

```kotlin
val displayOrder = selectedOrder ?: order
```

`selectedOrder` is a **graph-scoped** `StateFlow` on `PurchaseViewModel`, shared by `AchatsHome`, `AchatsDetail` and the form graph. `AchatsDetail` refreshes it asynchronously:

`AchatsNavHost.kt:127`

```kotlin
LaunchedEffect(orderId) { viewModel.loadOrderDetail(orderId) }
```

Between composition and that load completing, `selectedOrder` still holds **whichever bon was opened last**. During that window `isReceived` is computed from the wrong bon, and the overflow menu offers the wrong action.

Reproduction: open a `received` bon → back → open a `pending` bon → open the ⋮ menu quickly. It offers **"Rouvrir le bon"** for a bon that was never received.

Note the form graph *already* guards against exactly this (`PurchaseFormNavGraph.kt:735` uses `selectedOrder?.takeIf { it.id == orderIdArg }`). The detail screen was never given the same guard.

#### Defect B — reopen leaves `selectedOrder` stale

`PurchaseViewModel.kt:162-175`

```kotlin
fun reopenOrder(id: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
    viewModelScope.launch {
        try {
            repository.reopenPurchaseOrder(id)
            loadOrders()          // ← refreshes the LIST
            onSuccess()
        } catch (e: Exception) { onError(extractErrorMessage(e)) }
    }
}
```

`loadOrders()` refreshes the list; **`loadOrderDetail(id)` is never called**, so `selectedOrder` keeps the pre-reopen snapshot with `status = "received"`.

Consequences:

1. Returning to the bon's detail screen after a reopen still shows the **"Reçu"** badge and still offers **"Rouvrir le bon"** — for a bon that is now `pending`.
2. In the form graph, the edit-mode gate is

   ```kotlin
   LaunchedEffect(orderIdArg, selectedOrder?.id) {
       if (orderIdArg != null && selectedOrder?.id != orderIdArg) viewModel.loadOrderDetail(orderIdArg)
   }
   if (isEdit && selectedOrder?.id != orderIdArg) { spinner; return@composable }
   ```

   The guard compares **ids only**. After a reopen the id already matches, so **no reload is issued** and the gate passes immediately against the stale snapshot. The form is prefilled from stale data.

   *Today this is benign* — `reopenPurchaseOrder` changes only `status` and stock, not items/note/`montant_paye`, so the prefilled content is still correct, and `resolveBaseState` reads the order fresh from the DAO rather than from `selectedOrder`. But the form is running on data it believes is current and is not. Any future field touched by reopen becomes a silent prefill bug.

---

## 2. The main defect — Defect A, the Step 1 trap

### Root cause

`PurchaseFormNavGraph.kt:215-221`, inside the **Step 1 (Fournisseur)** destination:

```kotlin
LaunchedEffect(formSupplier, isEdit, supplierIdArg) {
    if (formSupplier != null && (isEdit || supplierIdArg != null)) {
        navController.navigate(Screen.PurchaseFormProducts.route) {
            popUpTo(Screen.PurchaseFormSupplier.route) { inclusive = false }
        }
    }
}
```

**The condition is a persistent state, not an event.**

It reads "a supplier is known", which in edit mode is true *forever* — the supplier is prefilled the moment the session hydrates and never becomes null again. It does not read "the user just chose a supplier", which is the thing that should actually cause a forward navigation.

The graph's start destination is genuinely Step 1:

```kotlin
navigation(startDestination = Screen.PurchaseFormSupplier.route, route = graphRoute, ...)
```

So entering the graph **does** start at Step 1 — that is why Step 1 "appears very briefly". It composes, the session prefills the supplier, this effect fires, and it is replaced by Step 2 within a frame.

Then the trap: `popUpTo(..., inclusive = false)` **keeps Step 1 on the back stack**. Pressing Back from Step 2 pops to Step 1, Step 1 enters composition again, `LaunchedEffect` restarts (a `LaunchedEffect` restarts whenever its composable re-enters composition, regardless of whether its keys changed), `formSupplier` is still non-null, `isEdit` is still true → it navigates straight back to Step 2.

That is exactly the reported symptom: *"Step 01 appears only very briefly"* and *"I cannot actually return to Step 01"*. The back stack is the worst of both worlds — Step 1 is retained but immediately re-skipped.

### This is not specific to "Rouvrir le bon"

Both menu actions converge on the same call:

```
"Modifier"        → onEdit() ─┐
"Rouvrir le bon"  → reopenOrder(...) { onEdit() } ─┘
                              → editOrder(orderId) → openForm(orderId, null)
                              → navigate(PurchaseFormGraph.createRoute(orderId = ...))
```

`AchatsNavHost.kt:28-40, 17-21`. `isEdit = orderIdArg != null` is true for both, so **the trap applies to "Modifier" as well**. Reopen is simply where it hurts most, because reopen has already reversed stock by the time the user is trapped.

The third entry point, "Nouvel achat" from a supplier page (`SuppliersNavHost.kt:103`, `supplierId` set, `orderId` unset), hits the **same** effect through the `supplierIdArg != null` branch and has the same broken Back.

### One open contradiction — to be confirmed on device

In an earlier session on this device I resumed an **edit draft**, pressed Back from Step 2, and Step 1 *stayed* (a further Back then exited the graph). That contradicts the analysis above, which predicts an immediate bounce back to Step 2.

I have not reproduced it either way in this session — the phone dropped to 4% battery and adb began re-authorizing mid-run. Possible explanations to check:

* a timing/animation race (the pop and the re-navigate collapsing into one another),
* different behaviour on the `draftId != null` resume path vs. the plain `orderId` edit path.

**The user's own report is the primary evidence and it matches Defect A.** The device repro is still owed and should be run before the fix lands, so that the fix is verified against a reproduction rather than against a theory.

---

## 3. Requirement 4 — is the reopened bon preserved as a Draft?

### Already satisfied for any actual modification

Verified on-device earlier in this work:

* change anything in an edit session → autosave writes a `purchase_drafts` row with `source_order_id` within 500 ms;
* Back / leaving the graph does **not** delete it;
* `ON_STOP` flushes it before the process can be killed;
* process death restores the session silently, and the draft is recoverable from **Brouillons** afterwards;
* the draft is deleted only inside the commit transaction.

So: reopen a bon, change a line, then leave / kill the app / power off → the modification is in Brouillons. **This requirement is met today and needs no change.**

### The one gap — and it is deliberate

If the user reopens a bon and changes **nothing**, no draft is created. That is the fingerprint contract working as designed and as approved: a freshly opened, untouched edit must not spawn a phantom draft.

Nothing is lost in that case — the bon still exists, now `pending`, editable from the list via "Modifier". There is no "unfinished modification" to recover.

### The real hazard behind requirement 4

`reopenPurchaseOrder` is **committed immediately**, before the form even opens:

`ProductRepository.kt:508-519` — reverses stock on every line, sets `status = "pending"`, deletes the `stock_movements` rows for that order.

So a user who taps **Rouvrir le bon**, then backs out, has already changed real data: the bon is no longer "Reçu" and stock has moved — with no draft, no undo, and (because of **Defect B**) a detail screen that still claims the bon is "Reçu".

That is a genuine design question and I have deliberately **not** decided it. Two options:

* **(i) Keep it committed** (status quo) — reopen is an explicit, confirmed action with its own dialog, and the stock reversal is arguably the point of it. Fix only **B** so the UI stops lying afterwards.
* **(ii) Defer the reopen** until the edit is saved — the form would open in a "will be reopened on save" mode and the stock reversal would join the commit transaction. Much larger change; touches the commit path and the fingerprint base.

**Recommendation: (i).** It is consistent with how the app already treats reception, and it keeps the Draft architecture untouched.

---

## 4. Proposed fix — smallest correct change

Three edits. None of them touch the session ViewModel, `SavedStateHandle` lifecycle, autosave, the fingerprint, or conflict handling.

### Fix A — make Step 1 a real step; stop modelling an event as state

In `PurchaseFormNavGraph.kt`, **delete** the auto-advance `LaunchedEffect` (lines 215-221).

Forward navigation from Step 1 already exists and is correct — `Step1Fournisseur`'s own **"Suivant"** button, which is an actual user event:

```kotlin
onNext = { navController.navigate(Screen.PurchaseFormProducts.route) }
```

Result:

* entering edit mode (Modifier **or** Rouvrir) lands on Step 1 as a real destination showing the bon's supplier;
* Step 1 → 2 → 3 forward, and Back 3 → 2 → 1 → exit, all behave normally;
* no new state, no forced navigation — requirement 5 satisfied by *removing* machinery rather than adding it.

**Cost:** one extra tap on entry, for edit mode and for "Nouvel achat depuis un fournisseur". That is the price of Step 1 being a real destination, which is what requirements 1–3 ask for.

*If that extra tap is unwanted for the supplier-page shortcut*, the alternative is to keep the skip but fire it **once per session** (a flag in the session's `SavedStateHandle`, so it survives process death and does not re-fire on Back). I do **not** recommend it for edit mode, because it still makes Step 1 flash — contradicting requirement 2 — but it is available for the `supplierIdArg` path alone if you want that flow left exactly as it is.

### Fix B — refresh the detail after a reopen

`PurchaseViewModel.reopenOrder`: add `loadOrderDetail(id)` next to `loadOrders()`, so `selectedOrder` reflects the new `pending` status before `onSuccess()` runs.

### Fix C — guard the detail screen against the previous bon

`PurchaseOrderDetailScreen`: take the id-guarded value, mirroring what the form graph already does.

```kotlin
val displayOrder = selectedOrder?.takeIf { it.id == order.id } ?: order
```

`order` is the correct bon from the list, so the fallback is always right, and the stale-status window disappears.

---

## 5. Explicitly out of scope

Found during this investigation, **not** proposed for change:

* **Supplier cannot be changed in edit mode.** `updatePurchaseOrder` (`ProductRepository.kt:646-648`) reads `existing.supplier_id` and never writes a supplier. But Step 1's **"Changer"** button is wired to the supplier picker in edit mode too (`PurchaseFormNavGraph.kt:237`). Changing the supplier there is a silent no-op — the save discards it. Once Fix A makes Step 1 visible in edit mode, this button becomes much easier to reach, so it is worth deciding: hide "Changer" in edit mode, or make the save honour it.
* `formatQty` / `formatDZD` / `Locale` issues — already fixed in earlier work.
* Other Draft flows (Chargement, Vente, Tournée-Vente) — not started, as instructed.

---

## 6. Verification plan for the fix

To run once the device is charged, on test bon **#11** (`TEST-F13-F14-2026-09-01`):

1. Mark #11 **received**, then **Rouvrir le bon** → must land on **Step 1** showing CANDIA.
2. Step 1 → 2 → 3 forward; then Back 3 → 2 → 1 → exit. Step 1 must be stable at every visit.
3. Same for plain **Modifier** on a pending bon.
4. Reopen, change one line, press Back → a Brouillon must exist with `source_order_id = 11`.
5. Reopen, change one line, kill the process → relaunch must restore silently to the step left, no dialog.
6. Reopen, change nothing, back out → **no** draft (fingerprint contract intact).
7. After reopen, return to the detail screen → badge must read **"En attente"** and the menu must offer **"Modifier"** (Fixes B + C).
8. Open a received bon → back → open a pending bon → menu must offer **"Modifier"** immediately (Fix C).
9. Regression: draft autosave, resume, and the CHANGED / RECEIVED / DELETED conflict dialogs unchanged.

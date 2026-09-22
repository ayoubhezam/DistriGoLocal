# Impression — architecture

Hardware printing for DistriGo receipts: how the module is laid out, why it is laid out that way,
and the decisions that were settled before any of it was written.

Written 2026-09-22, at the opening of the Print Settings feature. Phases 0, 1 and 2 are implemented —
phase 2's code is complete and unit-tested but has not yet met a real printer. Phases 3–5 are the plan.

---

## 1. Where we started

Before this module, "printing" meant one thing: draw an A4 PDF and hand it to Android's own print
dialog.

| Piece | File | Role |
|---|---|---|
| Content model | `ui/components/ReceiptData.kt` | `ReceiptData` + `ReceiptLineItem`, plus `Vente.toReceiptData()` and `PurchaseOrder.toReceiptData()` |
| On-screen preview | `ui/components/ReceiptPreviewSheet.kt` | Bottom sheet, hand-laid-out in Compose |
| PDF | `ui/components/ReceiptPdfGenerator.kt` | A4 `PdfDocument`, 595×842 pt, drawn with `Canvas`/`Paint` |
| "Print" | `ReceiptPreviewSheet.printReceiptPdf()` | Hands the PDF to `PrintManager` — the OS dialog, not hardware we drive |
| Share | `ui/components/ShareOptionsSheet.kt` | A second, independent call to the same PDF generator |

Call sites, before this work: `ui/purchases/PurchaseOrderDetailScreen.kt` (Achats) and
`ui/ventes/VentesScreen.kt` (Dépôt Ventes).

**Correction, made in phase 3.** The original audit reported that Tournées Ventes had no print button,
on the strength of grepping `ui/tournees/` for `ReceiptPreviewSheet` and "Imprimer" and finding
nothing. That grep was scoped to the wrong package. A tournée sale's detail screen is not in
`ui/tournees/` at all: `TourneesNavHost` routes `Screen.TourneesVenteDetail` to
`ui/ventes/VentesScreen.kt`'s `VenteDetailScreen`, the same screen Dépôt Ventes uses, whose
unconditional bottom row already carried "Aperçu & Imprimer". Tournées Ventes could always print, and
it inherits the thermal routing with no new button.

There are therefore **two** receipt entry points, not three, and both go through
`ReceiptPreviewSheet` — which is why phase 3 changed one button and reached all of them.

No Bluetooth, Wi-Fi or socket code existed anywhere in the project. This module is greenfield.

---

## 2. Paper width cannot be detected. It is chosen, then confirmed by a test print.

The question was whether Android can read the printer's paper width on connection. It cannot, and
the reasons are worth recording so nobody re-opens it.

**Bluetooth SPP** — what ~95% of 58/80 mm thermal printers speak — is a dumb byte pipe. ESC/POS has
status commands (`DLE EOT n` real-time status, `GS I n` printer ID) but they report *paper present /
near-end / drawer / error / a vendor model byte*. None reports print width. Genuine Epson TM units
expose print-area dots through `GS ( E` memory switches and Star's SDK reports it; the inexpensive
units our users actually buy (RPP02N, MTP-II, generic `BlueTooth Printer`) implement neither, and
many do not implement the read direction of the SPP channel at all. Device names are useless as a
heuristic — dozens of unrelated models ship as literally `BlueTooth Printer`.

**Wi-Fi.** A printer speaking **IPP** (AirPrint-class) does report `media-supported` /
`media-size-supported`. Receipt printers on Wi-Fi are almost always **raw TCP on port 9100**, which
is the same dumb byte pipe. So: no.

**Label languages.** TSPL has gap/black-mark auto-calibration (`AUTODETECT`, `~!D`) and CPCL has
`FORM`/sensor calibration. These detect label **length and gap position**, never width. Relevant
later for label stock, irrelevant to receipts.

**A4 is the real exception.** When the user picks A4 we hand off to `PrintManager`, whose own printer
discovery negotiates media size with the print service. A4 therefore needs no width setting from us;
the OS dialog owns it.

So paper width is **chosen manually**, which is also what Loyverse, SumUp, Square and Shopify POS all
do. Four rules:

1. **Manual picker, defaulted to 80 mm** — the most common counter printer. 58 mm is the pocket unit.
2. **Width belongs to the printer, not to the app.** A rep with an 80 mm counter unit and a 58 mm belt
   printer must not re-pick on every switch. Per-printer property (`SavedPrinter.paper`), with the
   app-level value acting as the default for the next printer added.
3. **A calibration strip is the detection.** Print a ruler with markers at column 32 and column 48
   plus a full-width bar; the user sees which marker reaches the paper edge. One tap, unambiguous,
   works on every printer ever made.
4. **Name heuristics pre-fill only.** Never silently — always a pre-filled value the user confirms on
   the test print.

The numbers the thermal renderer is built against (203 dpi, the universal thermal resolution):

| Paper | Printable | Dots | Font A (12×24) | Font B (9×17) |
|---|---|---|---|---|
| 58 mm | ~48 mm | **384** | **32 chars** | 42 chars |
| 80 mm | ~72 mm | **576** | **48 chars** | 64 chars |

Those two numbers — 32 and 48 — are the entire layout spec.

---

## 3. One screen for the receipt, two storage backends

"Paramètres du reçu" (logo, business name, phone) and printer configuration are merged into a single
entry, renamed **"Reçus et impression"**.

**Why merged.** Two sibling cards whose names differ by one word, both about the same piece of paper,
is a discoverability trap: a user who wants to change the logo has two plausible cards and no way to
tell which. Worse than either arrangement alone.

**Why the receipt content is not *nested under* printing.** It would invert the dependency. Logo,
business name and phone are printed on paper, shown in the Compose preview sheet, drawn into the PDF,
**and pasted into the WhatsApp text** (`ShareOptionsSheet.receiptAsPlainText`). They are a document
concern that printing consumes, not a printing concern. Burying "change my shop name" three levels
deep behind a Bluetooth menu would be wrong.

So: one screen, three sections, in this order — content feeds preview feeds paper:

```
── CONTENU DU REÇU ───────────────────   Room (business_settings), syncs, backed up
   [logo]  Nom du commerce   Téléphone

── IMPRIMANTE ────────────────────────   device-local JSON, never synced
   Méthode de connexion   [ Bluetooth ▾ ]
   Imprimante             Aucune      ›   → PrinterSelectionScreen
   Format du papier       [ 80 mm ▾ ]
   Langage d'impression   [ ESC/POS ▾ ]

── APERÇU ────────────────────────────
   [ live thermal mockup ]
```

The section headers carry the whole explanation; nobody has to guess which one holds the logo.

### The storage split is non-negotiable

`BusinessSettingsRepository` keeps the business identity in a **Room row** (`business_settings`,
uuid-stable across phones) precisely so it travels with backups and with the sync postponed to
phase 4. `AutoBackupStore` keeps device-local state in `no_backup/auto-backup/state.json` precisely
so it does *not*.

Printer configuration is the second kind. Put it in Room and `BackupCreator` picks it up: restore a
backup onto a second phone and it inherits a MAC address for a printer it has never been paired with;
when multi-role lands, every rep's phone fights over one printer row. Printer config describes *this
handset*, exactly like `AutoBackupStore`'s folder permission, and lives in
`no_backup/print/settings.json`.

One screen, two stores. `PrintSettingsStore` carries this reasoning in its file header.

---

## 4. ESC/POS, TSPL and CPCL are three machine classes, not three dialects

- **ESC/POS** — receipt printers. Streaming, line by line, no page concept. Our 58/80 mm thermal.
- **TSPL** — TSC-family **label** printers. Page-based: declare `SIZE`, `GAP`, build the whole label
  in memory, then `PRINT 1`.
- **CPCL** — Zebra/Citizen **mobile label** printers. Also page-based: `! 0 200 200 <height> 1` —
  **the page height must be known before the first byte is sent.**

Three consequences, all of which shaped the code:

1. **Offering all three as a careless global dropdown produces garbage.** Pick TSPL on an ESC/POS
   printer and the paper spools out with `SIZE 80 mm,297 mm` printed as literal text. This is the
   number-one support ticket in every POS app that exposes the setting without a gate.
2. **Language is stored per printer**, defaults to ESC/POS, and is confirmed by the test print. The
   flow is *choose → test → confirm*, never *choose → save → discover at the counter*.
3. **The renderer is two-phase: lay out → measure total height → emit.** ESC/POS ignores the height;
   TSPL and CPCL require it up front. Built for the stricter contract from day one so the renderer is
   not rewritten when the first CPCL belt printer arrives.

The setting stays — field reps in Algeria do buy CPCL belt printers — but it is framed as a
compatibility choice, not a headline option.

---

## 5. One layout engine, three consumers

The central decision. The preview is **not** a hand-drawn mockup. The layout is built once as plain
monospace rows, and the same rows feed the preview and the printer. The preview is then literally
truthful rather than approximately truthful, for the same amount of work — and when a printed receipt
looks wrong, there is one function to debug.

```
ReceiptData ──► ThermalLayout.layout(receipt, paper) ──► List<ReceiptRow>
                                                            │
                        ┌───────────────────────────────────┼──────────────────┐
                        ▼                                   ▼                  ▼
                EscPosRenderer                      TsplRenderer /      ThermalReceiptPreview
                → ByteArray                         CpclRenderer        (Compose, monospace)
                  (phase 2)                           (phase 5)            (phase 0)
```

`ReceiptRow` is a small sealed type — `Line`, `Columns`, `Rule`, `Blank`, `Raster`, `Qr`, `Cut`.
`PaperProfile` carries `charsPerLine` (32/48), `dotsPerLine` (384/576), dpi and the physical widths.

A4 stays entirely outside this: it keeps `ReceiptPdfGenerator` + `PrintManager`, unchanged.

### Item rows differ by paper, on purpose

- **80 mm (48 chars)** — a real single-line table: `name(20) colis(5) qté(5) P.U.(7) total(9)`.
- **58 mm (32 chars)** — two lines per item: the wrapped name, then `  qté × P.U.` left with the line
  total right-aligned.

This is what real receipts do, and it makes the 58↔80 switch *visibly* different in the preview. The
user sees product names truncating. That is the point, and a hand-drawn mockup cannot show it.

---

## 6. Accents and images, settled in the engine

### Accented French

`é à ç ê` need the right code page selected before the bytes go out — ESC/POS `ESC t n`, with
`n = 16` for CP1252 or `n = 19` for CP858 — and inexpensive clones vary in which they honour.

`PrinterCodePage` therefore:

- is a **fourth per-printer property**, beside language and paper;
- encodes through the JVM charset, and where a character has no byte in the target page, falls back
  to **transliteration** (`é → e`, `ç → c`, `« » → "`) rather than emitting `?` — a receipt with
  plain ASCII is readable, a receipt of question marks is not;
- publishes `ACCENT_PROBE` (`ÉÈÀÇÊÛ Ôî — àéèçêûôï`), which the test print includes so the user catches
  mojibake at setup time instead of at the counter.

### Logo and QR

Thermal printers take 1-bit raster. `ThermalRaster` scales a bitmap to the paper's exact dot width
(384 or 576, padded to a multiple of 8 because each byte is 8 horizontal dots) and dithers with
**Floyd–Steinberg**, which keeps a photographic logo legible where a plain threshold turns it into a
blob.

The preview renders the dithered raster *back* to a bitmap and draws that — so the logo on screen is
the logo the paper will carry, speckles and all.

The QR is emitted as a `Qr` row, not a bitmap, so the ESC/POS renderer can use the native `GS ( k`
barcode command (sharper, far fewer bytes) while the preview and any raster-only fallback go through
ZXing and `ThermalRaster`.

---

## 7. Connection state — one sealed hierarchy for both transports

Planned for phase 2. The point is that the UI has a single `when`, whichever transport is in use:

As built, in `PrinterGate.kt` and `transport/PrinterTransport.kt`:

```
sealed interface PrinterLink
  object NotConfigured                              "Aucune imprimante"
  data class Idle(printer)                          saved, paired, radio on
  data class Connecting(printer)
  data class Ready(printer)                         answered a probe
  data class Blocked(printer?, reason)              ◄── the important one

enum PrintFailure                       message()           → action
  NO_BLUETOOTH        "Bluetooth indisponible"   → (none; use the PDF)
  PERMISSION_DENIED   "Autorisation refusée"     → "Autoriser"
  ADAPTER_OFF         "Bluetooth désactivé"      → "Activer"
  NOT_PAIRED          "Imprimante non jumelée"   → "Ouvrir les réglages"
  UNREACHABLE         "Imprimante injoignable"   → "Réessayer"
  INTERRUPTED         "Impression interrompue"   → "Réimprimer"
  NOT_CONFIGURED      "Aucune imprimante"        → "Choisir"
```

Two deviations from the sketch above, both deliberate:

- **`PERMISSION_DENIED_FOREVER` was dropped.** Distinguishing it needs
  `shouldShowRequestPermissionRationale`, which is an Activity call, so the gate cannot know it
  without being handed UI state. Rather than add a parameter nothing else uses, the permission
  launcher's own result handles the "don't ask again" case.
- **`INTERRUPTED` was added.** A socket that breaks mid-job leaves half a receipt on the paper, which
  the user has to be told about differently from a job that never started — "Réimprimer", not
  "Réessayer".

`PrinterGate.check()` does no I/O and is cheap enough to call per recomposition; `probe()` opens a real
connection, because a printer that is paired, powered and already talking to another phone passes
every cheap check and still refuses the socket.

Three rules make it work:

- **Every `Blocked` carries its own action.** No toast ever says "erreur d'impression"; it says what
  is wrong and shows the button that fixes it. A rep standing in front of a client cannot debug.
- **`PrinterGate` runs the same checks in the same order** whether called from the settings screen or
  from the print button — so the settings screen cannot claim "Prête" while printing fails.
- **Failures go to the banner, never to a dialog.** The banner is persistent, carries the action and
  does not have to be dismissed before the user can act on it — they can walk over and switch the
  printer on with it still on screen. A modal on top of it said the same thing twice and blocked the
  screen while saying it. The dialog is kept only for something that went *right* and leaves no trace
  otherwise: "Test envoyé", "l'imprimante répond".

The label a failure shows and the action its button takes come from the same `message()` entry, so
they cannot drift apart — a button reading "Réessayer" that opened the system settings instead was
exactly the bug the device walk turned up.

**Never block the sale on the printer.** The sale is already committed when printing is attempted; a
failure offers *Réessayer / Partager en PDF / Plus tard* and leaves a reprint affordance on the
receipt. This is the single biggest field-reliability decision in the module.

---

## 8. Permissions (phase 2)

Nothing Bluetooth is declared today. Spanning API 26 → 36:

```xml
<!-- API ≤30 -->
<uses-permission android:name="android.permission.BLUETOOTH"       android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30"/>
<!-- API 31+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
                 android:usesPermissionFlags="neverForLocation"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
```

`ACCESS_FINE_LOCATION` is already declared for the map pickers, and is needed here too: on API ≤30
classic discovery requires it, and reading the SSID requires it **plus location services physically
switched on**, or `WifiInfo.getSSID()` returns `<unknown ssid>`. Handle that case explicitly — it
looks like a bug otherwise.

**The shortcut that avoids most of this:** list `BluetoothAdapter.bondedDevices` **first**. Most users
pair the printer once in Android's own settings, and bonded devices need only `BLUETOOTH_CONNECT` — no
scan, no location, no permission theatre. "Appareils jumelés" goes at the top of
`PrinterSelectionScreen`; "Scanner" is the secondary action for the minority who have not paired.

Follow the runtime-request pattern already in `ui/common/PhotoPicker.kt`.

---

## 9. PrinterSelectionScreen (phase 2)

```
┌ Imprimantes ──────────────────── ⟳ ─┐   ⟳ = Scanner (secondary)
│ ENREGISTRÉES                         │
│  🖨 Caisse 80mm    ●Prête       ⋮   │   ⋮ → Renommer / Format / Langage /
│  🖨 Belt RPP02N    ○Hors portée ⋮   │       Code page / Test / Supprimer
│ APPAREILS JUMELÉS                    │
│  ＋ MTP-II          66:22:xx        │
│ DÉTECTÉES  (après scan)              │
│  ＋ Printer_001     AA:BB:xx        │
└──────────────────────────────────────┘
```

Scan runs ≤30 s with a visible timer and auto-stops; classic discovery is expensive and hammers the
radio. A saved printer's row menu carries *Imprimer un test*, *Vérifier la connexion*, *Format / langage /
encodage*, *Renommer* and *Supprimer* — so paper width is confirmed when the printer is added rather
than discovered at the counter. The paper choice there excludes A4, which is not a thermal width at
all and takes the PDF path instead.

---

## 10. File layout

```
data/print/
├── PaperProfile.kt             58/80/A4 → chars, dots, dpi, mm            ✔ phase 0
├── PrintSettings.kt            device-local model + SavedPrinter          ✔ phase 0
├── PrintSettingsStore.kt       no_backup/print/settings.json              ✔ phase 0
├── lang/
│   ├── ReceiptRow.kt           the sealed row type                        ✔ phase 0
│   ├── ThermalLayout.kt        ◄── the engine                             ✔ phase 0
│   ├── PrinterCodePage.kt      encoding + transliteration + probe         ✔ phase 0
│   ├── ThermalRaster.kt        Floyd–Steinberg 1-bit raster               ✔ phase 0
│   ├── EscPosRenderer.kt        ESC/POS byte stream                      ✔ phase 2
│   ├── TsplRenderer.kt / CpclRenderer.kt                                    phase 5
├── transport/
│   ├── PrinterTransport.kt     the contract + PrintFailure                ✔ phase 2
│   ├── BluetoothSppTransport.kt   RFCOMM, UUID 00001101-…                ✔ phase 2
│   └── TcpTransport.kt            raw socket, default port 9100             phase 4
├── discovery/
│   ├── BluetoothScanner.kt     bonded devices + ACTION_FOUND              ✔ phase 2
│   └── WifiInfoProvider.kt     current SSID                                 phase 4
├── PrinterGate.kt              pre-flight: permission → adapter → paired → reachable   phase 2


ui/settings/print/
├── ReceiptAndPrintSettingsScreen.kt   absorbs the old ReceiptSettingsScreen ✔ phase 1
├── PrintSettingsViewModel.kt                                               ✔ phase 0
├── ThermalReceiptPreview.kt           shared by settings + real preview     ✔ phase 0
├── SampleReceipt.kt                   hardcoded ReceiptData, no DB          ✔ phase 0
├── PrinterSelectionScreen.kt / ViewModel                                  ✔ phase 2
```

`PrintSettingsStore` gets a `@Provides @Singleton` in `di/AppModule.kt`, following
`provideAutoBackupStore`.

---

## 11. Phases

| Phase | Scope | Ships | State |
|---|---|---|---|
| **0** | `PrintSettings` + store + `PaperProfile` + `ThermalLayout` + `ThermalRaster` + `PrinterCodePage` + preview. No hardware. | Settings screen with a working live preview — reviewable before touching Bluetooth | **done** |
| **1** | Merge receipt settings in; rename the card to "Reçus et impression" | The reorganisation of §3 | **done** |
| **2** | BT permissions, `BluetoothSppTransport`, `PrinterSelectionScreen`, `PrinterGate`, `EscPosRenderer`, `TestPrint` calibration strip | Real printing, ESC/POS only | **code done, unverified against hardware** |
| **3** | `ReceiptPrinter` behind the print button in `ReceiptPreviewSheet`, which Achats, Dépôt Ventes and Tournées Ventes all share; A4 keeps the PDF path; PDF offered as the fallback on every failure | Hardware printing in production | **done** |
| **4** | `TcpTransport` + SSID display + manual IP | Wi-Fi | |
| **5** | TSPL / CPCL renderers | Compatibility | |

Phase 0 de-risks the module: the layout engine is where the real design decisions live, and it is
testable on a desk with no printer (`app/src/test/.../data/print/`).

---

## 12. Phase 3 — the button

One button changed, in `ReceiptPreviewSheet`, and all three screens followed, because Achats, Dépôt
Ventes and Tournées Ventes open the same sheet.

**The paper picks the route, silently.** A4 keeps `ReceiptPdfGenerator` + `PrintManager`; 58/80 mm go
to the thermal printer. The user is not asked which they meant — they already said, on the settings
screen — but the button states where it is about to send the receipt underneath itself
(`printTargetLabel`), because printing is the one action here whose outcome depends on a setting made
somewhere else. A paper size should not be a surprise discovered on the roll.

**The A4 half stays in the composable.** `PrintManager` refuses an application-scoped context, so the
PDF path needs the Activity's; only the thermal half moved into `ReceiptPrintViewModel`.

**The sale is already committed when any of this runs.** So a printer that is off, out of range or
unconfigured is never phrased as a failed sale, nothing is retried automatically, and *Partager en
PDF* sits beside *Réessayer* on every failure — the share sheet the screen already had. An
unconfigured printer drops the retry entirely, since there is nothing to retry until one is chosen on
another screen, and says where to choose it.

Nothing records that a receipt was printed. That was a deliberate call: it would be a Room column and
therefore a migration, and the schema stays untouched at this stage. See the open questions.

## 13. Found on the device, phase 2

Three things the walk turned up that no unit test would have:

- **"Réessayer" opened the app's system settings.** `resolve()` handled the permission, adapter and
  pairing blockers by name and sent *everything else* to the app details page — so the retry button on
  an unreachable printer navigated away instead of retrying. Now `UNREACHABLE` and `INTERRUPTED`
  re-probe, and the `when` is exhaustive over `PrintFailure` so a new failure cannot fall into a
  default branch again.
- **The action sheet ran under the navigation bar**, leaving "Supprimer" visible but untappable. Two
  causes, both needed fixing: the sheet opened at its half-height detent and its rows ran past its own
  background, and even expanded it drew under the system navigation. Fixed with
  `skipPartiallyExpanded = true` plus `navigationBarsPadding()`, and the content scrolls for a large
  font scale.
- **A result dialog was observed not to appear once**, after a failed probe, though the same dialog
  rendered correctly when the failure was instant (Bluetooth off). Never explained. It stopped
  mattering when failures moved to the banner, but it is recorded here rather than quietly dropped.

## 14. Noted while building

**`File.renameTo` does not overwrite.** `PrintSettingsStore` originally used the temp-file-and-rename
that `AutoBackupStore` uses, and every write after the first one threw: `renameTo` is specified to fail
when the destination exists, and does on some filesystems. It happens to work on the phone's ext4/f2fs,
so the device never showed it and the JVM tests did immediately. `PrintSettingsStore` now uses
`Files.move(…, REPLACE_EXISTING)`.

**`AutoBackupStore.update` still has the original form.** It is not broken on Android for the same
reason, and it is outside this module, so it was left alone — but it is the same latent bug, and if
that store ever gains a desktop or JVM-side test it will surface there first.

## 15. Open questions

- **Which code page do the printers on the ground actually honour?** CP1252 (`ESC t 16`) is the
  assumption; CP858 (`ESC t 19`) is the fallback. Settled by the phase-2 test print on real hardware,
  not by reading datasheets.
- **Font B for 58 mm?** 42 chars instead of 32 would let the 58 mm layout keep the single-line table.
  It is small print on already-small paper; the two-line layout was chosen instead. Revisit if users
  complain about receipt length rather than legibility.
- **Reprint history.** Decided for now: **not tracked**. Nothing records that a receipt was printed,
  and the schema is untouched. If disputes or audits make it matter, it is a Room column and therefore
  a migration, and the receipts printed before that point will have no history to show.

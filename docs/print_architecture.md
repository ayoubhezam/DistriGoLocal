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

| Paper | Printable | Dots | Font A (12×24) | **Font B (9×17)** |
|---|---|---|---|---|
| 58 mm | ~48 mm | **384** | 32 chars | **42 chars** |
| 80 mm | ~72 mm | **576** | 48 chars | **64 chars** |

The receipt is set in **Font B**, so **42 and 64** are the layout spec. See §13 for why, and for the
rest of what makes the printout short.

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

## 5. The receipt is drawn, not typed

**The decision this module turns on.** ESC/POS has no bidirectional layout and no contextual
shaping, so Arabic comes out unjoined, in isolated forms, running left to right — illegible rather
than merely ugly. The apparent escape, CP864, holds *presentation* forms, which means doing the
shaping and reordering yourself and mapping into those code points: writing a text engine to avoid
the one Android already ships.

So the receipt is drawn on a Canvas with `StaticLayout` — HarfBuzz for shaping, ICU for bidi —
reduced to one bit per dot, and sent as images. Every POS app that prints Arabic, Hebrew, Thai or
Devanagari does this.

The cost is bytes: ~1.5 KB as text against ~40 KB as dots. **Measured before committing**, on the
actual printer:

| | |
|---|---|
| 40.1 KB sent in | 4.0 s |
| of which our own chunk pacing | 3.5 s |
| link rate | **69.8 KB/s** |
| banding / stutter | none — one continuous motion |

The link was never the bottleneck; the pacing was. It has been cut from 256 B/20 ms to 1 KB/4 ms,
which turns that 3.5 s into about 0.16 s. Reduced rather than removed, because the benchmark ran
*with* pacing in place: what it proves is that the printer keeps up at this rate, not that it would
with none at all. If banding ever appears, that is the knob.

### Row by row, not receipt by receipt

Each `ReceiptRow` becomes its own small bitmap, emitted and released before the next is drawn. One
image for a 200-item receipt would be a 14 MB bitmap; this way memory is bounded by the tallest
single row whatever the receipt's length, and rows stay the unit the preview consumes too.

`GS v 0` calls follow each other with **no line feed between them** — a row's height is its own
spacing, so a feed would open a gap the renderer never drew.

### Thresholded, not dithered

Floyd–Steinberg is right for a photograph and wrong for text: it speckles glyph edges and makes
small type look fuzzy. Text is drawn with `isAntiAlias = false` and reduced by a plain threshold, so
the dots land exactly where the renderer put them. The logo still arrives pre-dithered by the other
path — the two reach `MonoRaster` differently on purpose.

### What this cost, and what it bought

Gone: the character grid. `charsPerLine`, `pad`, `wrap`, `fit` and the money-shortening that dropped
centimes when a number outgrew its column. `ThermalLayout` no longer measures anything — it decides
what goes on the receipt and in what order, and the renderer, which alone knows what the glyphs look
like, does the rest.

Gained, beyond Arabic: cells that size themselves, so a long product name pushes its own row down
without disturbing the figures beside it; a preview that is not an approximation but the same
bitmaps; and amounts that always keep their centimes.

The printer's own text mode survives in exactly one place — the self-test — because a raster cannot
diagnose. A printer that ignores `GS v 0` and a printer that is switched off both produce blank
paper, and the strip is what a user is told to run when something is wrong.

## 6. One layout engine, three consumers

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

## 7. Accents and images, settled in the engine

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

## 8. Connection state — one sealed hierarchy for both transports

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

## 9. Permissions (phase 2)

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

## 10. PrinterSelectionScreen (phase 2)

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

## 11. File layout

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
│   └── TcpTransport.kt            raw socket + NetworkAddress, port 9100 ✔ phase 4
├── discovery/
│   ├── BluetoothScanner.kt     bonded devices + ACTION_FOUND              ✔ phase 2
│   ├── WifiInfoProvider.kt     SSID + local subnet, with WifiState        ✔ phase 4
│   └── NetworkPrinterScanner.kt   /24 sweep of port 9100                  ✔ phase 4
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

## 12. Phases

| Phase | Scope | Ships | State |
|---|---|---|---|
| **0** | `PrintSettings` + store + `PaperProfile` + `ThermalLayout` + `ThermalRaster` + `PrinterCodePage` + preview. No hardware. | Settings screen with a working live preview — reviewable before touching Bluetooth | **done** |
| **1** | Merge receipt settings in; rename the card to "Reçus et impression" | The reorganisation of §3 | **done** |
| **2** | BT permissions, `BluetoothSppTransport`, `PrinterSelectionScreen`, `PrinterGate`, `EscPosRenderer`, `TestPrint` calibration strip | Real printing, ESC/POS only | **code done, unverified against hardware** |
| **3** | `ReceiptPrinter` behind the print button in `ReceiptPreviewSheet`, which Achats, Dépôt Ventes and Tournées Ventes all share; A4 keeps the PDF path; PDF offered as the fallback on every failure | Hardware printing in production | **done** |
| **4** | `TcpTransport` (port 9100) + SSID display + manual IP + a local-subnet sweep | Wi-Fi | **done** |
| **5** | TSPL / CPCL renderers | Compatibility | |

Phase 0 de-risks the module: the layout engine is where the real design decisions live, and it is
testable on a desk with no printer (`app/src/test/.../data/print/`).

---

## 13. Phase 3 — the button

One button changed, in `ReceiptPreviewSheet`, and all three screens followed, because Achats, Dépôt
Ventes and Tournées Ventes open the same sheet.

**The paper picks the route, silently.** A4 keeps `ReceiptPdfGenerator` + `PrintManager`; 58/80 mm go
to the thermal printer. The user is not asked which they meant — they already said, on the settings
screen — but the button states where it is about to send the receipt underneath itself
(`printTargetLabel`), because printing is the one action here whose outcome depends on a setting made
somewhere else. A paper size should not be a surprise discovered on the roll.

**The A4 half stays in the composable.** `PrintManager` refuses an application-scoped context, so the
PDF path needs the Activity's; only the thermal half moved into `ReceiptPrintViewModel`.

**A4 is declared, not left to the dialog.** `printReceiptPdf` passed an empty `PrintAttributes`, and
the device walk showed the resulting dialog defaulting to **US Letter** — so choosing "A4" in the
settings produced a print dialog offering Letter. It now sets `MediaSize.ISO_A4`, which is also
simply true of the document: `ReceiptPdfGenerator` draws at 595×842 pt. §2 says the OS dialog owns
the media size, and it still does — but it owns it better when told what the document is.

**The sale is already committed when any of this runs.** So a printer that is off, out of range or
unconfigured is never phrased as a failed sale, nothing is retried automatically, and *Partager en
PDF* sits beside *Réessayer* on every failure — the share sheet the screen already had. An
unconfigured printer drops the retry entirely, since there is nothing to retry until one is chosen on
another screen, and says where to choose it.

Nothing records that a receipt was printed. That was a deliberate call: it would be a Room column and
therefore a migration, and the schema stays untouched at this stage. See the open questions.

## 14. A receipt is a running cost

A shop buying rolls by the box notices the difference between a twenty-line receipt and a thirty-line
one, so the layout is built to be short. Four decisions, in descending order of how much paper they
save:

**Font B, not Font A.** Every ESC/POS printer carries exactly two built-in bitmap fonts, selected
with `ESC M n`; there is no scale between them and no third choice. Font B is 9×17 dots against Font
A's 12×24 — roughly a third less height per line, and 42/64 characters per line instead of 32/48, so
fewer lines wrap in the first place. The trade is real and deliberate: 9×17 is small print, and
anyone who finds it too small wants `PrinterFont.A`, which the profile already takes.

**Tight line spacing.** The factory default feed is about 30–34 dots, sized for Font A's 24-dot
glyphs and generous even for those; against Font B's 17 it spends a third of the roll on white space.
`ESC 3 20` leaves three dots of gap — enough to keep descenders off the next line's capitals — and
saves ~1.7 mm on *every* line, which is about 5 cm on a twenty-line receipt.

**No blank line between items, and none anywhere else.** The indent on an item's figures line already
groups it with its name; a separator per item is a line of paper per item. The only blanks left in the
whole receipt are the two at the end, and those are not padding — they feed the last printed line
clear of the tear bar, which sits ~15 mm above the head.

**No QR.** It cost about a centimetre of roll on every receipt and encoded only the title, date and
total that the receipt already prints in words, so nothing could be learned by scanning it that
reading it did not already give. `ReceiptRow.Qr` and the `GS ( k` emitter went with it rather than
being left as an unused path to rot; the A4 PDF keeps its QR, where the space is free.

One consequence worth stating: at 42 columns the narrow roll *could* now rule a single-line item
table, and deliberately does not. 42 minus the three numeric columns leaves twelve characters for a
product name, and "Lait Candia demi-écrémé 1L" wrapped over three continuation lines is longer than
the two-line form, not shorter. `MIN_COLUMNS_FOR_TABLE` is set to 50 to keep that from happening by
accident.

### The preview follows the profile, not a guess

The on-screen line spacing used to be a chosen multiple of the font size. It is now derived:
the gap between two lines is `lineSpacingDots` wide in the same dots the characters are
`glyphWidthDots` wide in. Tighten `ESC 3 n` and the preview tightens with it — which is the only way
the two can stay honest about a receipt's length.

## 15. Wi-Fi: a second pipe, not a second app

Phase 4. A network printer is the same dumb byte pipe as a Bluetooth one — open a TCP socket to
**port 9100**, write ESC/POS, close — so almost nothing above the transport changed.

**What is genuinely different is how it fails.** Bluetooth fails on a radio that is off, a permission
that was refused, a pairing that was removed. A network printer has none of those: it has an address
and a question of whether this phone is on a network that can reach it. So `PrinterGate.check` now
branches by the printer's own `method`, and there is no shared checklist to factor out — a radio can
be switched off, a subnet cannot. `NO_NETWORK` joins the failures, with "Réglages Wi-Fi" as its action.

`transportFor()` lives on the gate rather than on `ReceiptPrinter`, because the gate is also what
decided the printer was reachable: one place choosing Bluetooth-or-network means the check and the
send cannot disagree about which kind of printer this is.

### Typing the address is the mechanism; the sweep is the convenience

A printer prints its own IP on its self-test page, so **Ajouter par adresse IP** is the path that
always works — including for a printer on another subnet, or behind a router that blocks the sweep.
Port 9100 is pre-filled and rarely touched, but editable, because the one printer that listens
elsewhere would otherwise be unreachable with no way to say so.

**Chercher sur le réseau** knocks on port 9100 across the local /24, 48 probes at a time with a
400 ms timeout each — crude, and deliberately so. The polite alternative is mDNS, but the till
printers this is for are cheap network modules that answer on 9100 and advertise nothing; mDNS finds
the office machines that should be going through Android's own print service anyway. Anything
answering on 9100 is a print server by convention, so unlike the Bluetooth scan there is no guessing
about what a result is.

The sweep needs **no permission at all** — just a local IPv4 address to sweep from.

### The SSID is reassurance, not a requirement

Printing never needs the network's name; a printer is reached by address. It is shown because an
unreachable network printer's usual cause is the phone having drifted onto mobile data or onto the
neighbour's router, and that is invisible unless it is said out loud.

Android will report Wi-Fi as connected while refusing to name it: since API 27 the SSID is gated
behind the location permission *and* location services actually being on, because knowing which
Wi-Fi you are on is knowing roughly where you are. `WifiState` therefore has a distinct `Unnamed`
case carrying which of the two is missing — an app that renders that as an empty string looks broken,
and one that demands the permission is asking for location to print a receipt. It explains instead.

### Two things the device walk turned up

**The SSID came back empty on a phone with location on.** From API 31 the `WifiInfo` carried on a
network's capabilities is *redacted* — the SSID reads `<unknown ssid>` — unless the caller asked for
location info when registering a `NetworkCallback`, which a synchronous read has not. The first
version stopped there and reported "nom du réseau masqué (localisation désactivée)" on a phone whose
location was switched on: false, and the sort of message that sends a user to change a setting that
was never the problem. It now falls back to the deprecated `WifiManager.connectionInfo`, which still
answers for an app holding the location permission, and a third `UNAVAILABLE` reason covers the case
where everything is granted and Android still will not say. The clean fix is
`registerNetworkCallback` with `setIncludeLocationInfo(true)`, which would turn this into a
subscription — worth doing if the SSID ever becomes more than reassurance.

**A pasted `host:port` was mangled.** The dialog has a separate port box, and the first version
appended it to whatever was in the address field — so pasting "192.168.1.50:9100" off a self-test
page produced host `192.168.1.50:9100` on port 9100, a printer nobody can reach. A port typed into
the address now wins, in `NetworkAddress.resolve`, which is a pure function precisely so the rule is
pinned by a test rather than buried in a ViewModel.

## 16. Selection is earned by connecting

Reported from the field after phase 3: a printer could be tapped and become the active one while it
was switched off, flat or still at the depot. Nothing checked, so the first anyone learned of it was a
client waiting at the counter.

A tap on a saved printer now opens a real connection first and the selection follows the outcome:
**Connexion en cours…** while the socket is opening, and on a refusal **Non connectée — appuyez pour
réessayer** on that row, with the printer *not* selected. Adding one from the paired list goes through
the same path, so a printer never becomes active merely by being saved.

Three rules keep it from being annoying rather than safe:

- **A failure never un-selects what already works.** Only a successful probe changes which printer is
  active; a failed one marks the row it was tried on and leaves the previous choice alone.
- **Device-wide blockers go to the banner, printer-specific ones to the row.** A radio that is off
  stops every printer, so "Bluetooth désactivé" belongs above the list. One unit not answering belongs
  on that unit's row.
- **Removing the selected printer clears the selection** rather than promoting another. Promoting one
  would name a printer nobody has connected to, which is the whole thing this prevents.

The cost, accepted deliberately: with the printer switched off there is no way to have one selected,
so the receipt sheet reads "Aucune imprimante" until it answers. That is the honest state, and the
PDF fallback covers it.

## 17. The paper chips edit what is in force

Also reported: with a printer selected, changing "Format du papier" moved the chip and left the
preview unchanged.

Not a Compose reactivity fault. The preview renders `effectivePaper`, which is
`selectedPrinter?.paper ?: defaultPaper`, while the chip wrote `defaultPaper` unconditionally — so
with a printer selected the control was editing a value nothing on the screen was showing. The chip
and the preview were both right about different things.

`setPaper`/`setLanguage` now write to whatever the value belongs to: the selected printer when there
is one, the device default otherwise. The chips display `effectivePaper`/`effectiveLanguage` for the
same reason, and each caption names its scope — "réglage de BT SPEAKER" or "défaut pour les nouvelles
imprimantes" — so the per-printer model is visible instead of surprising.

## 18. Found on the device, phase 2

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

## 19. Noted while building

**`File.renameTo` does not overwrite.** `PrintSettingsStore` originally used the temp-file-and-rename
that `AutoBackupStore` uses, and every write after the first one threw: `renameTo` is specified to fail
when the destination exists, and does on some filesystems. It happens to work on the phone's ext4/f2fs,
so the device never showed it and the JVM tests did immediately. `PrintSettingsStore` now uses
`Files.move(…, REPLACE_EXISTING)`.

**`AutoBackupStore.update` still has the original form.** It is not broken on Android for the same
reason, and it is outside this module, so it was left alone — but it is the same latent bug, and if
that store ever gains a desktop or JVM-side test it will surface there first.

## 20. Open questions

- **Which code page do the printers on the ground actually honour?** Now only affects the self-test
  strip, since the receipt carries no text bytes at all. CP1252 (`ESC t 16`) is the assumption;
  CP858 (`ESC t 19`) is the fallback.
- **The Canvas renderer has never met a printer.** It compiles and the layout around it is unit
  tested, but `CanvasReceiptRenderer` needs a real `StaticLayout` and `Bitmap`, so nothing below the
  row model is covered by tests. The first print on hardware is the real review — especially the
  threshold (too heavy and small text fills in, too light and it breaks up) and the Arabic shaping.
- **Font B for 58 mm?** 42 chars instead of 32 would let the 58 mm layout keep the single-line table.
  It is small print on already-small paper; the two-line layout was chosen instead. Revisit if users
  complain about receipt length rather than legibility.
- **Reprint history.** Decided for now: **not tracked**. Nothing records that a receipt was printed,
  and the schema is untouched. If disputes or audits make it matter, it is a Room column and therefore
  a migration, and the receipts printed before that point will have no history to show.

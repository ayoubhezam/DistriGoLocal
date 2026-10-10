# UI Fluidity — Progress Summary (2026-10-10, sprint closed)

Follow-up to the UI Fluidity & Frame-Rate Audit (`docs/audit/ui_fluidity_and_fps_audit.md`), which holds the full
measurements. All numbers come from the Galaxy M34 (120 Hz) holding the stress data (~15k products, ~40k sales,
~1,500 clients). Unless noted, they were measured on the release-like **benchmark** build.

## The headline

Most of the "heavy" feeling came from testing a **debug build**. In a release-like build, the lists and swipes keep up
with the 120 Hz screen. The hotspots the measurements found are fixed: one freeze, slow typing, and slow pickers.
Every screen opening was then traced, and fixed where a cost could be found. On the release build, the app now feels
production-ready on the device. The remaining audit items are paused so feature work can resume.

## Completed

| Step | What was done | Result | Commit |
|---|---|---|---|
| 0 — Measure | A `benchmark` build type (release code, installable over debug), a scripted measurement walk, Compose compiler reports | Debug build: flings 12–26 % janky, cold start 1.8–2.0 s. Benchmark build: flings < 0.5 %, cold start 0.37–0.40 s | `e8b02b7` |
| 2 — Dropdown freeze | The four big filter dropdowns (Dépôt Vente, Achats, tournée, Mouvements) replaced by a searchable lazy list | Client filter: **1,400 ms** freeze (debug) / **300 ms** (release) → 61 ms | `03e564b` |
| 1 — R8 + Baseline Profile | Code shrinking (no renaming, so crash reports stay readable); an app startup profile recorded on the phone | APK 43 → **27 MB**; cold start → **0.28–0.31 s**; slowest frames roughly halved | `c31cb35` |
| 7 — Search off the main thread | Clients, debtors and Produits searches: filtering on a background thread once typing pauses; a key redraws only the search box | Clients typing: 95th percentile 30 → **16 ms**, slow UI-thread frames 12 → 3 | `78bb76b`, `52afd09` |
| Openings — traced | A `tracing` build and Perfetto traces of the slow screen openings | Composition is small (1–3 ms). The cost is window creation, measure/layout, draw recording and text layout | `a455721` |
| Openings — fix 1 | The filter pickers show their list inside the filter sheet (no second window); Back returns from the list to the filters | Picker opening 61–77 ms → **28–34 ms** | `5da5b75` |
| Openings — fix 2 | Report amounts (`FitText`) settle in the layout pass that measures them; the animated figures lost their hidden measuring copy | Text layouts per Ventes report opening **102 → 43** (7.3 → 3.7 ms); measure/layout 29.3 → 23.8 ms | `4cc0eaa` |
| Openings — fix 3 | One full-screen background less on every frame: the window's own background is white, and the app's root no longer paints over it | Screen painted 5× or more: Produits 69 → **19 %**, Ventes report 13 → **2 %**. No speed change on this phone: its GPU absorbed the extra layer | `c9d6306` |
| Openings — fix 4 | Compose UI 1.9 (pulled in by a dependency) sent "no preferred frame rate" twice on every frame, and Samsung's Android 16 logs each call with a stack trace (14 % of the main thread's work). Turned off at app start | Main-thread drawing per frame while scrolling 0.7 → **0.3 ms**; per screen opening 20–27 → **7–10 ms**. Still 120 Hz | `7e8fcd8` |

## Where things stand

**Scrolling and swipes** (benchmark build, measurement walk before the opening fixes):

| | Janky frames | Slowest frame |
|---|---|---|
| Produits / Dépôt Vente fling | 0.5–0.8 % | 19–21 ms |
| Client / product tab swipes | 1.4–2.0 % | 18 ms |
| Product form tab swipe | 2.2 % | 36 ms |
| Cold start | — | 0.24–0.32 s (2026-10-10) |

Fix 4 then roughly halved the main thread's work per scrolling frame:

| Screen (6 flings, median per frame) | Before | After |
|---|---|---|
| Dashboard | 0.81 ms | 0.38 ms |
| Produits | 1.09 ms | 0.69 ms |
| Dépôt Vente | 1.16 ms | 0.69 ms |

**Screen openings after the four fixes.** Measured on the tracing build, 5 openings each. The slowest frame is the
main thread's longest frame of the opening, where the new page is built. This is a different measure from the walk's
frame times above, so the two can't be compared directly.

| Opening | Slowest main-thread frame | Frames the app itself misses |
|---|---|---|
| Filter picker (client list) | 21 ms | 1 |
| A sale's page | 22 ms | 1 |
| Ventes report | 28–30 ms | 1 |
| A client's page | 34 ms | 1 |

Each opening misses only its first frame, the one that builds the page. After that the app keeps ahead of the
display. Nothing in the measured walk freezes, and no frame takes over 100 ms.

## Paused (not started)

- **Product form tab swipe** (roadmap Step 3): 2.2 % janky, 36 ms slowest frame. Read the swipe offset in
  layout/draw rather than in composition.
- **Client page's first frame:** its 34 ms is spread over about twenty small parts. Building the tabs one frame
  later would save a few milliseconds but make them pop in during the slide, so it was left as it is.
- **Polish:**
  - photo rows' one-frame placeholder blink and per-row disk check;
  - client/supplier lists converted off the main thread;
  - the drawer and keyboard redrawing the whole shell;
  - the cart chevron and report bar animations.
- **Housekeeping:**
  - align the Compose versions (UI 1.9.0, foundation/animation 1.7.5, Material 3 1.3.1);
  - lifecycle-aware data collection;
  - re-record the baseline profile once the code settles.

## Notes

- **Judge the app's feel on the release-like build.** The debug build, which Android Studio's Run button installs,
  is 12–29 % janky and starts in about 2 s. Testing it is what made the app feel "heavy" in the first place, and
  again on 2026-10-10.
- Measure frame rate only on the benchmark build, never on debug.
- For typing, judge frame times and slow UI-thread frames, not "janky %". With the keyboard up, the cursor's own
  blinks count as late on every screen.
- In traces, count only "App Deadline Missed" frames as the app's own. "SurfaceFlinger Stuffing" and "Buffer
  Stuffing" come from the system, or from the app running ahead of the display.
- Nothing has been pushed.

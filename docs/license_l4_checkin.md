# L4: automatic check-in (plan)

**Status: approved by the owner on 2026-10-15, with the six recommended decisions of §12. Built step by step from L4a (§13).**

This expands the four lines of `docs/license_architecture.md` §8 (L4), written on 2026-10-10, before
activation codes, suspension and the new Compte existed. The protocol is unchanged (§2.3, §6.4).

---

## 0. In one page

Today the phone checks its license only when someone taps « Vérifier » in Compte. L4 makes it check by
itself, quietly:

- **Every 12 hours in the background**, with WorkManager, only when there is a network. Android chooses the
  moment, folding it into its own battery-saving windows.
- **When the app comes to the front**, if the last check-in is more than 6 hours old (or something is due).
- **« Vérifier » in Compte**, as now.

One check-in is two small HTTPS calls (a few kilobytes) and one signature with the phone's key. It holds
no wake lock of its own and runs no service. Battery cost is negligible.

What a check-in brings back reaches the phone without any action from the merchant:
- a renewal (a code typed on another phone, or days you added): the new end date;
- a **suspension** from « Comptes clients »: the subscription shows « Suspendu »;
- a **reactivation**: back to « Actif » on its own (new in this plan, §6);
- a transfer to another phone: « Appareil libéré ».

If the app is open, the screen changes at once. Otherwise, the next opening shows it.

L4 makes the phone **know**. Making it **obey** (refusing new sales when expired or suspended) is L5, as
planned on 2026-10-10.

---

## 1. What changed since 2026-10-10

| Since then | Consequence for L4 |
|---|---|
| Activation moved into L3, and codes (C1–C3) | Nothing to activate here: L4 only keeps an activated phone current. |
| Suspension (« Comptes clients », C4) | Today a suspended phone is treated as *revoked*: it forgets its license, and after « Réactiver » the merchant must activate again. L4 makes suspension a state of its own that heals by itself (§6, decision 1). |
| The redesigned Compte, « Vérifié le … · Vérifier » | Stays; it now shows the last check-in, whoever made it. |
| `FunctionCalls` (one way to call the server, token renewed) | The background check-in uses it as is. |
| The onboarding on fresh installs | The « first-run invitation » of the old plan now only concerns phones that **already hold data** but no license (§8, decision 5). |
| The admin's « Dernière connexion » | Becomes meaningful: with L4 it moves every day for a phone in use. |

---

## 2. Android components

No new library. Everything below is already in the project.

| Piece | What it is | Role |
|---|---|---|
| **WorkManager 2.10** (`work-runtime-ktx`) with `HiltWorkerFactory` | Already set up in `DistriGoApplication` (on-demand initialisation, after a pending restore), used by the daily backup | Runs the check-in in the background, survives reboots and app kills, respects Doze and battery saver |
| `LicenseCheckInWorker` *(new)* | A `CoroutineWorker` built by Hilt (`@HiltWorker`), like `AutoBackupWorker` | One check-in, then tells WorkManager « done » or « try again later » |
| `LicenseCheckInScheduler` *(new)* | Like `AutoBackupScheduler` | Starts or cancels the 12-hour schedule; enqueues the one-off foreground check-in |
| `LicenseCheckIn` *(new)* | A small coordinator around `LicenseClient.checkIn()` | One check-in at a time for the whole app: the worker, the foreground trigger and « Vérifier » all go through it. A second request while one runs waits for its result instead of starting another. |
| `CheckInLog` *(new)* | A tiny file next to the license (`no_backup/license/`) | Last attempt, last success, last outcome, and who ran it (background, foreground, button). Feeds the battery hint (§3.4) and diagnostics. |
| `MainActivity` lifecycle (`ON_START`) | The app's single activity | The foreground trigger, and a fresh local evaluation (§5.4). No new dependency (`ProcessLifecycleOwner` is not needed with one activity). |
| `LicenseManager`, `LicenseClient`, `TrustedClock`, `LicenseStore` | Existing (L1–L3) | Unchanged in their role; `LicenseStore` gains the *suspended* mark (§6). |

**Not used, on purpose:** a foreground service (a notification would sit in the merchant's bar for a few
kilobytes); `AlarmManager` exact alarms (they wake the phone and need a special permission); listening to
connectivity broadcasts in the background (Android forbids it since 7, and WorkManager's network constraint
does the job); the « ignore battery optimisations » exemption (Google restricts it, and a 14-day offline
window does not need it).

---

## 3. Battery, network and Doze

### 3.1 The schedule

- **Periodic work, every 12 hours, with a 4-hour flex window:** Android may run it at any moment in the last
  4 hours of each 12-hour period. That freedom lets it fold the check-in into a moment the phone is awake
  anyway.
- **Constraint: network connected** (`NetworkType.CONNECTED`), mobile data included. Merchants work on mobile
  data; a check-in is a few kilobytes. Wi-Fi only would leave van phones unchecked for days.
- **No « battery not low » constraint.** Unlike the backup, a check-in costs almost nothing, and it matters
  most when the phone has been away for long, which is also when its battery tends to be low.
- **Retries:** on a network or server failure, exponential backoff starting at 30 minutes (30 min, 1 h, 2 h,
  4 h, then WorkManager's 5-hour cap). No retry for answers that won't change by retrying (§5.3).
- **One of each:** the schedule and the foreground check-in are *unique work* under fixed names, so starting
  the app ten times enqueues one check-in, not ten.

### 3.2 Doze and App Standby

- **Doze** (screen off, phone still): background work and network wait for Android's maintenance windows,
  frequent at first, then spread out to several hours. WorkManager hands the job to Android's scheduler,
  which runs it in the next window. A 12-hour rhythm against a 14-day offline window leaves days of margin.
- **App Standby buckets:** Android delays background jobs more for apps used less. A merchant who opens
  DistriGo every day stays in the « active » or « working set » bucket, where jobs are delayed by about two
  hours at most. A phone left unused for weeks drifts to « rare » (about once a day), which is still enough.
- **Battery saver on:** the job is deferred like the others. The foreground check-in still works.

### 3.3 Samsung's battery features

One UI can put apps to « sleep » or « deep sleep » (after a few days unused, or by the user's choice). In deep
sleep, background work may not run at all. We don't fight it. We compensate:
- the **foreground check-in** (§5.1) runs whenever the merchant opens the app with Internet, which a working
  merchant does every day;
- the **battery hint** (§3.4) tells the merchant when background check-ins have stopped.

### 3.4 Showing that the background is held back

Like the daily backup already does (`AutoBackupScheduler`): if no background check-in has succeeded for 3
days while the phone has been online (the foreground ones succeeded), Compte's subscription card adds:
« La vérification automatique semble bloquée par l'économie de batterie. » with a button to the app's
battery settings (`ACTION_APPLICATION_DETAILS_SETTINGS`). It is a hint, never a block (decision 6).

### 3.5 What it costs

Per check-in: two HTTPS requests (nonce, then refresh; the TLS handshake is most of the time), a few
kilobytes each way, one ECDSA signature in the phone's secure hardware (milliseconds), two small files
written. Twice a day, inside windows Android has already opened, this is negligible next to any messaging
app's sync.

### 3.6 The reduced tier

A phone that failed attestation (§5.3) gets a **one-day** offline window. Two background runs a day plus the
foreground check-in cover it for a phone in daily use. A reduced phone left offline for more than a day goes
to `CheckInRequired`, as designed.

---

## 4. When a check-in starts

| Trigger | How | Condition |
|---|---|---|
| **Background** | Periodic unique work `license-check-in`, 12 h | Scheduled on activation, sign-in with a license, and every app start (`KEEP`, so a schedule lost with WorkManager's data comes back). Cancelled on sign-out, account deletion, revocation, or when there is no license. |
| **App in front** | One-off unique work `license-check-in-now`, network constraint | At `ON_START`, if the last success is more than **6 hours** old, or the state is `CheckInSoon`, `CheckInRequired` or `Suspended` (decision 2). Offline, it waits in WorkManager and runs as soon as the network returns. |
| **« Vérifier »** in Compte | Direct call through `LicenseCheckIn`, with the spinner | Always; the result shows in Compte's message line, as today. |
| **Reboot** | WorkManager restores its schedule by itself | — |

---

## 5. The flow, step by step

### 5.1 What one check-in does

```
LicenseCheckInWorker (or the foreground one-off, or « Vérifier »)
  └─ LicenseCheckIn.run(source)              one at a time for the whole app
       1. auth.restore(); license.evaluate()   the process may have just started for this job
       2. not signed in, or no license         → nothing to do; cancel the schedule; done
       3. LicenseClient.checkIn()
            a. POST license-nonce {purpose: refresh}         (FunctionCalls renews the access token)
            b. sign "distrigo-refresh\n<nonce>\n<installation id>" with the device key
            c. POST license-refresh {nonce, installation_id, signature, app_version}
       4. the answer, applied to the phone (5.2)
       5. CheckInLog: when, which source, outcome
```

The signature uses the Keystore key, which needs no screen unlock, so a check-in works with the phone locked
in a pocket.

### 5.2 Each answer, and what follows

| Server answer | On the phone | License state | Worker result |
|---|---|---|---|
| `ok` + new license | `LicenseManager.install()`: verifies the signature, refuses an older `seq`, writes the license, re-anchors `TrustedClock` (new offline window, reboot count reset), **clears a suspension mark** | `Active`, `RenewSoon` or `Grace`, with the server's dates | done |
| `subscription_inactive` | `LicenseManager.suspend()` *(new)*: keeps the license and the key, marks it suspended | `Suspended` *(new)* | done; the schedule stays, so the reactivation is picked up |
| `revoked`, `not_member`, `unknown_device` | `LicenseManager.revoke()`, as today | `Revoked` (« Appareil libéré ») | done; schedule cancelled |
| network error, timeout, server error | nothing changes | unchanged | **retry** (backoff) |
| session expired (refresh token refused) | the account is signed out locally, as `FunctionCalls` already does | unchanged until the offline window ends | done; Compte asks to sign in again |
| *(no call)* key missing on the phone | the local evaluation finds it first (phone reset, Keystore cleared): `NoLicense`, « clé absente », as `LicenseManager` already judges it | `NoLicense` | done; schedule cancelled; a new activation is needed |
| `bad_signature` | the server doesn't recognise this phone's key; retrying won't change it | unchanged | done (no retry); « Vérifier » shows the message |
| `bad_nonce` | a nonce expired between the two calls | unchanged | retry |

### 5.3 Where it is stored

- **The license, the clock anchor, the suspension mark:** files in `no_backup/license/`, encrypted and sealed
  as in L1. They are not in Room, on purpose: a `.distrigo` backup must never carry a license.
- **`app_meta.business_id`** (Room, key/value, no schema change): the business the data belongs to, set at
  the first activation (§7).
- **`app_meta.license.write_gate`**: L5, not L4.
- **`CheckInLog`:** a small file next to the license.

### 5.4 The screen, when the app is open

`LicenseManager.state` is a `StateFlow` held by a singleton. The worker runs **in the app's process**, so a
check-in made in the background while the app is open updates the same flow. Compose redraws at once:
- Compte: the status pill, the dates, « Vérifié le … » (it shows the license's issue time, whoever checked);
- the avatar and, from L5, the banners and the blocked buttons.

Also while the app is open:
- **at each `ON_START`**, a local evaluation (about a millisecond, no network), so a date crossed while
  the app sat in the background (midnight into grace, say) shows at once;
- **every 15 minutes** while in front, the same local evaluation;
- **on a date or time change** (`ACTION_TIME_CHANGED`, registered only while the app is open), the same,
  so `ClockBehind` shows without a restart (§2.4 of the architecture).

If the app is not open, nothing is drawn: the next opening reads the files and shows the new state.

---

## 6. Suspension, a state of its own (decision 1)

**Today:** a check-in answered `subscription_inactive` calls `revoke()`. The phone forgets its license and
shows « Appareil libéré ». After your « Réactiver », the merchant must tap « Activer cet appareil »,
which makes a new key and a new attestation.

**Proposed:**
- A new state `Suspended(license)`. Recording is not allowed (enforced from L5), and Compte reads
  « Suspendu » (red pill) with « L'abonnement est suspendu. Contactez DistriGo. ».
- The license and the key stay. Background check-ins continue, and the first one after your « Réactiver »
  installs a fresh license: `Active` again, with nothing to do for the merchant.
- The mark is a flag in `LicenseStore`, like *revoked*, so it survives restarts. Only a successful check-in
  clears it.
- No server change: the server already refuses the license with `subscription_inactive` and keeps the
  phone's seat.

---

## 7. Data that belongs to another business (§4.7, decision 4)

- **On activation or the first successful check-in**, `app_meta.business_id` is set to the license's
  business (`org`), if it is empty.
- **If it differs** from the license's business, the phone shows « Ces données appartiennent à une autre
  entreprise ». This happens after a sign-in with another account, or after restoring another business's
  `.distrigo`. There are two answers:
  - « Les garder pour <entreprise> »: rebinds the data, for an owner who moved to a new account.
  - « Se déconnecter »: releases the seat.
- Until the merchant answers, the state is `OtherBusiness`. Recording is not allowed (enforced from L5).
- `RestoreStockRule` already warns before restoring a backup made under another rule. The same warning will
  name the business when the backup carries another `business_id`.

---

## 8. Phones with data but no license (decision 5)

The onboarding opens by itself only on fresh installs (C2). A phone that has used DistriGo without an
account (the beta phones) never sees it. L4 adds a card at the top of the Dashboard, in the new design
language: « Activez DistriGo » « Vos données restent sur ce téléphone. » [Activer]. It opens the onboarding,
and stays until the phone holds a license. Before L5 it can be dismissed for the day; from L5 it becomes the
way out of read-only.

---

## 9. Server

Nothing is required for the check-in itself: `license-nonce` and `license-refresh` are live since L2c,
`record_check_in` updates `last_seen_at`, and suspension already answers `subscription_inactive`.

To watch it work: « Comptes clients » already shows « Dernière connexion ». With L4, a phone in use moves it
every day.

---

## 10. Tests and the phone walk

**Unit tests (JVM)**, with `work-testing` (already in the catalogue):
- the worker's result for each answer in §5.2 (`TestListenableWorkerBuilder`, a server played by
  MockWebServer as in `LicenseClientTest`);
- the coordinator: two simultaneous calls make one check-in;
- the scheduler: scheduled on activation, cancelled on sign-out and revocation, `KEEP` on restart; the
  foreground trigger's 6-hour rule;
- `Suspended`: set by `subscription_inactive`, kept across a restart, cleared by the next `ok`;
- the business binding (§7).

**On the M34 (debug build), the « done » walk:**

| Step | Expected |
|---|---|
| Activate, then `adb shell dumpsys jobscheduler` | DistriGo's 12-hour job is listed with its network constraint |
| Force the job (`cmd jobscheduler run -f`) | « Vérifié le » moves; « Dernière connexion » moves in « Comptes clients » |
| Airplane mode, force the job | Retries are scheduled, nothing changes on screen |
| Back online, open the app | A check-in within a minute |
| Reboot | The job is still scheduled |
| Add days to the subscription on the server, force the job, app closed | Next opening shows the new end date |
| « Suspendre » from Administration, force the job, app open | « Suspendu » appears without touching the phone |
| « Réactiver », force the job | « Actif » again, with no activation |
| `dumpsys batterystats` after a day | Only DistriGo's short jobs; no wake lock held |

---

## 11. What L4 does not do

- **No enforcement:** nothing is blocked yet, whatever the state. That is L5 (banners, `LicensePolicy`,
  the `LicenseWriteGate` triggers, the read-only allowlist).
- **No Play Integrity:** L6.
- **No push from the server** (Firebase Cloud Messaging) to force an immediate check-in after a suspension: a
  new dependency and a Google service for little gain over 12 hours. It can come later if suspensions must
  bite within minutes.

---

## 12. Decisions for the owner

| # | Question | Recommended | Alternative |
|---|---|---|---|
| 1 | Suspension | **Its own state; reactivation heals by itself** (§6) | Keep treating it as revoked (merchant re-activates) |
| 2 | Rhythm | **12 h in the background; in front after 6 h** | 24 h / 12 h (lighter), or 6 h / 3 h (suspensions bite sooner) |
| 3 | System notification | **Only when the phone must check in within 2 days and hasn't managed to**, on the backup's channel | Never (the screen alone) |
| 4 | Another business's data (§7) | **Block with the choice « Les garder » / « Se déconnecter »** | A warning only |
| 5 | Phones with data, no license (§8) | **A Dashboard card « Activez DistriGo »** | Nothing until the release (L7) |
| 6 | Battery hint in Compte (§3.4) | **Yes** | No |

---

## 13. Steps, once approved

Each step builds, passes the unit tests and is shown on the phone before the next.

1. **L4a: suspension as a state.** `Suspended`, the `LicenseStore` mark, the check-in's new mapping,
   Compte's « Suspendu ». *(Visible: suspend and reactivate from Administration, then « Vérifier ».)*
2. **L4b: the background check-in.** `LicenseCheckIn`, `LicenseCheckInWorker`, `LicenseCheckInScheduler`,
   `CheckInLog`, and scheduling on activation, sign-in, sign-out, deletion and revocation.
3. **L4c: the app in front.** The `ON_START` check-in and evaluation, the 15-minute evaluation, the time-change
   receiver, the battery hint, the notification (if decision 3 is yes).
4. **L4d: the data's business.** `app_meta.business_id`, the « autre entreprise » screen, the restore warning.
5. **L4e: the Dashboard card** (if decision 5 is yes).
6. **L4f: the phone walk** (§10), the architecture document's §8 updated, commit and push.

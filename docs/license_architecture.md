# Accounts, subscriptions and the offline license

Status: **decisions D1–D8 taken 2026-10-10** (section 9). Phase L1, the offline core, is in
`data/license/`.

---

## 0. In one page

- **Who you are** comes from an account: Google or e-mail/password, through the backend's auth.
- **What this phone may do** comes from a *license*: a small document the server signs (ECDSA P-256)
  that says "business X, plan Solo, valid until 2026-12-31, on the phone whose hardware key is K, as
  long as it checks in again before date O". The app verifies the signature offline with a public key
  compiled into it. The license is not a secret, so it needs no encryption. Only the signature
  protects it.
- **What time it is** is taken from the server's signed time at the last check-in, carried forward
  with the phone's monotonic clock (`SystemClock.elapsedRealtime()`, which the user cannot change
  and which keeps counting in deep sleep). The wall clock may only make the license *older*, never
  younger.
- **Which phone** is proved by a key pair generated in the Android Keystore that can never leave the
  phone. A copied app has the license file but not the key, so the license refuses to load.
- **When it ends**, DistriGo becomes *read-only*: everything can still be consulted, printed,
  exported and backed up, but nothing new is recorded. This is enforced the way strict stock already
  is: a policy shapes the screens (`StockPolicy`) and a rule in the database refuses the write
  (`DepotStockGuard`, `DocumentNumberTriggers`).
- **Backend: Supabase** (Postgres, Auth, Edge Functions). Firebase works too: sections 3–5 don't
  change, only the server does.
- **Business data never leaves the phone.** Only the account, the subscription and the device
  record are on the server. "Sync is out of scope until DistriGo Solo ships" still holds.

### What this can and cannot guarantee

No design makes an offline-first app tamper-proof on a phone its user controls. Code on the
device can be patched, and a rooted phone can run anything as the app. What this design achieves:

1. **Anything done with phone settings fails.** That includes changing or freezing the date, copying
   the app to another phone, sharing the account, reinstalling, clearing data, and restoring a backup.
2. **The only bypass left is modifying the APK or rooting the phone.** The server sees the
   modification the next time the phone checks in (key attestation, Play Integrity), and refuses to
   renew.
3. **A phone that never checks in stops recording after the offline window (D2, proposed 14 days)**,
   unless its APK has been modified.

What it does *not* stop is a modified APK that never goes online again. The only counter is to make
the online side worth having: updates, support, and later cloud backup and sync. Commercial RASP tools
(DexGuard, Promon and the like) raise the cost of patching, but they cost more than they are worth
for Solo. Revisit for Business.

| Attempt | Who | Stopped by | Residual |
|---|---|---|---|
| Set the date back after expiry | anyone | Monotonic floor (§3) | none |
| Turn off automatic time and "freeze" the date each morning | anyone | Monotonic floor; reboot cap | at most stretches the *offline window*, never past the next check-in |
| Never connect again | anyone | `offline_until` in the signed license | none |
| Sign in on a second phone | anyone | Seat limit, transfer revokes the first (§4.6) | first phone keeps working until its `offline_until` if it stays offline |
| Copy app data to another phone (device transfer, root copy) | anyone | License bound to a non-exportable Keystore key; `noBackupFilesDir` isn't transferred | none |
| Clear data / reinstall to reset state | anyone | No license, so the phone must activate online; server knows the account | none |
| Restore an old `.distrigo` | anyone | The license isn't in the backup; database bound to a business (§4.7) | none |
| Edit the license or clock files | root | ECDSA signature; HMAC with a Keystore key | root can run code as the app → flagged at next check-in |
| Patch the APK to skip checks | reverse engineer | Attestation shows a different signing certificate → no renewal | works while offline forever |

---

## 1. Three tokens, three jobs

The usual mistake is to treat the auth provider's JWT as the license. It expires in an hour, it isn't
bound to a phone, and it's signed with the provider's keys rather than ours. Keep the three apart:

| | Auth session | License | Integrity evidence |
|---|---|---|---|
| Proves | *who* is asking (the account) | *what this phone may do*, offline | *what* is asking (genuine app, genuine phone) |
| Issued by | Supabase Auth | our Edge Function, our ES256 key | Android Keystore (attestation), Google Play (Integrity) |
| Lifetime | access 1 h, refresh token long | until `offline_until` (≈ 14 days) | single use |
| Needed offline | no | **yes, at every launch** | no |
| Stored | encrypted (it is a bearer secret) | plain file, signed | never stored |

Losing the session (signed out, token expired) doesn't stop the app. The license keeps it working until
`offline_until`. Losing the license makes the app read-only even if the session is valid.

---

## 2. Blueprint

### 2.1 Components

```
 PHONE                                                         SERVER (Supabase)
 ─────────────────────────────────────────────────────         ─────────────────────────────────
 ui/account/      Compte screen, sign-in, banners              Auth          Google ID token, e-mail
 data/auth/       AuthRepository (session, encrypted)  ──────▶               + password, verification
 data/license/                                                 Postgres      businesses, memberships,
   LicenseManager    StateFlow<LicenseState>                                 subscriptions, devices,
   LicenseVerifier   ES256 + binding checks                                  license_issuances
   TrustedClock      monotonic floor (§3)                      Edge Fns      license-nonce
   DeviceKey         Keystore EC key + attestation     ──────▶               license-activate
   LicenseStore      noBackupFilesDir/license/                               license-refresh
   LicenseWriteGate  SQLite triggers (§2.7)                                  device-release
   LicenseRefreshWorker  WorkManager, network-constrained                    account-delete
 data/device/DeviceIdentity  (exists: installation id)         Secrets       ES256 private key(s),
                                                                             Google service account
                                                               Google APIs   Play Integrity decode,
                                                                             attestation revocation list
```

### 2.2 First activation

```
 App                                   Server                              Google
  │ sign in (Google / e-mail)  ───────▶ Auth: session for user U
  │ POST license-nonce          ───────▶ nonce N (single use, 5 min)
  │ Keystore: generate EC key K
  │   with attestation challenge N
  │ Play Integrity token, requestHash
  │   = SHA-256(N ‖ iid ‖ hash(K))     (only if D5 = Play)
  │ POST license-activate               verify attestation chain of K:
  │   {iid, K chain, integrity,  ─────▶  challenge = N, our package and
  │    android_id hint, model}           signing cert, TEE/StrongBox,
  │                                      verified boot               ───▶ revocation list
  │                                     decode integrity token       ───▶ decodeIntegrityToken
  │                                     business of U, subscription,
  │                                     seat check (one transaction)
  │                                     insert device (K, iid)
  │                     ◀───────────────  license L (seq 1), iat = server time
  │ verify L offline, anchor clock at L.iat, store L
  │ app_meta.business_id := L.org (or check it, §4.7)
```

### 2.3 Refresh (online sync of the license)

It runs on its own, so the offline window is rarely approached:

- `LicenseRefreshWorker`: periodic, `NetworkType.CONNECTED`, every 12 h, through the existing
  `HiltWorkerFactory`;
- on app foreground, if the last check-in is more than 6 h old and the phone is online;
- "Vérifier maintenant" in the Compte screen.

```
 App                                                Server
  │ POST license-nonce                       ─────▶ N
  │ sig = K.sign("distrigo-refresh" N iid)          (proves the phone still holds K:
  │ POST license-refresh {iid, N, sig,       ─────▶  a copy of the app can't sign)
  │   integrity?, signals, clock report}            device active? subscription? integrity policy?
  │                              ◀──────────────────  new L (seq+1, iat = now), account summary
  │ verify, re-anchor clock at L.iat, store
```

The answer carries everything the server knows, so a renewal, a revocation, a plan change or a new
offline window reaches the phone at its next check-in, without an app update. A phone told it has been
revoked becomes read-only at once. It doesn't wait for `offline_until`.

### 2.4 Offline validation

`LicenseManager` evaluates the license at process start (before the database's first write, the
way `RestoreStartup` is ordered), on every foreground, every 15 minutes while open, and whenever
`ACTION_TIME_CHANGED` arrives. An evaluation reads two small files, verifies one signature (about a
millisecond) and asks `TrustedClock` for the time. The result is a `StateFlow<LicenseState>`.

### 2.5 States

`t` is `TrustedClock.effective` (§3). All dates come from the signed license.

| State | Condition | Banner (French UI) | Writes |
|---|---|---|---|
| `Active` | `vf ≤ t ≤ vt` | none | yes |
| `RenewSoon` | `vt − t < 7 days` | « Votre abonnement expire le 31/12/2026. » | yes |
| `CheckInSoon` | `ou − t < 3 days` | « Connectez-vous à Internet avant le 24/10 pour continuer à enregistrer. » | yes |
| `Grace` | `vt < t ≤ gu` | « Abonnement expiré le 31/12. Période de grâce jusqu'au 07/01. » | yes |
| `Expired` | `t > gu` | « Abonnement expiré — consultation seule. Sauvegarde et export restent disponibles. » | **no** |
| `CheckInRequired` | `t > ou`, or reboots since anchor > `mb`, or clock state missing or altered | « Vérification de l'abonnement nécessaire : connectez-vous à Internet. » | **no** |
| `ClockBehind` | wall clock more than 24 h behind the floor | « La date du téléphone est en retard. Activez « Date et heure automatiques ». » | **no** |
| `Revoked` | the server said so | « Cet appareil n'est plus associé au compte. » | **no** |
| `NoLicense` | never activated, signed out, or key lost | sign-in screen | **no** |

Wall clock 10 minutes to 24 hours behind: an amber banner only. Past 24 hours, recording is blocked
because every new document would carry a wrong date, whatever the license says. `docs/data/timestamps.md`
assumes `Instant.now()` is right.

**Read-only still allows** consulting everything, the reports, printing a copy of an existing
receipt, Excel/CSV export, `.distrigo` backup and restore, Paramètres, and the Compte screen. A
business's records must never be held hostage.

### 2.6 Expiry rules that fit van sales

- The server sets `vt` and `gu` to **23:59:59 Africa/Algiers** of the day, so a license never
  ends at 10:00 in the middle of a tournée.
- **D4:** a tournée started before the cut-off can still be closed (retour camion, déchargement)
  while read-only, so stock isn't left stranded in the truck.

### 2.7 Enforcement, in two layers like strict stock

| Layer | Strict stock (today) | License (proposed) |
|---|---|---|
| Shapes the screens | `data/model/StockPolicy.kt`, read by form ViewModels | `LicensePolicy`: `canRecord`, banner. Read by the tab roots (FABs, quick actions on the Dashboard), the form entry points and the Compte screen |
| The rule | `DepotStockGuard`: rolls the transaction back | `LicenseWriteGate`: `BEFORE INSERT/UPDATE` triggers raise `LICENSE_READ_ONLY` |

The gate follows `DocumentNumberTriggers` and `UpdatedAtTriggers`: built from a list on every
database open, so every write path is covered, including ones written later. It covers the document
headers (`ventes`, `purchase_orders`, `retour_client`, `retour_fournisseur`, `pertes`,
`inventory_sessions`, `chargement_sessions`, `chargements`, `charges`, `client_payments`,
`supplier_payments`, `tournees`) and the stock ledger. The exact list is settled in L5, with a test
that fails when a table that has a `numero` or writes to the ledger isn't covered.

```sql
CREATE TRIGGER `trg_ventes_license` BEFORE INSERT ON `ventes`
WHEN (SELECT `value` FROM `app_meta` WHERE `key` = 'license.write_gate') = 'closed'
BEGIN SELECT RAISE(ABORT, 'LICENSE_READ_ONLY'); END
```

`license.write_gate` lives in `app_meta`, so it travels with the database. `LicenseManager` rewrites it
at every open, before any write, and on every state change, so the value a backup carries is always
overwritten. The repositories turn `LICENSE_READ_ONLY` into the French message, as they already do for
`DepotStockException`. The UI layer means users almost never reach the gate. Like `DepotStockGuard`,
it is the rule that holds whatever a stale draft or a forgotten screen attempts.

---

## 3. Offline clock validation: `TrustedClock`

### 3.1 Time sources and how far each is trusted

| Source | Can the user change it? | Used for |
|---|---|---|
| `iat` in the signed license | no (signed by our key) | **the anchor**: the only value allowed to move the floor *down* |
| `SystemClock.elapsedRealtime()` | no; counts deep sleep; restarts at 0 on reboot | carrying the floor forward |
| `Settings.Global.BOOT_COUNT` | no (system setting) | detecting reboots, capping them |
| `SystemClock.currentNetworkTimeClock()` (API 33+) | no (NITZ/NTP, not the user's setting) | pushing the floor forward |
| `System.currentTimeMillis()` with `Settings.Global.AUTO_TIME` = 1 | not without turning automatic time off | pushing the floor forward |
| `System.currentTimeMillis()` with automatic time off | yes | never moves the floor. Compared with it to detect a rollback |

One rule makes this safe: **any source may move the floor forward**, because a later time only
shortens the license, which helps no attacker. **Only a server-signed time may move it back.**
So a bad forward value, such as a carrier sending a wrong NITZ time, heals at the next check-in.

### 3.2 State

`AnchorState`, in `noBackupFilesDir/license/anchor.state`: the state as JSON on the first line, and on
the second an HMAC-SHA256 seal made with a non-exportable Keystore key (`distrigo_anchor_mac`). Auto
Backup and device transfer skip it, for the same reason they skip `DeviceIdentity`. It is written to a
temp file and moved into place. A missing state, an edited one, or one in an unknown format reads as
none (`TrustedTime.Unknown`).

```
v            format version (1)
floor        proven lower bound on real time at the checkpoint (epoch ms)
elapsed      elapsedRealtime() at the checkpoint
boot         BOOT_COUNT at the checkpoint, null where the system keeps none
reboots      reboots seen since the last check-in
anchored_at  the server time of the last check-in
max_seq      highest license seq accepted (no replay of an older license)
```

### 3.3 Algorithm

As implemented in `data/license/TrustedClock.kt`:

```kotlin
fun read(): TrustedTime {
    val last = store.load() ?: return TrustedTime.Unknown        // missing or altered → CheckInRequired
    val elapsed = clocks.elapsedRealtime()
    val boot = clocks.bootCount()
    // A reboot is certain when the boot count moved or the monotonic clock went back. When neither shows
    // one, the floor still holds: at least (elapsed − last.elapsed) has passed, even across a reboot unseen.
    val sameBoot = elapsed >= last.elapsed && (boot == null || last.boot == null || boot == last.boot)
    val reboots = when {
        boot != null && last.boot != null && boot > last.boot -> boot - last.boot
        !sameBoot -> 1
        else -> 0
    }
    var floor = last.floor + if (sameBoot) elapsed - last.elapsed else elapsed
    clocks.networkTime()?.let { floor = maxOf(floor, it) }       // API 33+, null when unknown
    val wall = clocks.wall()
    if (clocks.autoTime()) floor = maxOf(floor, wall)            // set by the system, not the user
    val next = last.copy(floor = floor, elapsed = elapsed, boot = boot, reboots = last.reboots + reboots)
    store.save(next)                                             // checkpoint
    return TrustedTime.Known(wall, next)  // effective = max(floor, wall); behindBy = max(0, floor − wall)
}

/** A check-in: the server's issuedAt becomes the floor, even below the current one. */
fun anchor(issuedAt: Long, seq: Long)
```

Checkpoints are taken at every evaluation (§2.4) and when the app stops (`ProcessLifecycleOwner`
ON_STOP), so a reboot loses at most the time since the last one.

### 3.4 The attacks, step by step

- **Date set back after expiry, phone not rebooted.** `floor` keeps advancing with `elapsedRealtime`,
  `effective = max(floor, wall) = floor`, so the license stays expired. `behindBy` is large, so the
  state becomes `ClockBehind`.
- **Date set back, then reboot.** `floor = old floor + time since boot`. The time the phone was
  *switched off* is not counted (no monotonic clock sees it), but a field phone is rarely off. Deep
  sleep is counted.
- **"Freezing" the date (automatic time off, reboot and reset the date every day).** Every day still
  adds at least the phone's uptime to `floor`, so the date in the license still advances. The gain is
  the hours the phone is switched off, and it only stretches the **offline window**: at the next
  check-in, `iat` resets the floor to the server's time. **`vt` can't be stretched beyond what's left
  of the offline window.** The reboot cap (`mb` claim, proposed 30 boots since the last check-in)
  closes the "reboot many times" variant.
- **Clock set forward by mistake.** The license looks older and may show `CheckInRequired`. The fix
  is to correct the date, or check in online, which re-anchors. With automatic time off, a forward
  wall clock never enters `floor`, so correcting it later isn't seen as a rollback.
- **Time zone changes** have no effect: every value is UTC epoch milliseconds.

### 3.5 Parameters

Each one is a claim in the license or a server setting, so it can be tuned without an app update.

| | Proposed | Claim |
|---|---|---|
| Offline window | 14 days (D2) | `ou` = min(`iat` + window, `gu`) |
| Reboots between check-ins | 30 | `mb` |
| Grace after `vt` | 3 days (D3) | `gu` |
| Rollback banner / block | 10 min / 24 h | app constants |
| Renew-soon / check-in-soon banners | 7 days / 3 days | app constants |

---

## 4. The signed license

### 4.1 Format

A **JWS compact token, ES256** (ECDSA P-256, SHA-256). Every server language signs it with a standard
library (`jose` in an Edge Function). On the phone, the platform's
`Signature.getInstance("SHA256withECDSA")` verifies it on every API level from 26. The only conversion
is the JWS signature from raw `R‖S` (64 bytes) to DER: about 20 lines, with no new crypto library.
Ed25519 isn't used because the platform supports it only from API 33, and `minSdk` is 26.

Header: `{"alg":"ES256","kid":"lic-2026-a","typ":"distrigo-license+jwt"}`. The distinct `typ`
means no other JWT (the auth token, for one) can be mistaken for a license.

| Claim | Meaning |
|---|---|
| `iss` | `"distrigo-license"` |
| `sub` | account (user) id |
| `org` | business id: the subscription belongs to the business, not to the person (multi-role later) |
| `dev` | base64url SHA-256 of the device key's public key (SubjectPublicKeyInfo) |
| `iid` | installation id (`DeviceIdentity`) |
| `pkg` | `com.distrigo.app` |
| `plan` | `trial`, `solo`, … ; `feat`: feature list for later plans |
| `vf`, `vt`, `gu` | valid from, valid to, grace until |
| `iat` | server time at issue: **the clock anchor** |
| `ou` | offline until |

Times are whole **seconds** since the epoch (JWT NumericDate), as `jose` and every JWT library write
`iat`. The app converts them to milliseconds when it reads them. A truncated `iat` is still a valid
lower bound on the time.
| `mb` | reboots allowed between check-ins |
| `seq` | per-device counter, +1 at every issue |

### 4.2 Verification, in this order

1. Split into exactly three parts. The header must be **exactly** `alg = ES256`. Refuse `none`, and
   refuse `HS256`, because "verify HS256 with the public key as the secret" is the classic JWT
   forgery. Refuse a `typ` other than ours.
2. `kid` must be one of the public keys **compiled into the app** (`BuildConfig`, never downloaded).
   Verify the signature.
3. `pkg` must equal `context.packageName` and `iid` must equal `DeviceIdentity.id()`. The Keystore key
   `distrigo_device_v1` must exist, and its public key must hash to `dev`.
4. `seq ≥ maxSeq` (an older license can't be put back).
5. Compare the dates with `TrustedClock` (§2.5).

Any failure at steps 1–4 leaves the license unused and the state `NoLicense` or `CheckInRequired`.
The file is kept so that a check-in can replace it.

### 4.3 Signing keys

- The keys are generated offline. The private key goes into the Edge Function's secrets and into a
  second offline copy (password manager). It is never in the repository.
- Two keys are pinned in the app from the first release: `lic-2026-a` (in use) and `lic-2026-b`
  (generated now, stored offline, unused). If `a` leaks, the server switches to `b` at once, and the
  next release drops `a`.
- Debug builds also trust a **test key** whose private half is in the test resources, so L1 can be
  built and tested without a server. Release and benchmark builds don't trust it.

### 4.4 What is stored where

| What | Where | Protection | Why |
|---|---|---|---|
| License | `noBackupFilesDir/license/license.jws` | ECDSA signature | not secret; must not be cloned by Auto Backup or device transfer |
| Clock state | `noBackupFilesDir/license/anchor.state` | HMAC, Keystore key | integrity only |
| Revocation | `noBackupFilesDir/license/revoked` | — | present until a newer license is installed |
| Auth refresh token | `noBackupFilesDir/auth/session.bin` | AES-256-GCM, Keystore key | it is a bearer secret |
| Device key | Android Keystore (StrongBox if present, else TEE) | non-exportable | the device binding |
| Installation id | `noBackupFilesDir/device_id` (exists) | — | already designed not to be cloned |
| Business id | `app_meta.business_id` | travels with the database | ties the *data* to a business (§4.7) |

Nothing license-related goes in Room, `business_settings` or SharedPreferences: those travel in
`.distrigo` backups and Auto Backup.

**No EncryptedSharedPreferences.** All of `androidx.security:security-crypto` was deprecated in
1.1.0, and Google won't release it again. Encrypting a signed license adds nothing anyway. The one
secret, the refresh token, is encrypted directly with a Keystore AES key, which takes a few lines.

### 4.5 Device binding

- **Key:** EC P-256, `PURPOSE_SIGN`, SHA-256, `setAttestationChallenge(nonce)`, StrongBox when
  available (API 28+, fall back on `StrongBoxUnavailableException`). **No** user authentication
  requirement: many field phones have no screen lock, and a key bound to the lock is wiped when the
  lock changes.
- **Proof at every check-in:** the phone signs the server's nonce with K. A copy of the app has
  `license.jws` but not K: offline the license fails step 3, and online it can't sign.
- **Key lost** (factory reset, Keystore wiped by an OEM bug): activate again. Same account and
  same `iid`: the server replaces the key and no transfer is counted.
- **`ANDROID_ID` as a hint only:** it survives a reinstall on the same phone (it is per signing key,
  user and device), so the server can tell "same phone, reinstalled" from "new phone" and not count a
  transfer. It is never a security check, because root can fake it.

### 4.6 Seats and transfers (D6)

- `max_devices` per subscription, **1 for Solo** (server-side, so multi-role is a number change).
- Signing in on a second phone while the seat is taken:
  « Ce compte est actif sur « Samsung Galaxy M34 » (vu il y a 2 h). Transférer l'abonnement sur ce
  téléphone ? » A transfer revokes the first phone. It turns read-only at its next check-in, or at its
  `ou` at the latest.
- **3 transfers per 30 days**, then « contactez le support ». Without a limit, two phones could share
  one subscription by transferring back and forth.
- « Libérer cet appareil » in the Compte screen frees the seat (online).

### 4.7 Data belongs to a business

On the first activation, `app_meta.business_id` is set to the license's `org`. A license for
*another* business on the same database, whether after a sign-in with another account or after
restoring another business's `.distrigo`, shows « Ces données appartiennent à un autre compte » and
needs an explicit choice. Otherwise one subscription could be lent to read and write someone else's
records. The same id is what a future sync will need.

---

## 5. Integrity and reverse engineering

### 5.1 Hardware key attestation: the main check

It works **without Google Play services**, which matters if some users carry Huawei phones. The
server verifies the certificate chain of K at activation:

- the chain ends at one of **Google's attestation roots**: both the legacy RSA root and the ECDSA
  P-384 root that devices using Remote Key Provisioning have used exclusively since April 2026. Take
  the current list from Google's key-attestation page at implementation time, and check the
  revocation list;
- `attestationChallenge` = the nonce, and `attestationSecurityLevel` is TEE or StrongBox. Software
  means an emulator or a weak device;
- `rootOfTrust`: `verifiedBootState = VERIFIED`, `deviceLocked = true`. Anything else is an unlocked
  bootloader or a custom ROM;
- `attestationApplicationId`: package `com.distrigo.app` **and the SHA-256 of our signing
  certificate**. A repackaged APK has to be re-signed with another key, and it shows here. This is
  the strongest anti-repackaging check available, and it doesn't depend on Play.

### 5.2 Play Integrity: when distributed through Play (D5)

- A **standard request** at activation and at each check-in, with
  `requestHash = SHA-256(nonce ‖ iid ‖ dev)`. The token is decoded **on the server** through Google's
  API, never on the phone.
- The server checks `requestPackageName`, `requestHash`, a fresh timestamp,
  `appRecognitionVerdict = PLAY_RECOGNIZED`, the certificate digest, and that `deviceRecognitionVerdict`
  contains `MEETS_DEVICE_INTEGRITY`.
- **Caveat 1:** on Android 13+, since May 2025, the strong and basic labels need an app that Play
  installed or updated, and a sideloaded APK isn't `PLAY_RECOGNIZED`. **If DistriGo is sold as an
  APK, Play Integrity adds little, and attestation (§5.1) carries the check.**
- **Caveat 2:** phones without Google Play services get no verdict, so the verdict can't be a
  requirement.

### 5.3 Tiered response, decided on the server

| Evidence | Offline window | Action |
|---|---|---|
| Attestation good (+ integrity good, or unavailable) | plan default (14 d) | normal |
| Google's API down | plan default | never fail a paying customer for Google's outage |
| Software attestation, unverified boot, or unlocked bootloader | 1 day | flagged for review |
| Signing certificate mismatch, or Play says unrecognized on a Play install | refused | repackaged app |

The policy is server-side, so a false positive on some model is fixed in minutes, without an
update.

### 5.4 Local signals: report, don't lock

The signing certificate as `PackageManager` sees it (`GET_SIGNING_CERTIFICATES`, API 28+),
`FLAG_DEBUGGABLE` on a release build, the installer (`getInstallSourceInfo`, API 30+), a debugger
attached, root and Frida traces, emulator properties. These go in the check-in request and feed §5.3.
The app never locks itself on them: each is either easily patched or prone to false positives, and a
phone wrongly locked in the field costs more than a pirate.

### 5.5 R8: keep `-dontobfuscate`

`proguard-rules.pro` keeps class names on purpose: readable crash reports in Paramètres → Diagnostic,
and stable worker and draft class names. Obfuscation would add hours, not weeks, to an attacker's work
on a license check. The defense is the server refusing renewal, not the bytecode being hard to read.

---

## 6. Backend

### 6.1 Recommendation: Supabase

| | **Supabase** | Firebase | Custom (Ktor + Postgres) |
|---|---|---|---|
| Google + e-mail/password | yes (`signInWithIdToken`; verification, reset) | yes, the most mature on Android | to build |
| E-mail sign-in without Play services | yes | yes | yes |
| Server code to sign licenses | Edge Functions (TypeScript, Deno) | Cloud Functions (Blaze plan) | yours |
| Database | **Postgres + row-level security** | Firestore (documents) | Postgres |
| Play Integrity | call Google's decode API | App Check gate built in; license logic still custom | yourself |
| Production cost | Pro plan: free projects pause after a week idle | Blaze, pay as you go | server + operations |
| Exit | open source, self-hostable, plain Postgres | proprietary | — |

Why Supabase:

1. **The license server is a small relational system with invariants:** a seat count checked in a
   transaction, a transfer rate limit, and an issuance log. That is Postgres's home ground.
2. **One backend, not two.** When sync resumes, the groundwork already in the Room schema (`uuid`,
   `version`, `updated_at` triggers, tombstones, `origin_device_id`, per-device numbering) maps onto
   Postgres tables, and the account and business ids from this work are the ones sync will use.
3. **E-mail sign-in needs no Google Play services.**

Firebase is a fine alternative if you prefer Google's ecosystem. Everything on the phone (§2–§5)
stays the same.

### 6.2 Data model

**Built for Solo, designed for Business.** The schema is
`supabase/migrations/20261010120000_accounts_and_licenses.sql`. Its rules:

- **The business is the tenant.** Every row that belongs to a business carries `business_id`, even
  where a join would find it, so each row-level policy is one indexed check. Roles live in
  `memberships`, never in user metadata, which users can write themselves.
- **Business data is not here.** Clients, sales and stock stay on the phone, and each phone's database
  belongs to one business (`app_meta.business_id`, §4.7). The rule above applies to sync tables when
  Phase 4 comes.
- **No business is created on sign-up.** `create_business` is called explicitly at onboarding. A
  trigger would give every future agent a business and a trial of their own.
- **Two schemas.** `public` holds what the app may read, under row-level security. `private` holds
  the rest and the functions the Edge Functions call. The API doesn't expose `private`, and the app's
  roles hold no privilege there. Postgres's default `EXECUTE` for everyone is revoked.

```
public.plans          trial, solo: devices, offline days, grace, reboots, transfers a month, trial days
public.businesses     the tenant; name, time_zone (Africa/Algiers), created_by
public.profiles       display name, readable by fellow members (auth.users is not)
public.memberships    (business_id, user_id) → role owner | manager | agent, status active | removed
public.subscriptions  one per business: plan, status, valid_from / valid_to as LOCAL DATES (inclusive),
                      overrides of the plan (D7)
public.devices        one per installation: key, attestation, tier, status, seq, offline-days override
private.nonces · license_issuances · device_transfers · trial_claims · subscription_events · payments
```

- **Dates are local days.** In Studio, `valid_to = 2027-01-15` means "valid through the 15th":
  `issue_license` turns it into 23:59:59 Algiers time.
- **One trial per phone.** `trial_claims` is keyed by ANDROID_ID and survives account deletion, so
  deleting the account and signing up again earns no second trial.
- **Every subscription change is audited by a trigger**, an edit by hand in Studio included, in
  `subscription_events`.
- **Payments work with any gateway.** `(provider, external_ref)` is unique, so a retried webhook
  extends once.
- **What the app can read:**
  - a member reads their business, its members' profiles, its subscription and its plan;
  - owners and managers read every device of the business, an agent only their own;
  - a device's key, attestation, installation id and hints are never readable (column grants);
  - the app can rename the business (owner or manager) and its own profile, and **writes nothing
    else**.

### 6.3 Functions

The rules that must hold under concurrency live in SQL, in `private`. The Edge Functions do the
cryptography: attestation, the device key's signature, signing the license. They compose the SQL calls
in one transaction over a direct Postgres connection (`SUPABASE_DB_URL`).

| SQL (`private.`) | Does |
|---|---|
| `create_business` | the business, its owner, a 30-day trial unless the phone already had one |
| `create_nonce` / `consume_nonce` | a single-use 5-minute challenge; a replay gets false |
| `activate_device` | a seat: reinstall recognised, `seat_taken` with the phones holding it, transfer (3 / 30 days, then `transfer_limit` with the next date), re-key keeps seat and seq. Locks the subscription row, so two phones can't both take the last seat |
| `device_for_check_in` / `record_check_in` | the device of an installation for this user, or `revoked` / `not_member` / `unknown_device`; then the check-in's signals |
| `issue_license` | the next license's claims (§4.1), recorded: end of local day, grace, offline window capped at grace, one day for a `reduced` phone |
| `release_device` | by its user, or an owner or manager |
| `delete_account_data` | a sole owner's businesses go with the account; with colleagues, `transfer_ownership_first` |
| `extend_subscription` | a payment or days given by hand, from the end or from today, idempotent per reference |

| Edge Function | Does |
|---|---|
| `license-nonce` | `create_nonce` for the signed-in user |
| `license-activate` | checks the attestation (§5.1), `create_business` on first use, `activate_device`, `issue_license`, signs |
| `license-refresh` | `consume_nonce`, `device_for_check_in`, checks the device key's signature, `record_check_in`, `issue_license`, signs |
| `device-release` | `release_device` |
| `account-delete` | `delete_account_data`, then deletes the auth user. Play requires apps that create accounts to offer deletion in the app and on the web |

The code: `supabase/functions/<name>/index.ts` is a thin shell. `_shared/http.ts` handles Deno, the
auth check and the environment. `_shared/service.ts` holds the operations; `attestation.ts`,
`policy.ts`, `jws.ts`, `x509.ts` and `der.ts` do the cryptography, with no third-party library, so the
same files run on Supabase and in the Node tests.

### 6.4 Protocol (what the app speaks in L4)

Every call is `POST` with the session's `Authorization: Bearer <access token>`. Every reply is JSON
with a `status` the app switches on: HTTP 200 for every expected outcome, 400 for a malformed request
(`field` names it), 401 without a valid session. Bytes travel as base64, nonces as base64url.

**What the device key signs:** UTF-8 of `distrigo-<purpose>\n<nonce, base64url>\n<installation id>`,
with purpose `activate` or `refresh`, as SHA256withECDSA (DER, what `DeviceKey.sign` writes). The
purpose stops an activation's signature from serving as a check-in's. Neither the seq nor the time is
in it: a phone that missed a reply would sign the wrong seq and be locked out.

| Function | Request | Replies |
|---|---|---|
| `license-nonce` | `purpose` | `ok` + `nonce` (32 bytes, 5 minutes, single use) |
| `license-activate` | `nonce`, `installation_id`, `certificate_chain` (the key made with the nonce as attestation challenge), `signature`, `android_id`, `model`, `os_version`, `app_version`; then, as asked, `business_name`, `business_id`, `transfer`, `replace_device` | `activated` + `license`, `device_id`, `business_id`, `trial`, `tier`, `reasons` · `no_business` · `choose_business` · `seat_taken` + `devices` · `transfer_limit` + `next_at` · `refused` + `reason` · `bad_signature` · `bad_nonce` |
| `license-refresh` | `nonce`, `installation_id`, `signature`, `app_version`, `signals` | `ok` + `license` · `revoked` · `not_member` · `unknown_device` · `subscription_inactive` · `bad_signature` · `bad_nonce` |
| `device-release` | `device_id` | `released` · `not_allowed` · `unknown_device` |
| `account-delete` | — | `deleted` · `transfer_ownership_first` |

Every activation reply except `activated` is rolled back. The phone therefore answers `no_business`
or `seat_taken` with the same nonce, key and attestation, within the nonce's 5 minutes. On `revoked`,
`not_member`, `unknown_device` or `subscription_inactive`, the phone calls `LicenseManager.revoke()`.

### 6.5 Accounts → subscription

- Sign-up creates the user, and a trigger gives the user a profile (and nothing else). At onboarding
  the app names the business, and `create_business` makes it, with the user as `owner` and the
  **trial** (D7). An agent invited later joins an existing business instead.
- A Google sign-in and an e-mail sign-up with the same verified address are the same user
  (Supabase links identities by verified e-mail).
- The subscription belongs to the **business**, so multi-role adds memberships and raises
  `max_devices`. No migration is needed.
- Payment, at first: you confirm a payment and extend `valid_to` (Supabase Studio, or a small admin
  page). Later, a payment gateway's webhook writes the same row. The phone sees the change at its
  next check-in.
- **Before publishing on Play (D5):** check Play's Payments policy. Selling the subscription inside the
  app requires Play Billing. An app that only signs in to an account paid for elsewhere, with no
  purchase button or link, falls under different rules.
- **Personal data:** only the account (name, e-mail), the phone model and the integrity signals reach
  the server. Clients, sales and stock stay on the phone. Say so in the privacy policy, and check
  what Algerian law 18-07 asks for (declaration, data hosted abroad).

### 6.6 Google sign-in setup

Use Credential Manager (`androidx.credentials` + `googleid`, `GetSignInWithGoogleOption`). The old
`GoogleSignInClient` is deprecated. Pass the Web client id as `serverClientId`. Register an Android
client with the SHA-1 of **every** signing key: debug, the **benchmark build (debug key)**, release,
and Play App Signing if D5 = Play. Google sign-in needs Play services. E-mail/password is the
fallback.

---

## 7. Where it lands in DistriGo's code

| Existing | Change |
|---|---|
| `MainActivity.kt:300/309/318/327`: `onProfileClick = { /* TODO: profile */ }` | navigate to `Screen.Compte` |
| `DsTopBarAvatar(initials)` | initials from the account name |
| "Effectué par" TODOs (`VenteFormSessionViewModel.kt:247`, `TourneeVenteFormSessionViewModel.kt:251`, `ChargementViewModel.kt:97`, `ChargementFormSessionViewModel.kt:176`) | can be filled from the account (optional, L3) |
| `DeviceIdentity` (noBackupFilesDir) | it is the installation id `iid`, unchanged |
| `DistriGoApplication` + `HiltWorkerFactory` | schedules `LicenseRefreshWorker` like `AutoBackupScheduler` |
| `RestoreStartup` ordering | license evaluated and gate written before the first write |
| `DocumentNumberTriggers` / `UpdatedAtTriggers` | model for `LicenseWriteGate` |
| `StockPolicy` / `DepotStockGuard` | model for `LicensePolicy` / the gate |
| `backup_rules.xml`, `data_extraction_rules.xml` | nothing to add: `noBackupFilesDir` is already outside both |
| Manifest | `INTERNET` and `ACCESS_NETWORK_STATE` are already declared |

New packages: `data/auth/`, `data/license/`, `ui/account/` (Compte, sign-in, sign-up, password reset,
the first-run activation screen), `ui/designsystem/` (a `DsLicenseBanner`).

The **Compte screen** shows: the account (name, e-mail, Google / e-mail), the **Abonnement** (plan,
status chip, « du … au … », « Dernière vérification : il y a 3 h », « Hors ligne possible jusqu'au … »,
« Vérifier maintenant »), the **Appareil** (this phone, the account's other devices when online,
« Libérer cet appareil »), the business, « Se déconnecter », and « Supprimer le compte ».

New dependencies: `androidx.credentials`, `credentials-play-services-auth`, `googleid`;
`supabase-kt` auth (with a Ktor engine), or four plain HTTPS calls; `com.google.android.play:integrity`
(only if D5 = Play); `lifecycle-process` if it isn't already on the classpath. No Tink, no
security-crypto.

---

## 8. Roadmap

Each phase ends tested, committed and walked on the phone (benchmark build), like the overhauls
before it.

**L0 — Decisions.** Answer D1–D8.

**L1 — License core, offline only (no server).** Built 2026-10-10.
- `data/license/`:
  - `License` (parse) and `LicenseVerifier` (§4.2);
  - `LicenseCrypto` (ES256 with no library: raw R‖S → DER) and `LicenseKeys` (pinned keys: release
    keys empty until L2, test keys in debug builds only);
  - `TrustedClock` (§3) behind `Clocks` / `AndroidClocks`;
  - `LicenseStore` (the token, the sealed anchor, the revocation marker);
  - `DeviceKey` and `KeystoreSeal` (Android Keystore);
  - `LicenseManager` → `LicenseState`, judged by `LicenseRules`.

  Nothing calls the manager yet. It is created with `LicenseManager.forApp(context)`, with no Hilt
  binding until L4.
- Golden tokens: `tools/license/golden-tokens.mjs` signs 26 of them with Node's crypto into
  `app/src/test/resources/license/golden.json`. The test keys are in `tools/license/test-keys.json`.
- JVM tests: 58 in `data/license/`.
  - Every golden token, each refusal with its reason.
  - The DER conversion against Java's own signatures.
  - Every state boundary of §2.5.
  - The clock scenarios, including a random walk over 20 seeds that checks the floor stays a true
    lower bound.
  - The attacks of §0 end to end, against the real files.

  A mutation check (judging by the phone's date alone) fails exactly the four rollback tests.
- Device test `DeviceKeyTest`: the device key, attestation challenge and chain, signature, and the
  Keystore seal. **6/6 passed on the Galaxy M34 (Android 16), 2026-10-10.** It has no StrongBox, so the
  key fell back to the TEE. The key was attested, with the server's challenge inside, and its chain of
  5 certificates verifies link by link. Run it with `am instrument`, never a connected* task.
- Not built: the debug panel showing a test license on the phone. It isn't needed until there is a
  screen for it (L3).

**L2 — Backend.**
- **L2a, schema: built 2026-10-10, not yet run on Supabase.** The §6.2 migration and the §6.3 SQL
  functions.
  - `supabase/tests/local/run.sh` runs them on a throwaway local PostgreSQL 17, with a stub of what
    Supabase provides (`supabase_stub.sql`).
  - 33 checks pass: trial and trial claims, the license's claims, seats and transfers and their
    limit, reinstall, re-key, nonces, overrides, payments and the audit, agents, release, row-level
    security for owner, agent, another business and anon, account deletion, and two phones racing
    for the last seat in two real sessions.
  - Removing the subscription lock makes the race test fail (both phones get the seat).
- **L2b, Edge Functions: built 2026-10-10, not yet deployed.** The five functions of §6.3 and the §6.4
  protocol.
  - **Attestation:** chain to Google's 2 root keys, pinned from `android.googleapis.com/attestation/root`
    (RSA-4096, and the ECDSA P-384 root of remote key provisioning). Google's revocation list, cached
    a day. The KeyDescription: challenge, security level, root of trust, the app's package and
    signing certificate.
  - **Signing:** ES256 with WebCrypto.
  - **Tests:** `supabase/tests/local/run.sh` runs 29 Node tests after the 33 SQL checks, on the real
    Galaxy M34 attestation that `DeviceKeyTest` captured (`tests/fixtures/`: a chain of 5 ending at the
    P-384 root, TEE, verified boot, locked).
    - Our certificate reader agrees with Node's.
    - Forged links, replayed attestations, another signer or package, an unknown root, revoked keys
      and expired certificates each meet their verdict.
    - The full flow runs against the database: activate, check in, replay, wrong signature, another
      account, release, delete.
    - The phone's key hash (Kotlin) equals the server's.
  - **Cross-checks:** the same unit tests pass under Deno 2.9 (`npx deno test`), and `deno check`
    passes on every function. The server's licenses (`app/src/test/resources/license/server-golden.json`)
    verify in the app (`ServerTokensTest`), so app and server provably agree on the format.
  - **Mutation checks:** removing the signer check fails the repackaging test; removing the seat lock
    fails the race.
- **L2c, the project:** the Supabase project (Pro), the migration applied, release keys `a` and `b`
  generated by you (the private keys never pass through the chat or the repo), the secrets set, the
  functions deployed.

**L3 — Accounts and the Compte screen.**
- Credential Manager Google sign-in. E-mail sign-up, verification and reset, in French. The encrypted
  session.
- The Compte screen, the four `onProfileClick` TODOs, avatar initials, account deletion.

**L4 — Activation and check-in.**
- First-run screen « Activer DistriGo » (data kept). Activation with attestation (+ Play Integrity
  if D5). `LicenseRefreshWorker`, the foreground check-in, the transfer dialog, the
  `app_meta.business_id` check (§4.7).
- Done when a phone activates, survives airplane mode and a reboot, and picks up a change to `vt`
  made on the server at its next check-in.

**L5 — Enforcement.**
- `LicensePolicy` in the tab roots, the Dashboard's quick actions and the form entry points.
  `DsLicenseBanner`. `LicenseWriteGate` triggers and their coverage test (androidTest, like
  `BusinessSettingsTest`).
- The read-only allowlist (§2.5), the `ClockBehind` screen with « Régler la date »
  (`Settings.ACTION_DATE_SETTINGS`), the open-tournée rule (D4).
- Done when the phone walk below passes:

  | Step | Expected |
  |---|---|
  | Test plan with `ou` = now + 10 min, airplane mode, wait | `CheckInRequired`, writes refused |
  | Date set back 2 days | `ClockBehind` |
  | Date set back, then reboot | still expired |
  | Second phone signs in | transfer dialog; first phone read-only at its next check-in |
  | Clear data | activation screen |
  | Restore another business's `.distrigo` | warning |
  | Backup and export while expired | both work |

**L6 — Integrity hardening.** Server-side attestation and Play Integrity verification, the §5.3 tiers,
local signals sent with each check-in, a list of flagged devices for you.

**L7 — Release.** The path for phones already holding data (sign in, keep everything). Trial length.
A « Licence » section in `CLAUDE.md` (the two layers, "every new document table goes in the gate
list"). The privacy policy text.

L1 comes before L2 so that the part hardest to get right, time and signatures, is proven in
isolation. L5 comes after L4 so that enforcement never locks the development phone before check-in
works.

---

## 9. Decisions (taken 2026-10-10)

| | Question | Decision |
|---|---|---|
| **D1** | Backend | **Supabase** (Postgres, Auth, Edge Functions) |
| **D2** | Offline window | **14 days**, set per plan on the server |
| **D3** | Grace after expiry | **3 days**, writes allowed, amber/red banner |
| **D4** | After expiry | **read-only**; an open tournée can still be closed (retour camion, déchargement) |
| **D5** | Distribution | **Sideloaded APK first**, Google Play once the roadmap and polish are done. Key attestation (§5.1) is the primary check. Play Integrity (§5.2) is a separate module, switched on when the app ships on Play |
| **D6** | Seats | **1 active device** for Solo; self-service transfer, **3 per 30 days** |
| **D7** | Trial | **30 days** from sign-up. You can extend any account by hand in Supabase Studio (`subscriptions.valid_to`, with `note` and `updated_by`), for example 2–3 months for testers. The phone picks it up at its next check-in |
| **D8** | Updates | **An update never signs anyone out.** The session, the license, the clock state and the database all survive an update. The stored formats carry a version, unknown claims are ignored, and the Keystore aliases never change |

---

## Sources (checked 2026-10-10)

- Jetpack Security deprecation: [Android Cryptography docs, via OWASP MASTG-BEST-0050](https://mas.owasp.org/MASTG/best-practices/MASTG-BEST-0050/), [KINTO Tech migration notes](https://blog.kinto-technologies.com/posts/2025-06-16-encrypted-shared-preferences-migration)
- Attestation root rotation (ECDSA P-384, RKP-only from 2026-04-10): [bayton.org](https://bayton.org/android/android-enterprise-faq/key-attestation-root-certificate-change/), [Nevis FIDO docs](https://docs.nevis.net/configurationguide/component-installation-guides/nevisFido/default-uaf-authenticator-metadata). Confirm against Google's key-attestation page before L6
- Play Integrity verdict changes (Android 13+, May 2025): [Google, improved verdicts](https://developer.android.com/google/play/integrity/improvements), [Android Enterprise Community](https://www.androidenterprise.community/kb/announcements/google-play-integrity-api-behavioral-changes/11228)

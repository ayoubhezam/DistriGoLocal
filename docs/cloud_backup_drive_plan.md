# DistriGo: cloud backup to Google Drive — plan

Status: **plan only, not implemented, awaiting the owner's decisions (§9)**. Written 2026-11-03 from the review of the
project. Supersedes the earlier Supabase Storage proposal (§1.3).

---

## 1. Context

### 1.1 Goal

Protect a business's Room database and data when its phone is lost, stolen or replaced, with an **optional, encrypted
cloud copy**, while DistriGo stays offline-first: **Room remains the only database**, the cloud only ever holds whole
encrypted snapshots, no synchronisation, no merging.

### 1.2 What already exists (checked in the code)

| Area | Where | What it does |
|---|---|---|
| Backup file | `data/backup/BackupCreator.kt`, `BackupArchive.kt`, `BackupFormat.kt`, `BackupManifest.kt` | `.distrigo` = zip (Deflater BEST_SPEED) of a consistent DB copy + photos + manifest with a SHA-256 per entry; written to a staging folder, copied, **read back and verified**, deleted if anything fails. |
| DB copy | `data/backup/DatabaseSnapshot.kt` | `VACUUM INTO` on a read-only connection (Android 11+), in one read transaction: sales carry on while it is copied. |
| Restore | `RestoreCoordinator`, `RestorePreparer`, `RestoreInstaller`, `RestoreStartup`, `RestoreStockRule`, `BackupInspector` | Inspection, schema check, stock-rule warning, install at next start, app restart. |
| Auto backup | `data/backup/auto/*` (`AutoBackupScheduler`, `AutoBackupWorker`, `AutoBackupRunner`, `AutoBackupStore`, `AutoBackupAlerts`, `BackupFolder`) | Daily WorkManager periodic job (battery not low), skipped when the data fingerprint (`DataFingerprint`) is unchanged, keeps the newest N, saves to a SAF folder or to private storage; alerts when backups stop. |
| Manual backup | `BackupNowWorker`, `BackupJobNotifier` | One-time job with a notification. |
| License gate | `data/local/database/LicenseWriteGate.kt` | Backups and restores stay allowed when the subscription is read-only. |
| Google sign-in | `ui/account/GoogleSignIn.kt` | Credential Manager + `GetSignInWithGoogleOption(GOOGLE_WEB_CLIENT_ID)` with a nonce → **ID token** for Supabase. Gives no Drive access. |
| Supabase | `supabase/` (migrations, edge functions), `data/auth/*`, `data/license/*` | Accounts (email, Google, anonymous via activation code), `businesses`, `memberships`, `devices`, `subscriptions`, RLS, license functions. **Stays for accounts and licenses only.** |
| Keystore | `KeystoreSessionCipher`, `LicenseCrypto` | Keystore already used for the session and the license. |
| HTTP | OkHttp 4.12 | Already a dependency. |

### 1.3 Decision taken: Google Drive, not Supabase Storage

- **No** backup bucket, **no** `backup_objects` table, **no** retention edge function on Supabase.
- Backups go to the user's own Google Drive, in the hidden **`appDataFolder`**, scope **`drive.appdata`** only.
- Cost for DistriGo: none (it uses the user's 15 GB quota).

### 1.4 Measured sizes (2026-11-02, owner's test phone)

| | Size |
|---|---|
| DB on the phone (5 years, 80 186 sales, 397 167 sale lines, 472 783 stock movements, 5 008 products) | 463 MB |
| `VACUUM INTO` copy | 443 MB |
| `.distrigo` zip (BEST_SPEED) | **204 MB** (ratio 0.46) |
| Per sale, all included | ≈ 2.5 KB of backup |

Estimates: new business 1–3 MB · 1 year at ~20 sales/day **≈ 15 MB** · 1 year at ~45/day ≈ 40 MB. Photos are JPEG
(≈100–200 KB each) and do not compress: 300 photos ≈ 30–60 MB. Encryption adds almost nothing.

---

## 2. Requirements kept from the previous plan

- The **same `.distrigo` file**, built by the existing code, then **encrypted on the phone**: AES-256-GCM, chunked
  (segments). Drive only ever holds encrypted bytes.
- A **backup key per business** (random 256-bit DEK), cached on the phone protected by the **Android Keystore**, and
  **wrapped** by a key derived from a **backup password** and, separately, by a printable **recovery code**.
- **WorkManager** daily job: Wi-Fi only by default, battery not low, skipped when the data fingerprint is unchanged,
  exponential backoff on failure, **resumable** upload.
- **Restore on a new phone** through the existing restore pipeline.

---

## 3. Design

### 3.1 `CloudBackupProvider` (provider-neutral core)

Written once, independent of the provider: building the `.distrigo`, the encryption format, the key ring, the
skip-if-unchanged rule, retention, scheduling, restore. The provider only moves bytes.

```kotlin
interface CloudBackupProvider {
    val id: String                                   // "gdrive"
    suspend fun link(activity: Activity): LinkResult // consent; returns the linked account
    suspend fun unlink()
    suspend fun account(): LinkedAccount?            // e.g. email shown on the status card
    suspend fun list(businessId: String): List<CloudBackupEntry>
    suspend fun upload(meta: CloudBackupMeta, source: EncryptedSource, session: UploadSession?): UploadResult // resumable
    suspend fun download(entry: CloudBackupEntry, fromByte: Long): InputStream
    suspend fun delete(entry: CloudBackupEntry)
    suspend fun readKeyring(businessId: String): ByteArray?
    suspend fun writeKeyring(businessId: String, bytes: ByteArray)
    suspend fun hasBlob(name: String): Boolean        // photos, by content
    suspend fun putBlob(name: String, source: EncryptedSource)
}
```

- **Drive implementation**: about 300 lines on OkHttp (not the heavy Google API Java client).
- A **fake provider** (in memory / temp folder) for unit tests.
- Supabase (or another) can be added later as a second implementation without touching the core.

### 3.2 Encrypted file format (`.dgenc`)

- **One file per backup.** Header (magic, format version, key id, segment size, nonce prefix) then **8 MB segments**,
  each AES-256-GCM with its own tag; nonce = prefix + segment counter; the **last segment is flagged** in its AAD so a
  truncated file is detected.
- Streaming end to end: RAM stays flat (a few MB). The staging copy lives in the cache and is deleted after upload.
- Drive **`appProperties`** on each file: business id, device id, created at, schema version, app version, plain size,
  key id. (Values ≤ 124 bytes each.)
- **Integrity without re-downloading**: Drive returns the `md5Checksum` of the uploaded file; it is compared with the
  MD5 computed while uploading. The inner per-segment GCM tags and the `.distrigo` SHA-256s are checked again at restore.

### 3.3 Key ring

- `keyring.json` in `appDataFolder` (per business): the DEK wrapped twice — by a KEK from the **password**
  (PBKDF2-HMAC-SHA256, high iteration count, random salt) and by a KEK from the **recovery code**; plus key id, KDF
  parameters, creation date.
- A copy of the (encrypted, safe) key ring is kept on the phone, so it can be put back if deleted from Drive.
- The DEK itself is cached locally wrapped by a Keystore key, so daily backups need no typing.
- DistriGo never sees the DEK, the password or the recovery code.

### 3.4 Retention, enforced by the app (7 daily + 4 weekly)

- Runs **only after** a new backup has uploaded and passed the MD5 check.
- Lists the business's backups (by `appProperties`), keeps the newest of each of the last **7 days** and the newest of
  each of the last **4 weeks**, deletes the rest.
- **Never** deletes the newest verified backup; **never** deletes anything before the new one is confirmed. A failed
  delete is retried at the next run.

| Failure case | Behaviour |
|---|---|
| User deleted files, or « Supprimer les données d'application cachées » in Drive | Before skipping an unchanged day, check the last backup still exists on Drive; if not, upload again. If the key ring is missing, put back the local copy. |
| Drive full (`403 storageQuotaExceeded`) | Stop immediately (no retry loop). Alert « Google Drive est plein — la sauvegarde en ligne est suspendue ». Local backup keeps running. Optional (decision §9): trim weekly backups first. |
| Access revoked / token can no longer be renewed silently | The worker cannot show consent → alert « Reconnecter Google Drive ». The existing « pas de sauvegarde depuis X jours » alerts also apply. |
| Two phones on the same Google account | Each backup records its device; retention per business; the restore list shows the device. |
| 401 | Get a fresh token silently, retry once. |
| 429 / 5xx / network | WorkManager exponential backoff; the upload session is saved and resumed. |

### 3.5 Photos: included, stored by content (decision proposed)

- Each photo is uploaded **once** as its own encrypted file, named by a **keyed hash** (HMAC with a key derived from the
  DEK) so Drive learns nothing from the names. Each backup's manifest lists the photos it uses.
- Retention deletes photo files that no remaining backup uses.
- 300 photos cost their size once (≈30–60 MB), not ×11 kept backups.
- Toggle « Inclure les photos », on by default.

### 3.6 Scheduling (WorkManager)

- Unique periodic work (daily) + unique one-time work « Sauvegarder maintenant »; names distinct from the local auto
  backup and the license jobs.
- Constraints: `NetworkType.UNMETERED` by default (`CONNECTED` if « Autoriser les données mobiles » is on), battery not
  low.
- Skip when the data fingerprint is unchanged **and** the last backup is still on Drive.
- Upload session URI and progress persisted, so a cut connection or a killed app resumes instead of restarting.
- Notifications through the existing `BackupJobNotifier` style.

### 3.7 Restore on a new phone

Sign in → existing device transfer → « Restaurer depuis Google Drive » → link Drive (same Google account) → list
(date, size, device) → choose → resumable download → check segments → decrypt with the password (or the recovery
code) → hand the `.distrigo` to the existing restore pipeline (inspection, schema check, stock-rule warning, restart).
Also offered right after sign-in on an empty phone.

---

## 4. Google authorization

- **The existing Google sign-in cannot be reused for Drive**: Credential Manager returns an **ID token** (identity for
  Supabase), not an **OAuth access token** with a Drive scope.
- Use Google Identity Services' **Authorization API**:
  `Identity.getAuthorizationClient(activity).authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.appdata"))).build())`
  (dependency `com.google.android.gms:play-services-auth`).
  - First time: returns a `PendingIntent` → consent screen.
  - Afterwards: called **silently** (also from the worker) for a fresh access token (~1 h). No refresh token stored.
  - If it needs a resolution in the background → alert « Reconnecter Google Drive ».
- Same Google Cloud project and Web client as the Supabase Google sign-in. The Drive account may differ from the
  Supabase account; activation-code (anonymous) users can link Drive too.
- **Not available without Google Play Services** (e.g. recent Huawei phones): the screen shows « Non disponible sur ce
  téléphone » and only the local backup is offered.

### 4.1 Google Cloud setup (Phase 0, owner)

1. Enable the **Google Drive API** in the project.
2. OAuth consent screen: add scope `drive.appdata`, app name, logo, support email, **privacy policy URL**, app domain.
3. Check Android OAuth clients exist for the **debug, release and Play app-signing SHA-1s** (probably already done for
   Google sign-in).
4. Publish the consent screen to **Production**.
5. **To verify before submitting**: the classification of `drive.appdata` on Google's Drive scopes page. Believed to be
   a recommended, non-sensitive scope (light brand verification only). If it is « sensitive »: verification review with
   privacy policy and a demo video, no security assessment.

### 4.2 Play Store

- **Data safety**: data goes to the user's own Drive at their choice, encrypted end to end. Likely still to declare:
  financial/business records and client contact details (names, phones), marked **optional** and **encrypted in
  transit**. Exact answers to be checked against Play's Data safety help when filling it in.
- Update the **privacy policy** (what is uploaded, where, encryption, who can read it: nobody but the user).
- No new runtime permission (INTERNET is already there) — consistent with the Play Store plan (avoid permissions that
  need Google declarations).

---

## 5. Losing the Google account

- Backups live only in that account: if the **account is lost**, they are unreachable; the recovery code does not help
  (it unlocks the key, not the account).
- Password forgotten but account fine → the **recovery code** covers it. Both lost → backups cannot be decrypted (by
  design).
- UI warnings:
  - At linking: « Vos sauvegardes seront dans le Google Drive de nom@gmail.com. Pour restaurer sur un nouveau
    téléphone, il faudra ce compte ET votre mot de passe (ou le code de récupération). »
  - The linked account is always shown on the status card.
  - « Délier » and « Changer de compte » warn that the old account keeps the old backups.
  - A periodic reminder to also keep a local backup (PC, clé USB).
- « Changer de compte » links the new account and uploads a full backup at once.

---

## 6. Screens (French labels; Arabic added with them, see CLAUDE.md « Text and languages »)

Paramètres › Sauvegarde › **« Sauvegarde Google Drive »**

- **Not linked**: short explanation · button **« Lier Google Drive »** · « Facultatif — la sauvegarde sur le
  téléphone continue sans Google. »
- **Linking**: Google consent (`drive.appdata`) → **« Créer le mot de passe de sauvegarde »** (twice) → **« Votre code
  de récupération »** with « Imprimer », « Partager », « J'ai noté le code ».
- **Linked**:
  - « Compte lié : nom@gmail.com »
  - « Dernière sauvegarde : il y a 3 h · 14 Mo »
  - Toggles « Sauvegarde automatique », « Wi-Fi uniquement » (or « Autoriser les données mobiles »), « Inclure les
    photos »
  - Buttons « Sauvegarder maintenant », « Restaurer », « Changer le mot de passe », « Changer de compte », « Délier »
- **« Délier »**: confirmation, revokes the app's access and the local key cache; leaves the backups on Drive (still
  restorable later).
- Alerts: « Google Drive est plein — la sauvegarde en ligne est suspendue », « Reconnecter Google Drive »,
  « Non disponible sur ce téléphone ».
- Activation-code users without a Google account and the local backup are unaffected.

---

## 7. Phases

| Phase | Content | Size |
|---|---|---|
| 0 | Owner's decisions (§9); Google Cloud setup (§4.1); check `drive.appdata` classification | Owner + S |
| 1 | Encryption core: segmented AES-GCM `.dgenc`, key ring (PBKDF2 password + recovery code wrapping), Keystore cache; tests (wrong password, damaged segment, truncated file) | M |
| 2 | `CloudBackupProvider` + Drive implementation on OkHttp: authorization, list, resumable upload, download from a byte, delete, `appProperties`, error mapping (401, 403 quota, 404, 429/5xx); fake provider for tests | M |
| 3 | Engine + WorkManager: daily and « maintenant » jobs, constraints, skip if unchanged and still on Drive, persisted upload sessions, MD5 check, retention, alerts | M–L |
| 4 | Photos by content (keyed-hash names), photo cleanup, « Inclure les photos » | S–M |
| 5 | Restore on a new phone: link, list, choose, resumable download, decrypt, existing restore pipeline; offered after sign-in and device transfer | M |
| 6 | Screens (§6), French and Arabic | M |
| 7 | QA: Wi-Fi cut mid-upload, app killed, access revoked, Drive full (simulated), hidden data deleted, wrong password, recovery code, restore on the emulator from the phone's backup, read-only subscription, two devices | M |
| 8 | Privacy policy, Play Data safety, consent screen review | S (mostly owner) |

---

## 8. Risks

- **No Google Play Services** (recent Huawei phones, common in Algeria): no Drive; local backup only.
- **Drive full** is common on free accounts (15 GB shared with Gmail and Photos): handled by an alert, but the cloud
  backup stops until the user frees space.
- **Silent loss of access** (access revoked, password changed): « Reconnecter » alert + « no backup for X days » alert.
- **Lost Google account or forgotten secrets**: unrecoverable by design; §5 warnings are the only defence.
- **Large databases**: a 200 MB backup takes minutes on slow Wi-Fi; resumable uploads and Doze deferrals delay but do
  not break it.
- **Google review timing**: consent screen and Data safety can delay a release by days to weeks.

---

## 9. Decisions needed from the owner

1. **Key model**: password + recovery code as planned? Minimum password length (suggested 8)?
2. **Retention**: 7 daily + 4 weekly? When Drive is full: trim weekly backups first, or alert only?
3. **Photos**: included by content as in §3.5, toggle on by default?
4. **Mobile data**: Wi-Fi only by default, with « Autoriser les données mobiles »?
5. **Plans**: cloud backup for all plans, or paid plans only?
6. **Google Cloud**: will the owner do Phase 0, or should a step-by-step checklist be written?

---

## 10. Working rules for the implementation (from the project)

- Read `CLAUDE.md` first (architecture, « Text and languages », database and stock rules).
- No user-facing text in code: French in `res/values/strings_<section>.xml`, Arabic in `values-ar`, together.
- Do not change receipts, PDFs or WhatsApp texts.
- Room stays the primary database; new write paths that move stock go through the ledger and the guard (not expected
  here: backup only reads).
- Commit and push only on the owner's explicit « commit and push »; run `./gradlew :app:assembleDebug
  :app:testDebugUnitTest` (output to a file) before committing; never `connectedAndroidTest`.
- Phone tests: install only from the home screen, never drive another app (Android's file picker and share sheet
  included), `svc power stayon usb` during tests and `false` after.

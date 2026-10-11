# Moving DistriGo to a new laptop

Checklist written 2026-10-11. Follow it top to bottom:

- **A** on the old laptop: back up.
- **B** on the new laptop: install.
- **C** on the new laptop: check, before touching the phone.
- **D** on the old laptop: wipe, only once C passes.

**Where everything lives:**

- **GitHub:** all the code, docs, Supabase migrations and Edge Functions. Every commit is pushed; nothing to
  back up.
- **Only on the old laptop:** the files in A1. They must be copied by hand.
- **The phone:** the app's data and its activation. The move doesn't touch them, as long as the app is never
  uninstalled (C3).
- **Supabase:** the server, its secrets and the database. Nothing to restore.

## A. Old laptop: back up

### A1. Files to copy

Copy, don't move: while the old laptop is still in use, keep the originals in place.

**Critical: they can't be re-created.**

- [ ] `C:\Users\X390\DistriGo-secrets\`, the whole folder:
  - `distrigo-release.jks`: the release keystore. Without it, the app can never be updated in place.
  - `license-keys\`: the license signing keys (`lic-2026-a` and `lic-2026-b`, `.pem` and `.env`) and
    `public-keys.json`. If they're lost, every phone needs an app update with new keys.
- [ ] `local.properties` in the project folder: the keystore passwords (the `.jks` is useless without them)
  and `MAPS_API_KEY`.
- [ ] `C:\Users\X390\.android\debug.keystore`: signs the debug and benchmark builds, the ones on the phone.
  The license server accepts its fingerprint (C1).
- [ ] `C:\Users\X390\Desktop\DistriGoUI.png`: the onboarding mockup. It isn't in the repo.

**Useful:**

- [ ] Claude Code's memory, 29 notes on the project's history and on how you like to work:
  `C:\Users\X390\.claude\projects\C--Users-X390-Desktop-my-application-DistriGo-with-local-stroage\memory\`
- [ ] Optional: `.claude\settings.local.json` in the project folder (the permissions already approved).

**Not needed**, they come back by themselves: `build/`, `.gradle/`, `.idea/`, `node_modules/`,
`supabase/.temp/`, the Android SDK, and the logins.

### A2. Protect the copy

- [ ] Put the files in a **7-Zip** archive: format 7z, encryption **AES-256**, **"Encrypt file names"**
  checked. Windows 11 Home can't encrypt a USB drive with BitLocker.
- [ ] Keep the archive's password in a password manager, never in a file next to the archive. Save the
  keystore passwords from `local.properties` there too.
- [ ] Keep **two copies** of the archive: the external drive and a second place (another drive, or a cloud
  folder; the archive is encrypted).
- [ ] If you **moved** files instead of copying them, the drive holds the only copy until the second one
  exists.
- [ ] Once the archive is checked (A3), delete the unencrypted copies from the drive.

### A3. Check the copy

- [ ] Open the archive with its password on another computer (or on the new laptop). Check that every file in
  A1 is there and isn't empty.

## B. New laptop: install

Versions used on the old laptop:

| Tool | Version | Note |
|---|---|---|
| Git for Windows | latest | Brings Git Bash, which the scripts need |
| JDK | **17** | Not 24 or newer as the default (B3) |
| Android Studio | 2026.1.1 or newer | |
| Android SDK | Platform 36, Build-Tools 36.0.0, Platform-Tools | |
| Node.js | 24 (24.17.0) | Supabase CLI and the Edge Function tests |
| PostgreSQL | 17 | Only for the local server tests |
| Gradle, AGP, Kotlin | 8.13, 8.12.3, 2.0.21 | Pinned in the repo, downloaded automatically |

### B1. Git and the project

- [ ] Install Git for Windows.
- [ ] Allow long paths. The build already makes paths over 260 characters.
  - In PowerShell **as administrator**:
    `New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" -Name LongPathsEnabled -Value 1 -PropertyType DWORD -Force`
  - `git config --global core.longpaths true`
- [ ] Set your name and email: `git config --global user.name "ayoubhezam"` and
  `git config --global user.email "<your email>"`.
- [ ] Choose the project folder **before cloning**, because Claude's memory is tied to it (B6):
  - **The same path,** if your Windows user is `X390` again:
    `C:\Users\X390\Desktop\my_application\DistriGo with local stroage`. The memory copy is then the simplest.
  - **A shorter path,** e.g., `C:\dev\DistriGo`: fewer long-path problems.
- [ ] `git clone https://github.com/ayoubhezam/DistriGoLocal.git "<folder>"`. Sign in to GitHub when the
  browser asks.

### B2. Restore the files, before the first build

Android Studio creates a new `debug.keystore` on the first debug build if none exists, so restore first.

- [ ] `DistriGo-secrets\` goes to `C:\Users\<you>\DistriGo-secrets\`.
- [ ] `debug.keystore` goes to `C:\Users\<you>\.android\`. Create the folder if needed; replace any new
  keystore already there.
- [ ] `local.properties` goes to the project folder root. Then edit it:
  - `RELEASE_STORE_FILE`: the new path of `distrigo-release.jks`, with forward slashes.
  - `sdk.dir`: delete the line. Android Studio writes the right one when it opens the project.

### B3. Java and Android Studio

- [ ] Install **JDK 17** (e.g., Temurin 17 from adoptium.net).
  - Set `JAVA_HOME` to it, so `./gradlew` uses it in the terminal.
  - Don't make JDK 24 or newer the default: Gradle 8.13 runs on Java 23 at most.
- [ ] Install Android Studio and open the project.
  - If it offers to upgrade AGP (the Upgrade Assistant), **decline** for now.
  - Settings → Build, Execution, Deployment → Build Tools → Gradle → **Gradle JDK: 17**.
  - SDK Manager: Android 16 (API 36) platform, Build-Tools 36.0.0, Platform-Tools.

### B4. Node and Supabase

- [ ] Install Node.js 24 LTS.
- [ ] Run these in the project folder:
  - `npx supabase@2.120.0 login`
  - `npx supabase@2.120.0 link --project-ref nwzriieoptyrdttyxdlx`. If it asks for the database password,
    leave it blank.
  - `cd supabase/functions/tests && npm ci`
- Nothing to restore on the server: its secrets, the database and the five functions stay on Supabase.

### B5. PostgreSQL (optional)

- [ ] Install PostgreSQL 17 in its default folder (`C:\Program Files\PostgreSQL\17`). It's only used by
  `supabase/tests/local/run.sh`. In another folder, set `PG_BIN` to its `bin` folder.

### B6. Claude Code

- [ ] Install Claude Code, sign in, and open it once in the project folder.
- [ ] Claude Code creates `C:\Users\<you>\.claude\projects\<name>\`, named after the project's path: every
  character other than a letter or digit becomes `-` (e.g., `C:\dev\DistriGo` gives `C--dev-DistriGo`).
  Copy the backed-up `memory` folder into it.
- [ ] Optional: copy `settings.local.json` back into the project's `.claude\` folder.

## C. New laptop: check, before touching the phone

### C1. Same signing keys

- [ ] Run `./gradlew :app:signingReport`. The SHA-256 values must be exactly these:
  - **debug** and **benchmark**:
    `91:41:C3:19:01:4F:82:B3:90:C8:5E:F8:7A:80:03:E3:C5:96:D7:4F:0B:BD:7F:B3:36:D3:20:D2:C8:D1:4C:28`
  - **release**:
    `BA:93:AD:17:6C:08:9C:25:D5:1A:D0:E6:46:2B:72:0A:41:8B:BF:4A:0E:FF:50:FC:C4:E4:22:72:79:37:3E:35`

  These are the two values in the server's `LICENSE_APP_SIGNING_DIGESTS`. A different value means a wrong
  file: stop and fix it before installing anything.

### C2. Build and tests

- [ ] `./gradlew :app:testDebugUnitTest` passes.
- [ ] `npx supabase@2.120.0 functions list` shows the five functions. This proves the login and the link.
- [ ] Optional, needs B5: `bash supabase/tests/local/run.sh` passes (33 SQL checks, then the Node tests).

### C3. The phone

- [ ] In the app, make a `.distrigo` backup first, as a safety net.
- [ ] Connect the phone. On the phone, allow USB debugging for the new computer.
- [ ] Install **over** the current app: `./gradlew :app:installBenchmark`, the build the phone runs.
- [ ] If the install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, or Android Studio offers to uninstall
  the existing app: **say no.** It means the debug keystore isn't the old one (go back to C1). Uninstalling
  erases the phone's data and its activation.
- [ ] Open the app. The data is there, and Compte shows « Actif ».

## D. Old laptop: wipe, only once C passes

- [ ] **Supabase:** Dashboard → Account → Access Tokens. Delete the old laptop's CLI token; its name starts
  with `cli_` and contains the old computer's name.
- [ ] **GitHub:** Settings → Applications → Authorized OAuth Apps → Git Credential Manager → Revoke. This
  signs out every computer, so the new laptop asks you to sign in again at its next push.
- [ ] **Claude Code:** run `/logout` on the old laptop.
- [ ] **Phone:** Developer options → Revoke USB debugging authorizations. Then allow the new laptop again
  when the phone asks.
- [ ] **Windows:** Settings → System → Recovery → Reset this PC → Remove everything. Under "Change settings",
  turn **Clean data** on. The secrets were on this disk; a normal delete leaves them recoverable.

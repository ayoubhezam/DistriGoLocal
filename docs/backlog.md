# Backlog

Items noted for later, not being worked on. Each says where it was found and what was decided.

## License hardening, for the Play Integrity phase

- **LIC-02 (audit 2026-11-09): unattested or repackaged apps are still activated, on the reduced tier.** A chain that
  does not end at a Google root (a software key, an emulator, a repackaged APK with its own key) is accepted with a
  one-day offline window, and the signer check only fires on a genuine Google chain. Since 2026-11-17 such phones get
  no trial (LIC-04), but a code still activates them. Owner, 2026-11-17: close it with Play Integrity when the Play
  Console exists; then consider refusing paid activation on reduced-tier phones, and obfuscating `data/license`.
- **A factory reset gives a phone a new ANDROID_ID, so a new trial.** Accepted for now: key attestation offers no
  stable hardware identifier an app can read. Revisit with Play Integrity's device recall, if Google offers it then.

## Pre-release security audit

- ~~**`create_business` grants a trial without any check when `p_android_id_hint` is null.**~~ Fixed 2026-11-17 (LIC-04,
  migration 20261117140000_license_device_checks): no ANDROID_ID or no Google-attested key, no trial. The note below is
  kept for history. The Edge Function reads
  `android_id` as optional (`supabase/functions/_shared/service.ts`, `optionalText(body, "android_id", 64)`), so a
  direct call with a valid sign-in that omits it gets a 30-day trial; any made-up value does the same, since the
  identifier is sent by the phone and not verified. The app always sends ANDROID_ID, so normal users are not
  affected. Found 2026-11-03 while fixing the refused-trial grace (85f8776). Related, also out of scope then: a
  factory reset gives the phone a new ANDROID_ID and so a new trial.

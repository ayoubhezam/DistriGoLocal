# Backlog

Items noted for later, not being worked on. Each says where it was found and what was decided.

## Pre-release security audit

- **`create_business` grants a trial without any check when `p_android_id_hint` is null.** The Edge Function reads
  `android_id` as optional (`supabase/functions/_shared/service.ts`, `optionalText(body, "android_id", 64)`), so a
  direct call with a valid sign-in that omits it gets a 30-day trial; any made-up value does the same, since the
  identifier is sent by the phone and not verified. The app always sends ANDROID_ID, so normal users are not
  affected. Found 2026-11-03 while fixing the refused-trial grace (85f8776). Related, also out of scope then: a
  factory reset gives the phone a new ANDROID_ID and so a new trial.

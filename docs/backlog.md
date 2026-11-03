# Backlog

Items noted for later, not being worked on. Each says where it was found and what was decided.

## Licensing

- **Trial card shows « Durée : 31 jours » and « Il reste 30 jours ».** Found 2026-11-03 on the emulator after
  signing up (trial 3 Nov → 3 Dec 2026). `create_business` sets `valid_to = today + trial_days`, so with today
  counted the trial runs 31 days; the card's two figures disagree. Decide whether the trial is 30 days including
  today (server) or only the wording changes (app).

## Pre-release security audit

- **`create_business` grants a trial without any check when `p_android_id_hint` is null.** The Edge Function reads
  `android_id` as optional (`supabase/functions/_shared/service.ts`, `optionalText(body, "android_id", 64)`), so a
  direct call with a valid sign-in that omits it gets a 30-day trial; any made-up value does the same, since the
  identifier is sent by the phone and not verified. The app always sends ANDROID_ID, so normal users are not
  affected. Found 2026-11-03 while fixing the refused-trial grace (85f8776). Related, also out of scope then: a
  factory reset gives the phone a new ANDROID_ID and so a new trial.

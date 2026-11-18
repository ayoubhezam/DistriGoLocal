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

## Pre-release: errors still dropped without a word

Found 2026-11-18 while fixing the same thing in Tournée vente and Achats (Solo release point 3). Owner, 2026-11-18:
not in point 3, fix before release as its own point. Each of these calls passes an `onError` that ignores the reason
(`onError = {}` or only resets a flag), so a refused or failed write looks like a button that did nothing:

- Client and supplier detail pages: `ui/clients/ClientDetailScreen.kt` (3 places), `ui/suppliers/SupplierDetailScreen.kt`
  (3 places).
- Client and supplier forms: `ui/clients/ClientFormScreen.kt:561`, `ui/suppliers/SupplierFormScreen.kt:152` and `:158`.
- Tournée form: `ui/tournees/TourneeFormScreen.kt:317`.
- « Visité, sans vente » on a tournée: `ui/tournees/TourneesScreen.kt:685`.
- Product photo save: `ui/products/ProductFormScreen.kt:295–324` (the product is saved; only its photo is lost quietly).
- « Livrer » in Ventes: `ui/ventes/VentesScreen.kt:1101`.

The fix follows Ventes: the reason shown where the action was (a banner by the button, the dialog kept open, or a
snackbar or toast once the screen is gone).

## After the release

- **Edit a received bon in one step (owner, 2026-11-18: after the release).** Today a received bon is reopened first
  (« Rouvrir le bon » undoes the reception: stock and purchase price), edited, then marked received again. One save
  could do all three in one transaction with the stock check: undo the old reception, write the new lines, receive
  again. Held back because it rewrites the purchase-price history (CMUP, last price) in one go and needs its own tests.

## Accounts and e-mail: what remains of Solo release point 3

Point 3 shipped 2026-11-18 (d051de9 save errors, 4172424 « Mot de passe oublié », f0d2df1 the code step kept on an
error) and was tested end to end on the owner's phone with a real Resend e-mail. Left for later:

- **E-mail confirmation at sign-up is off** (owner, 2026-11-18: leave it off for now). Anyone can sign up with an
  address that is not theirs; its owner can still take it back with « Mot de passe oublié ». Now that Resend sends
  mail, turning it on is a Supabase switch; the app already shows « Ouvrez le lien envoyé à … ».
- **The Auth e-mail settings live only in the Supabase dashboard:** custom SMTP (Resend, `no-reply@distrigoo.com`),
  the « Reset password » template (source: `supabase/templates/recovery.html`, pasted by hand), OTP length 6, expiry
  3600 s, 30 e-mails an hour. A change to the template file must be pasted again; `supabase/config.toml` could carry
  them later (`config push` sends the whole auth section, so check it first).
- **Resend free plan:** 100 e-mails a day, 3,000 a month, one domain. Plenty for resets; watch it if confirmation
  e-mails are turned on.
- **No « Changer le mot de passe » in Compte** for someone signed in: only through « Mot de passe oublié ».
- **Owner's review of the Arabic** of the reset page, its messages and the e-mail.

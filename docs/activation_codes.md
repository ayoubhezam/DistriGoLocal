# DistriGo: activation codes

How a merchant activates DistriGo with a code instead of an e-mail account, and how codes renew a
subscription. Approved on 2026-10-14; built in steps C1 to C4 below. It extends the license system of
`docs/license_architecture.md`: the license, the device key, check-ins and the seat rules are unchanged. A code
only decides **which business** a phone joins.

## Why

Many merchants, DistriGo's target audience, find e-mail and password sign-up tedious or don't know how to do
it. Payment is offline: cash or BaridiMob, to DistriGo or to a reseller. A code does both jobs. The merchant
types 12 characters, and the payment made to whoever handed over the code becomes the subscription.

## Decisions (owner, 2026-10-14)

| # | Decision |
|---|---|
| 1 | **A code is a business key.** Its first use creates the business. Later uses re-activate the same business on a new phone, under the usual rules (1 phone, 3 transfers per 30 days). This replaces e-mail recovery. |
| 2 | Codes are sold **directly and by resellers**, so they're created in bulk, by batch. |
| 3 | **Renewals are by code:** « Saisir un code » in Compte extends the subscription. **Only the first code is the business key**; renewal codes only add time. |
| 4 | **Unsold codes can expire:** an optional expiry date per batch, 12 months by default. |
| 5 | **One check character:** the phone catches typos before contacting the server, so a typo never counts as a failed attempt. |
| 6 | The onboarding appears on **fresh installs only**. The e-mail sign-up keeps its flow. For a code without a company, the app asks « Nom de l'entreprise » and « Téléphone » right after the code. Activity and number of sales reps are filled later in Compte. |

## The code

- **12 characters** from Crockford's base32 alphabet, `0123456789ABCDEFGHJKMNPQRSTVWXYZ`: no I, L, O or U, so
  no character can be read as another. Shown as `XXXX-XXXX-XXXX`.
- When typed, it's read leniently: capitals, `O` as `0`, `I` and `L` as `1`, and spaces, dashes and anything
  else ignored. The app (`ui/onboarding/CodePages.kt`) and the server (`private.normalize_code`) apply the
  same rule.
- **11 random characters (55 bits) plus 1 check character**, by the Luhn mod 32 algorithm. It catches every
  single wrong character and almost every swap of two neighbours. 2^55 is about 36 million billion codes, so
  guessing one is hopeless; the attempt limits only keep the server quiet.
- **The database never holds a code**, only its fingerprint: an HMAC-SHA256 keyed with a secret kept in Supabase
  Vault (`activation_code_pepper`, made by the migration and never shown). A leaked table reveals no code, and
  without the vault secret, no code can be tried against it offline either.

## The three uses

| Situation | Where | What happens |
|---|---|---|
| **New code** | Onboarding | Creates the business, named by the code or by the merchant if the code carries no name, with the code's plan and duration (`extend_subscription`, the code as the payment reference). The code becomes the business's **key**. Then the phone is activated. |
| **Key code**, on another phone | Onboarding | Joins that business, then the usual seat rules: if another phone holds the seat, the app asks to transfer it (3 per 30 days). |
| **New code** | Compte, « Saisir un code » | Extends the subscription by the code's duration: from its end, or from today if it has ended. The code is now a **renewal**: it never becomes a key, and can't be used again. |

A new code entered in the onboarding by someone already signed in to an account that has a business acts as a
renewal of that business; this covers an expired trial being paid for. A trial-plan code used as a renewal keeps
the business's paid plan.

Every answer, in the phone's words:

| Server | The phone says |
|---|---|
| `bad_code` | « Ce code n'existe pas ou n'est plus valable. » (unknown, revoked, or its business deleted) |
| `code_expired` | « Ce code a expiré. Demandez-en un nouveau à votre revendeur. » |
| `code_used` | « Ce code a déjà servi à un renouvellement. » |
| `code_needs_business` | The company step: name and phone |
| `too_many_attempts` + `retry_at` | « Trop d'essais. Réessayez après HH:MM. » |

## Accounts without e-mail

- The app signs in **anonymously** (Supabase Auth, `POST /auth/v1/signup` with an empty body). It's a real
  account with no e-mail, so the license, the Keystore key and check-ins work exactly as for e-mail accounts.
- **To turn on in the dashboard:** Authentication → Sign In / Providers → **Allow anonymous sign-ins**.
- If someone is already signed in, their account is used rather than a new anonymous one.
- When a key brings a business to a new phone, the business's other **anonymous** members that hold no active
  phone are removed, because their sessions are gone with their old phones.
- **Later, in Compte:** « Ajouter une adresse e-mail » turns the anonymous account into an e-mail one
  (`PUT /auth/v1/user`).

## Database (migration `20261014120000_activation_codes.sql`)

- `private.activation_codes`:
  - fingerprint, plan, days, optional company name, **batch** (e.g. « Revendeur Sétif — oct. 2026 »), note;
  - `expires_on` for unsold codes;
  - once used: `use` (`key` or `renewal`), business, `redeemed_at`, `redeemed_by`;
  - `revoked_at`.
- `private.code_attempts`: failed attempts per installation, account and connection (a fingerprint of the IP).
  The limits are **5 an hour per installation or account, 30 an hour per connection**. A shared mobile
  connection carries many people, hence the higher figure.
- `public.businesses`: `contact_phone`, `activity`, `sales_reps`. The phone comes from either sign-up; the
  other two from the e-mail sign-up, or later from Compte.
- `private.create_business` takes the three new details, so the e-mail sign-up stores them as well.

**For the owner, in Supabase Studio's SQL editor:**

```sql
-- 50 six-month codes for a reseller; shown once, copy them into a spreadsheet
select * from private.create_codes(50, 'solo', 182, 'Revendeur Sétif — oct. 2026');
-- one code for a merchant you set up yourself, with the company name already on it
select * from private.create_codes(1, 'solo', 365, 'Vente directe', p_business_name => 'Épicerie Benali');
-- a merchant calls: what is this code?
select private.code_status('7KQ4-M2XP-9HTD');
-- block a code, e.g. a key that leaked
select private.revoke_code('7KQ4-M2XP-9HTD', 'Perdu par le revendeur');
-- each batch: unused, keys, renewals, expired, revoked
select * from private.codes_by_batch;
```

## Server (Edge Functions)

- `license-activate` accepts `activation_code`, and for a code without a company `business_name` and
  `contact_phone`. The code is redeemed **in the activation's transaction**: business created, code bound and
  phone activated, or nothing at all. Every answer but `activated` rolls back, so the same request serves the
  merchant's next step (the company) within the nonce's five minutes. For the e-mail sign-up it also takes
  `contact_phone`, `business_activity` and `business_sales_reps`.
- `code-redeem` (new): « Saisir un code » in Compte. It answers `renewed` with the new end date, after which
  the phone checks in to fetch its renewed license.

## Steps

| Step | What | State |
|---|---|---|
| **C1** | Migration, SQL functions, Edge Function changes; SQL and Node tests on a local PostgreSQL. Deployed to the live project only after the owner's go. | **live, 2026-10-14**: `db push` and the six functions deployed. The live smoke test passed (20 checks), and a rolled-back check of codes on the live database too. |

**C1's tests** (`PG_BIN=".../PostgreSQL/18/bin" supabase/tests/local/run.sh <work-dir>`):

- **SQL (`codes_test.sql`, 22 checks):**
  - the reading rules and the check character, including every single typo across 20 codes;
  - the keyed fingerprint;
  - creating codes by batch;
  - a code making its business, with and without a company;
  - the key on two more phones;
  - renewals: after a trial, after an ended subscription, with a trial code, and spent only once;
  - expired, revoked, unknown and mistyped codes;
  - a deleted business's key;
  - the attempt limits;
  - `codes_by_batch`;
  - the e-mail sign-up's details;
  - who may touch codes.
- **Concurrency (`run.sh`):** one new code typed on two phones at once makes one business. Removing the
  code's row lock makes this test fail with two businesses.
- **Node (`service.test.ts`, 7 tests):** the real Edge Function code against that database, with the captured
  Galaxy M34 attestation:
  - an unknown code refused and counted;
  - the attempt limit;
  - the company asked for, then the business made and the phone activated;
  - the key on another account;
  - renewals spent once;
  - an activity off the list refused.
- **Deno:** `deno check` passes on all six functions.
| **C2** | App: anonymous sign-in; codes, e-mail sign-up and sign-in wired into the onboarding; a real test on the phone. | **done, 2026-10-14**: 518 unit tests pass, and the live test on the owner's M34 passed |

**C2's phone test** (Galaxy M34, live project, debug build):
1. The real account signed out, freeing its seat.
2. Test code `V1A9-Y8XW-JFC3` (batch « Test C2 — téléphone ») typed in lowercase without dashes was shown as
   `V1A9-Y8XW-JFC3`.
3. The anonymous account was made, the company was asked, and the phone was activated (attested key, signed
   license) in 4 s.
4. The success screen showed « Abonnement », 30 jours, until 12 nov. 2026. Compte showed « Activé avec un code »,
   Actif.
5. The server held the business with its phone, solo 14/10 to 12/11, the code as its key, one active phone, and
   an anonymous account.
6. The owner deleted the test account (business with it) and signed back in by e-mail: « Bon retour ! », trial
   until 9 nov. 2026.

**C2's app changes:**
- **Accounts:** `AuthApi.signInAnonymously` (`POST /auth/v1/signup` with no e-mail). `AuthSession.isAnonymous` comes from the user's `is_anonymous`. `anonymous_provider_disabled` maps to its own message.
- **License client:**
  - `LicenseClient.startActivation(ActivationDetails)` sends a code, or a new e-mail account's business;
  - `CodeNeedsBusiness` and the refusals' French messages;
  - `renewWithCode` (`code-redeem`, then a check-in).
- **`ui/onboarding/OnboardingViewModel`** does the account side and reports `OnboardingStatus`:
  - busy, error;
  - `needsBusiness` (a code without a company, or an account without a business);
  - `seatTaken` (the transfer dialog);
  - `codeRequired` (an e-mail sign-up on a phone that already used its trial: the code screen, whose code then
    renews).
- **The code screen** checks the Luhn character before asking the server.
- **Where the onboarding opens:**
  - at launch on a fresh install (no account, no license, no products, clients, sales or purchases:
    `AppMetaDao.hasBusinessData`), where Back leaves the app;
  - from Compte when signed out: « Se connecter », « Créer un compte », « Activer avec un code ».
- **Removed:** the old sign-in and sign-up screens, and the « Aperçu » button.
| **C3** | Compte: « Votre code » (kept encrypted on the phone: only it can show the code again), « Saisir un code », « Ajouter une adresse e-mail », a sign-out warning for code accounts. | **done, 2026-10-14**: 523 unit tests pass, and the owner's test on the M34 passed (codes in batch « Test C3 — téléphone ») |
| **C4** | « Administration » in DistriGo, for the owner only: create batches, follow them, check and revoke a code, export as WhatsApp text and PDF cards. A nightly cleanup of anonymous accounts that never got a business. | **done, 2026-10-15**: server live; app 539 unit tests; walked on the owner's M34 (codes, check and revoke, PDF cards); « Comptes clients » used by the owner |
| **C5** | Compte redesigned from the owner's mockup (2026-10-14), with « Informations de l'entreprise »: the business-profile function reads and changes the name, phone, activity and sales reps. | **done, 2026-10-14**: server live; app 526 unit tests, approved by the owner on the M34 |

## C5: « Informations de l'entreprise » (business-profile)

- **Migration `20261014180000_business_profile.sql`:**
  - `private.business_of(user, business?)` finds the caller's business: the one named, or their only one. It
    returns no_business, choose_business or not_member otherwise.
  - `private.business_profile` reads the name, contact_phone, activity, sales_reps and can_edit (owner or
    manager).
  - `private.update_business_profile(user, changes jsonb, business?)` changes only the keys present; a null
    or blank contact_phone clears it. Agents get not_allowed.
  - The three functions are revoked from public, anon and authenticated by name: the private schema's
    default privileges cannot take back the EXECUTE that PUBLIC gets on every new function (the privilege
    test failed until this was added).
- **POST business-profile:** `{}` reads; any of name, contact_phone, activity, sales_reps changes them. The
  function refuses a blank or long name, an unknown activity or range, and a phone over 30 characters
  (bad_request).
- **Tests:** 63 SQL checks (7 new in `business_profile_test.sql`), 41 Node tests, deno check on all seven
  functions.
- **Live, 2026-10-14:**
  - Deployed: db push, and the `business-profile` function only. The other six are unchanged in behaviour.
  - Smoke test: GET → 405, no session → 401, app roles without EXECUTE.
  - The owner's profile was read (« DISTRIGO », its phone, activity and reps still empty), and a change was
    made in a rolled-back transaction.
  - A throwaway anonymous account got no_business, and a blank name → 400. The account was deleted.
- **The app, approved by the owner on the M34 (2026-10-14):**
  - Compte is rebuilt in the onboarding's identity: Plus Jakarta Sans and DsBrand colours, white cards on Mist,
    the wave at the end of the page. The top bar is pinned, with a hairline once the page scrolls.
  - The cards follow the mockup: the business (name, phone, how the account signs in), « Votre code
    d'activation », « Informations sur l'abonnement », « Informations de l'entreprise » (business-profile,
    each row in its own dialog), « Gestion du compte ».
  - One change from the mockup: the status pill sits in a « Statut » row. Next to the card's title it pushed
    the title onto two lines at phone width.
  - Long values take two lines. An e-mail sign-up on a phone that already had its trial reads « Essai déjà
    utilisé ».
  - Sizes come from `CompteSize`, measured against a reference app the owner chose. Plus Jakarta Sans is wide,
    so the text sits one step below the usual sizes.
  - The last profile read is kept on the phone (`BusinessProfileStore`), so Compte shows the business without
    Internet.

## C4: « Administration » and the cleanup

**The owner's decisions (2026-10-14):**
- The codes are managed in DistriGo, on an « Administration » screen for the owner only, not on a web page.
- Exports are a WhatsApp text and printable PDF cards.
- Anonymous accounts without a business are deleted after 7 days.
- The screen uses Compte's design language (ui/designsystem/BrandComponents.kt).

**Server (migration `20261014200000_codes_admin.sql`, function `codes-admin`), live 2026-10-14:**
- **Who may use it:** `private.is_admin(user)` is true only when the account's app_metadata says
  `"distrigo_admin": true`. Only the service role writes app_metadata. The owner's account
  (ayoubhezam05@gmail.com) was marked on 2026-10-14:
  ```sql
  update auth.users set raw_app_meta_data = raw_app_meta_data || '{"distrigo_admin": true}'::jsonb
  where email = '…';
  ```
- **POST codes-admin:** anyone else gets not_allowed. Its actions:
  - `batches` reads `codes_by_batch`;
  - `create` takes a count (1 to 200), plan solo, days, a batch, and optionally a company, expires_on and a
    note. It answers the codes once. Without expires_on they expire in 12 months; expires_on null means
    never;
  - `status` and `revoke` work on one code.
- **Cleanup:** `private.cleanup_anonymous_accounts(after default 7 days)` deletes the anonymous accounts that
  never had a membership. Members past or present, and accounts given an e-mail, stay.
  - pg_cron (installed by the migration) runs it every night at 02:30 UTC, as job `distrigo-anonymous-cleanup`.
- **Tests:** 67 SQL checks and 47 Node tests pass, with deno check on all eight functions.
- **Live smoke test:**
  - The cron job is active, and the app roles can't execute the functions.
  - GET → 405, no session → 401.
  - A throwaway anonymous account was refused both batches and create (not_allowed), and no batch was made.
    The account was deleted.

**App (2026-10-15):**
- **Shared design pieces.** Compte's pieces now live in `ui/designsystem/BrandComponents.kt`, for every screen
  in the new design language:
  - `BrandScreen` (pinned top bar, scrolling content, the wave), `BrandCard`, `BrandCardHeader`, `BrandInfoRow`,
    `BrandActionRow`, `BrandPill`, `BrandNotice`, `BrandMessage`, `BrandWideButton`, `BrandDialog`,
    `BrandChips`, `BrandCodeText`;
  - `BrandSize`, formerly CompteSize.
- **The session carries `isAdmin`** (app_metadata `distrigo_admin`). Opening Compte refreshes the session, so the
  flag appears without signing out. `FunctionCalls` is the one way the app calls an Edge Function, for
  LicenseClient and `CodesAdminClient`.
- **« Administration »**, from Compte → Gestion du compte, for the owner only. Its view model belongs to its own
  screen, so merchants opening Compte never call codes-admin.
  - The batches, each with its free codes (« 41/50 libres ») and what the others became.
  - « Nouveau lot »: name; 1/5/10/20/50/100 codes; 1 mois/3 mois/6 mois/1 an; codes valid 12 mois/6 mois/sans
    limite; optional company and note.
  - « Codes créés »: the codes, shown once, with a warning before leaving unshared; « Envoyer sur WhatsApp »
    (WhatsApp or WhatsApp Business, else the share sheet) and « Cartes PDF à imprimer ».
  - « Vérifier un code »: what a code became; revoke one still free, or a key.
- **PDF cards:** ten to an A4 page (2 × 5, about 93 × 52 mm), with dashed cutting lines. Each card has
  « DistriGo » with « Go » in orange, the code, the plan and duration, the deadline, the company if any, and
  « Ouvrez DistriGo → « Activer avec un code » ». The PDF is written to `cache/codes/`, the last export only, and
  shared through the FileProvider.
- **Found on the phone and fixed:**
  - « Quitter » on the codes' screen went back two screens: the closing screen asked to leave a second time.
    Its exit now pops only while it is the current screen.
  - The variable font's lighter weights gave spaces almost no width in the PDF. The card's words are now
    placed one by one.
- **Test batches:** « Test C4 », « Test C4 PDF » and « Test C4 PDF 2 », 8 codes, all revoked.

**« Comptes clients » (added to C4 on 2026-10-15, at the owner's request).** The owner had no way to cancel a client's
account.
- **Migration `20261015090000_clients_admin.sql`:**
  - `private.find_businesses(query)` searches by a part of the name, the phone's digits (4 or more), a part of an
    e-mail, or one of the business's codes. With no query it returns the 30 latest.
  - `private.set_business_suspended(admin, business, suspended, note)` sets subscriptions.status to suspended or
    active, adding the note.
  - `private.delete_business(admin, business)` deletes the business (subscription, seats and memberships go with
    it); its key code then opens nothing.
  - Both refuse the owner's own business (own_business).
- **codes-admin actions:** `businesses`, `suspend`, `reactivate`, `delete_business`.
- **Suspension:** no license is issued, so activation is refused (« L'abonnement est suspendu ») and each phone's
  license ends at its next check-in, within the 14-day offline window at most until L4 checks in on its own.
- **App:** « Comptes clients » in Administration. Search; each client shows who it is, its subscription and a
  status pill (Actif, Essai, Expiré, Suspendu). Opening one shows its details with Suspendre (with an optional
  reason) or Réactiver, and Supprimer le compte, each confirmed. The owner's own business is marked « (vous) »
  and offers no action.
- **Tests:** 73 SQL checks and 52 Node tests pass; the app has 539 unit tests. Deployed 2026-10-15 (db push,
  codes-admin).
- **Clean-up on 2026-10-15:** the owner's test accounts « ALI OEUF » (by code) and « DOLIPRANE SARL » (e-mail) were
  deleted on the server, at the owner's request.
- **Phone, 2026-10-15:** the owner deleted « Client Test C4 » and « SARL FATEH » himself from « Comptes clients ». The
  test client's throwaway account was deleted afterwards. Only « DISTRIGO » remains.


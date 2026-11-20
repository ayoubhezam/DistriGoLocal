# Google Play Billing (Solo release point 5)

Status, 2026-11-20: **server prepared, app not started, nothing live.** The billing switch stays off until the Play
Console exists. Owner's decisions (2026-11-18/20):

- Play Billing is mainly for users **outside Algeria** (Gulf, France, other Arab countries). In Algeria, activation
  codes stay the local way to pay. Algeria supports developer and merchant registration (default currency USD).
- Base prices in **USD**, converted by Play, with room for regional prices. Placeholders until the Console exists:
  monthly **9.99 USD**, yearly **99.99 USD**. The owner sets the final numbers and any local override there.
- **No free-trial offer in Play**: the 30-day trial stays ours, on the server, one per phone.
- **Codes and Play together: the later end date wins, no stacking.**
- **Anyone signed in can buy, but an account made with a code must first add an e-mail.** An anonymous account is
  lost when the app is uninstalled, and a Play purchase tied to it would be orphaned.

## 1. Products

| Product ID | Base plans | Plan | Days per period |
|---|---|---|---|
| `distroniq_solo` | `solo-monthly` (P1M, auto-renewing), `solo-yearly` (P1Y, auto-renewing) | `solo` | 30 / 365 |
| later: `distroniq_business_5`, `_10`, `_20` | `monthly`, `yearly` | a Business plan of that many phones | |

Product IDs are permanent in the Play Console. Play subscriptions are not bought in quantities, so Business seats come
as one product per number of phones. The catalogue the server accepts is `CATALOGUE` in
`supabase/functions/_shared/play.ts`: add a product there before selling it.

## 2. How a purchase becomes a license

```
app ── launchBillingFlow (obfuscatedAccountId = SHA-256 of the business id) ──▶ Google Play
app ── play-verify {purchase_token, product_id} ──▶ Edge Function ── subscriptionsv2.get ──▶ Google
                                                     │ checks: e-mail account, owner of a business, product we sell,
                                                     │         the purchase's account id is this business's
                                                     ├─ private.record_play_subscription (one row per token)
                                                     └─ acknowledge (Google refunds what is not acknowledged in 3 days)
Google ── RTDN ──▶ Pub/Sub topic ── push (signed OIDC token) ──▶ play-rtdn ── subscriptionsv2.get ──▶ record
app ── license-refresh (check-in) ──▶ issue_license: last day = later of subscriptions.valid_to and the Play expiry
```

- **Entitled states:** `ACTIVE`, `IN_GRACE_PERIOD`, and `CANCELED` until its expiry ("will not renew": paid time
  still runs). `ON_HOLD`, `PAUSED`, `EXPIRED` and the pending states give nothing. A refund is reported by Google as
  expired. A voided-purchase notification makes the server ask Google again.
- **Payments:** one `private.payments` row per Google order (`provider = google_play`, `external_ref` = the order
  id), so a renewal is counted once, however often it is reported.
- A trial business that buys moves to the `solo` plan. A refused trial's « no grace » ends, as with a code.
- A purchase stays with the business it was first recorded for (`other_business` otherwise).
- After an uninstall or on a new phone, the app asks Play for the account's purchases and sends each one to
  play-verify again: that is « restore purchases ».

Code: migration `20261118140000_play_billing.sql`, `supabase/functions/_shared/google.ts` (Google client, Pub/Sub token
check), `supabase/functions/_shared/play.ts` (the two operations), `play-verify/`, `play-rtdn/`. Tests:
`supabase/tests/local/play_billing_test.sql` (13 checks) and `supabase/functions/tests/play.test.ts` (a fake Google).

## 3. The app (step 4, after the owner's review)

- A billing layer over the Play Billing Library, behind an interface, with a fake for the tests. It handles pending
  purchases and restores existing purchases at start-up and after sign-in.
- Abonnement becomes the paywall: the monthly and yearly cards with Play's own localised prices, « S'abonner »
  (Google's payment sheet), then « Abonnement actif jusqu'au … » and « Gérer mon abonnement » (Play's subscription
  page). « J'ai un code d'activation » stays below, folded. French and Arabic.
- An account made with a code is asked to add an e-mail first (Compte already has « Ajouter une adresse e-mail »);
  only then do the purchase buttons show. The server refuses it too (`email_required`).
- **A switch, off until the Console exists:** until then the app looks as it does today, with no purchase button.

## 4. Once the Play Console exists: the checklist

In order. Each step says who does it.

1. **Owner — Play Console:** create the app `com.distrigo.app`. Upload a release-signed bundle (AAB) to the
   **internal testing** track. Products can only be created once a build that contains the Billing Library is uploaded.
2. **Owner — Monetize › Subscriptions:** create `distroniq_solo`, with base plans `solo-monthly` and `solo-yearly`,
   auto-renewing. Set the USD base price (placeholder 9.99 / 99.99) and any regional price. **No free-trial offer.**
   Turn on the **grace period** and **account hold**.
3. **Owner — Google Cloud:** in the project linked to the Play Console, enable the **Google Play Android Developer
   API** and create a **service account**. Download its JSON key (never in the repository).
4. **Owner — Play Console › Users and permissions:** invite the service account's e-mail, with the right to view
   financial data and manage orders and subscriptions.
5. **Claude — Supabase secrets:** `PLAY_SERVICE_ACCOUNT_JSON` = the key file's content. Then deploy `play-verify` and
   `play-rtdn`, and push migration `20261118140000` if it is not yet live.
6. **Owner (with Claude) — Pub/Sub:**
   - create the topic `play-rtdn`;
   - give `google-play-developer-notifications@system.gserviceaccount.com` the **Pub/Sub Publisher** role on it;
   - create a **push** subscription to `https://nwzriieoptyrdttyxdlx.supabase.co/functions/v1/play-rtdn`, with
     authentication on, a service account of the project, and that URL as the audience.

   Then set the Supabase secrets `PLAY_RTDN_AUDIENCE` (that URL) and `PLAY_RTDN_SERVICE_ACCOUNT` (the push
   subscription's service account e-mail).
7. **Owner — Play Console › Monetization setup:** set the RTDN topic `projects/<project>/topics/play-rtdn`, then
   « Send test notification ». play-rtdn answers `test`, visible in the function's logs.
8. **Owner — License testing:** add the testers' Google accounts. Their purchases are free and renew quickly.
9. **Claude — the app's switch on, in a test build.** Then, on a phone with a tester account:
   - buy monthly;
   - check that the license runs to Play's date;
   - cancel, then check that the paid time still runs;
   - refund from the Console, then check that the license falls back to the subscription's own end;
   - check a renewal and a restore on a second install.
10. **Owner and Claude — before going public:**
    - add the Play Billing paragraph to the privacy page (§4, point 4 of 2026-11-18): payments are handled by Google,
      which sends us only the order number, product, dates and status;
    - read Play's current Payments policy together (see §5);
    - set the final prices.

## 5. Open questions

- **Codes inside a Play-distributed app.** Play's Payments policy governs apps that unlock paid features with codes
  bought outside Play. The terms already say nothing about where to buy codes, and the app links to no purchase page.
  Read the policy's current wording with the Console open. If needed, use Play's own promo codes for Play users and
  keep ours for Algeria.
- **An EU representative (GDPR art. 27)** before actively selling in France (owner's decision, noted 2026-11-18).

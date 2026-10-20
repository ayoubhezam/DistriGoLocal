# Dashboard and « Objectif » widget: UX strategy

*2026-10-17. Inputs reviewed: the Figma export (`App.tsx`, `HomeScreen.tsx`), the app's current Dashboard, the
commission engine behind « Politique de commission », and the tournée data. No code: this is the plan only.*

*Updated 2026-10-17 with the owner's decisions. Locked rules are marked **Decided**; the questions still open are
listed at the end.*

---

# Decisions locked (owner, 2026-10-17)

| # | Rule |
|---|---|
| D1 | **The 7-day chart is removed completely**, from the Dashboard and from every other screen of this plan. |
| D2 | **« Créances & dettes » is removed from the Dashboard.** Debts are read in Clients and Rapports. The Dashboard stays fast, light and focused. |
| D3 | **« Ce mois » merges into the Objectif widget.** |
| D4 | **Every term matches the app.** No delivery status (« Livré / En attente »); « Bons » is replaced; « Dépenses » becomes « Charge ». |
| D5 | **« Payé à la vente » replaces « Espèces encaissées ».** For the day: what was sold, what was paid on the spot, and the credit left. |
| D6 | **The comparison is today vs. yesterday's sales**, not the same day last week (detail in D10). |
| D7 | **« Clients du jour » keeps its horizontal scroll.** There's no gesture conflict: the Dashboard isn't inside a swipe (only the client, supplier and product detail screens swipe between tabs). The « Tous / Ventes / … » filter belongs to « Activité récente » only. |
| D8 | **The Figma design is adapted to the app's current look** (Layers 1–4): flat white cards, thin border, no shadow, Mist background, brand navy titles. |
| D9 | **The commission engine counts dépôt and tournée sales together.** |
| D10 | **Yesterday at the same hour:** « +12,4 % vs hier à cette heure ». « Hier » is the last day that had sales, so the day after a day off still compares with something. |
| D11 | **The « Activité récente » filter is « Tous / Payées / À crédit ».** |
| D12 | **« Alertes » stays** (stock-outs, low stock, old debts), shown only when there is one. |
| D13 | **The five remaining commission rules are adopted** (Part 2, Step 1): returns subtract, the % rounds down, the objective is the first tier, a switch shows or hides the bonus, a closed period is frozen. |

| D14 | **« Nouvelle vente »** opens the tournée sale when a tournée is open, otherwise Dépôt Vente (owner, 2026-10-20). |
| D15 | **« Encaissement »** opens a picker of the clients who owe something (largest balance first, with search), then the same « Versement » dialog as the client's page, without leaving the Dashboard (owner, 2026-10-20). |
| D16 | **The header reads « Bonjour, Ayoub »**: the account's first name, the date under it and the open tournée's chip; « Bonjour » alone for an account without a name (owner, 2026-10-20). |
| D17 | **Back to the Figma design, fewer numbers** (owner, 2026-10-20, mockup `dashboard.png`): one blue card — « Ventes du jour » centred with its change as a pill beside it, never a sentence of comparison; under it « Objectif du mois » (bar, « réalisé / objectif DA », %), or the month's sales with « Fixez un objectif » without a policy; then « Payé à la vente » and « Crédit ». The separate Objectif and « Ce mois » cards are gone (pace, bonus and next-step sentence move to « Mon objectif », step 4). Order: quick actions, Tournée du jour, Clients du jour, Alertes, Activité récente (4 sales at most). |
| D18 | **In « Paliers » the bar aims at the next tier** (owner, 2026-10-20): « Objectif du mois · Palier N », « réalisé / seuil du palier N », the share of that threshold, from zero; once every tier is passed, the last one. A middle tier reached is not the goal reached. The goal's text and bar are white below 100 %, light green (#86EFAC) at exactly 100 %, gold (#FFD54F) beyond; the % rounds down as everywhere (D13). `GoalProgress.target` holds the rule. |
| D19 | **No comparison at all on « Ventes du jour »** (owner, 2026-10-20): its title and amount only, no percentage or arrow; this replaces D10 and the pill of D17. « Payé à la vente » is what was paid on today's sales at the time, each capped at its total; « Crédit » is the rest. A later versement or a client return does not change them. A client in « Clients du jour » opens the tournée's own action sheet (sale for that client, navigation, call, « sans vente », removal). |

---

# Part 1: The dashboard

## Layout, from top to bottom

1. **Header:** the greeting, the date and the tournée chip. Keep the menu button: the drawer is the only way to reach
   Clients, Rapports and the rest.
2. **Aujourd'hui** (the blue card):
   - « Ventes du jour »: dépôt + tournée;
   - « Payé à la vente » (D5) and « Crédit » (sold minus paid on the spot);
   - the comparison with yesterday at the same hour (D6, D10), written out: « +12,4 % vs hier à cette heure ».
3. **Objectif** (Part 2). It includes « Ce mois » (D3): with no policy set up, it shows the month's sales and margin,
   so the slot is never empty.
4. **Quick actions** (4): « Nouvelle vente », « Encaissement », « Nouvel achat », « Charge », the set chosen on
   2026-10-07. « Catalogue » is dropped because Produits is already a tab; « Dépenses » is the app's « Charge » (D4).
5. **Alertes** (D12): stock-outs, low stock and old debts, compact, shown only when there is one.
6. **Tournée du jour:** shown while a tournée is open today. Progress (« 8 sur 37 clients visités »), then « Avec
   achat », « Sans achat », « En attente ». The app has the data: a client can be marked visited without a sale, and
   sales are linked to the tournée.
7. **Clients du jour:** the horizontal row (D7), with each client's status and an « Ajouter » that adds clients to the
   tournée, then « Voir tout ». Shown with the Tournée card.
8. **Activité récente:** the latest sales, each opening the sale, with its own filter « Tous / Payées / À crédit »
   (D11). Each row shows the
   client, the amount, the time and a payment pill computed from the sale: « Payé », « Partiel » or « Impayé ». No
   delivery status (D4).

**Removed:** the 7-day chart (D1), « Créances & dettes » (D2), the separate « Ce mois » card (D3).

## Style (D8)

| | Figma design | Built as |
|---|---|---|
| Cards | Shadows, blue glow on the top card | White, thin border, no shadow, on Mist |
| Top card | Gradient with orbs and a glow | Brand blue; its color alone sets it apart |
| Corners | 24 px | 18 dp |
| Text colors | Slate grays | Near-black navy for text, brand navy for titles |
| Smallest text | 9–10 px | 11 sp |
| Avatars | Pastel color picked per client | The app's avatar palette |

## Accessibility details

- **Minimum text 11 sp.** In the Figma, the client names, « Ajouter client » and the Tournée labels are 9 px.
- **Contrast.** Light gray on white (« 8 sur 37 clients visités », the sale reference lines) is about 2.6:1, below the
  4.5:1 minimum. Pale blue labels on the blue card are about 3.9:1.
- **Touch targets.** The activity filter buttons are about 24 px tall, half the 48 dp minimum.
- **Money format.** Use the user's money format everywhere, with the same decimals (the Figma mixes « 48 250,00 » and
  « 36 870 »).
- **Bars and figures.** Each progress bar needs a spoken description (« 74 % de l'objectif »). Figures may count up on
  screen, but only when they change, and never when animations are turned off.

---

# Part 2: Linking « Objectif » to « Politique de commission »

What the engine does today shapes everything below:

- **Four kinds of reward:** « Taux fixe », « Forfaitaire », « Gratuité » and « Paliers ».
- **Two measures:** « Ventes facturées » and « Recouvrement », over a month, quarter or year.
- **No screen shows the policy's result yet.**

The widget will be the first place the policy becomes visible, so anything unclear in the policy will show up there.

## Step 1: The rules behind the widget

**Decided:**

- **Scope (D9):** « Ventes facturées » counts dépôt and tournée sales together. « Recouvrement » then counts payment at
  sale for both, plus separate payments, which makes the two consistent.
- **Note for the owner:** any target saved while only tournée sales counted will now be reached faster, because dépôt
  sales add to it. Review it after the change.

**Also decided (D13):**

1. **Returns** subtract from the counted sales within the period; otherwise the measure could be gamed.
2. **The percentage rounds down.** The engine used to round 99,6 % up to 100 % while the target wasn't reached; now
   « 100 % » shows only when the target is actually reached.
3. **With tiers, the objective is the first tier**, and the policy form enforces it.
4. **A switch « Afficher la prime sur le tableau de bord »** in the policy shows or hides the bonus. In Solo, the
   person holding the phone may be the owner, who pays it; with the switch off, the objective works without the
   bonus.
5. **A closed period is frozen.** A later edit to an old sale can't change a bonus already paid.

## Step 2: One calculation, one "progress snapshot"

The widget never calculates anything itself. The policy feeds a single calculation (the existing engine, extended),
which produces a snapshot that every screen reads:

- the period and days left;
- the measure, with its label;
- the value reached, the target and the percentage;
- the status: no policy, in progress, reached, top tier, period closed;
- the bonus earned so far;
- the next milestone: its threshold, the gap, and the bonus once there;
- each tier's state;
- the daily pace needed and the pace so far;
- the projection at the current pace.

It recalculates whenever a sale, payment or return is saved or deleted, when the policy changes, and when the day
changes. The reports already reload this way, so the mechanism exists. Each closed period keeps its snapshot, and those
snapshots form the history.

## Step 3: One widget, four variants, in the same words as Paramètres

| Policy type | What the card shows |
|---|---|
| « Taux fixe » | A bar to the target and a locked bonus, « Prime à débloquer : 3 % de vos ventes ». Once reached, the bonus counts up live: « + 300 DA par 10 000 DA vendus ». |
| « Forfaitaire » | A bar plus a locked chip « 15 000 DA à débloquer », which opens once the target is reached. |
| « Gratuité » | The product's photo with « 6 cartons offerts », locked, then unlocked. |
| « Paliers » | One bar with a tick at each threshold (P1, P2, P3, each with its rate), filled up to the current value, and the current tier named. |

Reuse the policy screen's exact words: « Palier », « Seuil », « Taux », « Prime forfaitaire », « Gratuité », « Ventes
facturées », « Recouvrement ». The same words in both places is what lets the user see how the two connect.

## Step 4: One motivating sentence

At a glance the card shows three layers:

1. **The progress:** « 888 000 DA sur 1 200 000 · 74 % ».
2. **One sentence about the next step**, always the nearest milestone rather than the final goal:
   - « Plus que 312 000 DA en 8 jours · environ 39 000 DA par jour ».
   - With tiers: « Plus que 12 000 DA pour le palier 2 : votre prime passe de 17 760 à 22 500 DA ». The engine pays
     the tier's rate on the *whole* amount, so each tier is a jump. That's the strongest hook; show it.
3. **The pace:** a chip « En avance », « Dans les temps » or « En retard », with an icon and text, not just a color.
   Next to it: « À ce rythme : 1 280 000 DA fin du mois ».

The gap can also be turned into sales using the average basket the app already computes: « ≈ 8 ventes de plus par
jour ». That's concrete for someone on the road.

*Example tiers used above: P1 600 000 DA → 2 %, P2 900 000 DA → 2,5 %, P3 1 200 000 DA → 3,5 %. At 888 000 DA, P1
pays 2 % × 888 000 = 17 760 DA; at P2, 2,5 % × 900 000 = 22 500 DA.*

## Step 5: The card changes with the period

| Moment | What the card does |
|---|---|
| No policy | Shows « Ce mois » (D3): the month's sales and margin, with a discreet « Fixez un objectif » that opens Paramètres. |
| First days | Shows the daily pace needed rather than a nearly empty bar. |
| Above 90 % | The remaining amount takes the emphasis. |
| Target reached | A one-time celebration, short and plain; the bonus unlocks, then the card moves to the next tier or the surplus. |
| Top tier | « Palier max atteint ». |
| Policy changed mid-period | A note « Politique modifiée le … ». |
| Period over | A summary (« Août : 98 % · à 20 000 DA près »), then the new period starts. |

## Step 6: The detail screen « Mon objectif », the bridge to the policy

Tapping the card opens a screen with:

- **the rule in plain French, generated from the policy:** « Vous touchez 2,5 % de vos ventes dès 900 000 DA ce
  mois-ci… »;
- **the tier ladder:** reached ✓, current, next with its gap, the rest locked;
- **the pace, as text:** needed per day, so far per day, and the projection (no chart, D1);
- **past periods**, achieved or not, with the bonus;
- **« Modifier la politique »**, which opens Paramètres.

## Step 7: Close the loop from Paramètres

On « Politique de commission », show a live preview while editing: « Avec cette politique, ce mois-ci : 74 % · prime
0 DA ». The owner sees the effect of a target or a tier before saving it.

## Step 8 (optional): Reminders

Local notifications at the key moments, at most one a day:

- a tier is reached;
- « Plus que X pour le palier 3 »;
- three days before the period ends.

---

# Order of work, with L5

Every question is answered; the plan is final. L5 (license enforcement) also gates « the Dashboard's quick actions »,
so the two overlap there. Work on them in turn, one change set at a time:

1. **The commission calculation and its snapshot**, with D9 and D13, tested on their own. No overlap with L5.
2. **L5**, as planned in `docs/license_architecture.md`.
3. **The new Dashboard** (Part 1), with the Objectif card. The quick actions are built once, behind L5's gate from the
   start.
4. **« Mon objectif »**, then the Paramètres preview, then the reminders.

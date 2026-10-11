# DistriGo: review of the onboarding and sign-in mockup

Review of the mockup `DistriGoUI.png` (8 phone screens), written 2026-10-10 before any code. It holds the
UX opinion, the changes proposed, the decision still open, and the list of assets to prepare.

## Context

- **DistriGo** is an Android app (Kotlin, Jetpack Compose) for distributors and wholesalers in Algeria. It
  covers the dépôt stock, van sales (*tournées*), sales, purchases, clients, suppliers, inventory and
  reports. The UI is in **French**.
- **Offline-first:** all business data (clients, sales, stock) stays on the phone. The account exists only
  for the **subscription**.
- **Accounts:** Supabase Auth with **email + password**, or **Google sign-in**. Email confirmation is off
  for the beta.
- **License:** a 30-day free trial starts when the phone is activated. The license is a signed document
  that keeps working offline for up to 14 days.
- **Current design system** (`DsColors`):
  - colors: primary indigo `#5B6EF5`, success `#12B76A`, warning `#F79009`, danger `#F04438`, neutrals
    from `#101828` to `#F9FAFB`;
  - shapes: corner radii of 10, 14 and 18 dp;
  - text: system font (Roboto), light theme only;
  - app icon: still Android Studio's default.
- **Icons:** Material Icons Extended is already a dependency.

## The mockup's 8 screens

1. **Welcome:**
   - DistriGo logo (a "D" with a truck and speed lines, tagline « GÉREZ. DISTRIBUEZ. DÉVELOPPEZ. »).
   - Photo of a warehouse and a blue truck carrying the logo, under a navy gradient.
   - « La solution complète pour les distributeurs et grossistes », with a subtitle.
   - Orange « Commencer → » button and page dots.
2. **Value page 1:** illustration (boxes, shelves, scanner, checklist), « Gérez vos produits et vos stocks ».
   Three feature rows with icons: Produits & catégories, Inventaire et mouvements, Gestion des dépôts et
   camions. « Passer » and a round navy arrow button.
3. **Value page 2:** illustration (phone with a client list, rep with a tablet), « Suivez vos ventes et vos
   clients ». Rows: Gestion des clients, Ventes retours et règlements, Crédits et suivi des encours.
4. **Value page 3:** illustration (laptop with charts), « Pilotez votre activité ». Rows: Tableaux de bord,
   Rapports et analyses, Croissance de votre business. Button « Créer mon compte → ».
5. **Connexion:**
   - Logo, « Accédez à votre espace en toute sécurité ».
   - Fields « Téléphone ou e-mail » and « Mot de passe » (eye icon), « Mot de passe oublié ? ».
   - Navy « Se connecter → », then « ou » and « Se connecter avec Google ».
   - « Pas encore de compte ? Créer un compte », with a blue wave in the bottom-left corner.
6. **Créer un compte, 1/2:** « Renseignez vos informations personnelles ». Fields: Nom complet, Téléphone,
   E-mail (facultatif). Button « Continuer → ».
7. **Votre activité, 2/2:** fields Nom de l'entreprise, Type d'activité (list), Nombre de commerciaux
   (list). Checkbox « J'accepte les conditions d'utilisation ». Button « Créer mon compte → ».
8. **Success:**
   - Green check with confetti, « Compte créé avec succès ! Bienvenue sur DistriGo ».
   - « Offre découverte » card (crown badge): Durée 30 jours, Expire le (date à définir).
   - Benefits: Accès à toutes les fonctionnalités de base, Support inclus.
   - « Accéder à l'application → », « Merci de votre confiance ! ».

## Verdict

**It's a good fit, and it would lift DistriGo's first impression.** The style is clean, trustworthy and
B2B: navy and royal blue, light-blue tinted icon circles, generous white space, bold rounded headings, and
one illustration style throughout. The structure is right too: welcome, 3 value pages (skippable),
sign-in, 2-step sign-up, then a success screen presenting the trial.

## Changes proposed

Seven things would collide with what is already built or with how DistriGo is used. The first three are
must-fixes; the other four are improvements.

| # | In the mockup | Why it's a problem | Proposal |
|---|---|---|---|
| 1 | **"Téléphone ou e-mail"** to sign in, and email marked optional at sign-up | Accounts are email + password (or Google). Phone login needs an SMS provider, which is paid, needs setup, and delivery to Algerian numbers is uncertain. | Email (or Google) is the login. Keep **phone as a contact field** (useful for support and password resets), not as a login. |
| 2 | **Sign-up has no password field** | Without Google, an account can't be created. | Add *Mot de passe* to step 1. A Google sign-up skips step 1 entirely (Google supplies name and email) and goes straight to « Votre activité ». |
| 3 | **Welcome page only has « Commencer »** | Anyone switching phones, reinstalling, or taking back a transferred seat must sign in, not sign up. | Add **« J'ai déjà un compte »** on the welcome page and the value pages. |
| 4 | Value pages show generic features (« Croissance de votre business ») | DistriGo's real differentiators are missing. | Replace with concrete ones: **« Fonctionne sans Internet, même en tournée »**, **« Vos données restent sur votre téléphone »**, **Tournées et chargements camion**, **Reçus imprimés en Bluetooth**. |
| 5 | Success screen: « Expire le (date à définir) » | The exact date is known: the license's end date. | Show the real date (e.g., *9 nov. 2026*), and make this screen the moment activation completes. Today that's the « DistriGo est activé » dialog. |
| 6 | Step 2 asks *Type d'activité* and *Nombre de commerciaux*, with a terms checkbox | Nothing stores these yet, and the checkbox needs real documents. | Worth keeping, since it tells you who your users are for the Business plan, but it needs a small migration (two columns on `businesses`) plus real **CGU + privacy policy** text. |
| 7 | Full-screen warehouse **photo** on the welcome page | A large photo makes the APK heavier and slows the first frame on mid-range phones. The truck also needs the logo on it. | Use a strongly compressed image (target under ~200 KB), or an illustration in the same style as the other pages. |

**Two smaller points:**

- **Contrast:** the light grey placeholder and caption text in the mockup sits around 2.5:1 contrast,
  which is hard to read in a van in sunlight. Keep text at 4.5:1 or above and touch targets at 48 dp or more.
- **When onboarding shows:** only on a fresh install, never to someone updating with data already on the
  phone. They get a simple « Activez votre compte » invitation instead.

## One decision for the owner: the brand color

The app today uses an **indigo** primary (`#5B6EF5`). The mockup is **navy / royal blue / orange**. If only
the onboarding changes, people go from a navy-and-orange welcome into an indigo app, and it feels like two
products.

**Recommendation: adopt the logo's colors app-wide.** The app's colors are defined in one place
(`DsColors`), so it's a small change, but every screen should then be checked. The alternative is to draw
the onboarding in the current indigo with orange as the brand accent.

Related: the mockup uses orange for the welcome button and navy for every other main action. Pick one
main-button color for the whole flow.

## Assets to prepare

**Not needed:**

- **Icons:** every one in the mockup (box, clipboard, truck, person, card, shield, chart, document, lock,
  eye, phone, building, crown, calendar, check, arrows) exists in Material Icons Extended, already in the app.
- **Drawn in code:** the background waves, gradients, page dots, step indicator, form fields and the
  confetti check.

**Needed:**

| # | Asset | Format and specs | Notes |
|---|---|---|---|
| **Brand** | | | |
| 1 | **Logo mark** alone (the "D" + truck + speed lines), no text | **SVG**, transparent background | Becomes the app icon, splash and headers |
| 2 | **Full logo**: mark + "DistriGo" + tagline, stacked as in the mockup | **SVG** | The wordmark looks custom, so it's needed as a file rather than set in a font |
| 3 | **White version** of the full logo | SVG | For dark or photo backgrounds |
| 4 | **Exact brand colors** | Hex values from the logo source | Eyeballed guesses: navy ≈ `#0F2D6B`, blue ≈ `#1E6BE5`, orange ≈ `#F7931E`. They should come from the file, not the screenshot. |
| 5 | **Heading font** | TTF or OTF, **open license (OFL)**, SemiBold / Bold / ExtraBold | The mockup's rounded headings look like Nunito, Baloo 2 or Poppins. Any Google Font is fine. Body text can stay the system font. |
| **App icon** (today the app still has the default Android icon) | | | |
| 6 | Launcher icon, **foreground layer** | SVG, or PNG 432×432; the mark inside the central 66% (adaptive-icon safe zone) | |
| 7 | Launcher icon, **background** | A color, or a simple SVG | |
| 8 | **Monochrome** version of the mark | Single-color SVG | For Android 13+ themed icons |
| **Illustrations** (all from the same set or the same artist, matching the brand colors) | | | |
| 9 | **Welcome hero**: warehouse + truck carrying the logo | PNG or JPG, at least 1440 px wide | Or an illustration instead of a photo (change 7) |
| 10 | **Page 1**: products & stock (boxes, shelves, scanner, checklist) | Transparent PNG at least 1200 px wide, or SVG if flat (no blurs or shadows) | |
| 11 | **Page 2**: sales & clients (phone with a client list, rep with tablet) | same | |
| 12 | **Page 3**: dashboard & reports | same | With change 4, a page about **offline & privacy** (rep in the field, phone with a lock or cloud-off icon) would be stronger |
| 13 | *(optional)* **No connection** illustration | same | For a sign-up attempted without Internet |
| **Third party** | | | |
| 14 | **Google "G" logo** | The official SVG from Google's sign-in branding guidelines | Their rules forbid redrawing it |
| **Content** (not graphics, but needed to build it) | | | |
| 15 | **Conditions d'utilisation** and **Politique de confidentialité** | Text, plus a public URL to host them | The checkbox needs them, and so do Google Cloud's consent screen and the Play Store |
| 16 | **List of « Type d'activité »** | e.g., alimentaire, boissons, cosmétiques, matériaux, pharmacie, autre | The owner's call |
| 17 | **Support contact** (WhatsApp, phone or email) | Text | For « Mot de passe oublié » and the « Support inclus » promise |

**For the illustrations:**

- **Licensing:** use sources whose license allows commercial use (unDraw is free with no attribution;
  Storyset's free tier requires attribution; IconScout and similar sell licenses). Keep the license proof.
- **AI-generated images:** generate all of them in one session with the same prompt style so they match.
- **Format:** deliver the originals at full size; they'll be converted to WebP and sized for the APK.

## Next steps

Once the assets are ready and the brand color and phone-login questions are answered, the build goes in
small steps:

1. Brand colors and heading font.
2. App icon and splash screen.
3. Welcome and value pages.
4. Sign-in and sign-up.
5. Success screen.

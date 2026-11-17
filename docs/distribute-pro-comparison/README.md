# Distribute Pro compared with DistriGo

Date: 14 November 2026

This report compares two Android apps for distributors:

- **Distribute Pro** (SAFE SOFT, version 07.11.25, package `com.safesoft.proapp.distribute`). It is the phone
  companion of a desktop management program; its version history names PME PRO and HYPER PRO.
- **DistriGo**, a standalone, offline-first app for a distributor: dépôt stock, tournées, sales, purchases,
  clients, suppliers, inventory, losses, returns and reports.

## The three parts

1. [Feature table](01-feature-table.md): what both have, what only Distribute Pro has, what only DistriGo has.
2. [Where Distribute Pro is weaker](02-weaknesses.md): UX, offline use, workflow.
3. [What DistriGo needs to replace it](03-replacement-needs.md), including sending sales to the desktop program.

## Summary

- Distribute Pro is a **satellite**: it does little on its own, but it exchanges data with the desktop program.
  It pulls clients, products and company settings, and pushes delivery notes and client payments.
- DistriGo is a **complete business app** that works with no PC, but it has no link to any desktop program yet.
- To replace Distribute Pro, DistriGo needs one thing first: a bridge to the desktop program. Colis entry on
  sale lines and a discount (remise) on the delivery note come next, if the owner wants them: of the 79 delivery
  notes later exported from the phone, only the most recent one uses them.

## How Distribute Pro was examined

The app was explored on a phone where it is installed and in daily use, with the owner's permission, as a normal
user would: screenshots, screen reading and taps over USB. The APK was not decompiled.

Screens visited: home, Clients (list, map, new-client form, client page, payment dialog), Vente (list, an existing
note, the new-sale form with its client picker, product picker, quantity dialog and remise dialog), Produits (list,
product page, stock report, new-product form, famille filter), Import/Export (read only), the exported-notes list,
the sales statistics screen, the version history and the serial-number dialog.

## Limits of this report

- **Paramètres was not explored.** It is protected by a password. What the report says about settings comes from
  the app's own version history.
- **Four modules were not seen.** Achat, Commande, Inventaire and Fournisseur are listed in the version history
  but hidden on this install.
- **No action button was pressed:** no sync, export, import, delete, update, print or PDF, and no sale was
  validated. The validation and payment dialog was therefore not seen.
- **One install only.** Other configurations of the app (more tariffs, TVA, other modules) may look different.
- DistriGo's side was taken from its source code, not from a walk of the app.

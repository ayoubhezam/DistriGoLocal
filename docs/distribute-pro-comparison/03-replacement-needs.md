# 3. What DistriGo needs to replace Distribute Pro

Part of [Distribute Pro compared with DistriGo](README.md).

The phone's whole purpose is to send its notes to the desktop program, so the bridge is required. Items 2 and 3
are in the app, but an export of the phone's 79 delivery notes (15 November 2026) shows colis entry and a remise
on only the most recent one; confirm with the owner before treating them as required.

## Required

1. **A bridge to the desktop program.**
   - Pull products (name, barcode, reference, price, colissage, famille, stock of the van's dépôt) and clients
     (code, name, phone, balance).
   - Push validated sales, client payments and new clients.
   - Mark what was sent, and never send the same document twice.
   - DistriGo's records already carry a unique id, a version and the device they came from, which is the
     groundwork for this.

2. **Colis entry on sale lines.** The seller types the number of colis, the quantity comes from the product's
   colissage, and both are printed on the ticket. DistriGo already stores units per colis on the product.

3. **Remise on the note**, as an amount or a percentage, carried through to the receipt, the client balance and
   the bridge.

## Needed for the bridge to work well

4. **External codes.** Keep the desktop program's client code and product reference on each DistriGo record, so
   the bridge can match records in both directions.

5. **The van as a dépôt.** Map DistriGo's van stock to the dépôt the desktop program uses for the phone (« VAN1 »
   on the phone examined), so stock loaded on the desktop arrives on the phone.

6. **Locks for sales reps.** A password on settings, and switches that block price changes and changes to a
   validated note.

7. **Purchases through the bridge**, if purchases made on the phone must also appear in the desktop program.

## To confirm before building

These exist in Distribute Pro, but nothing on the phone examined shows they are in use. Ask the owner first.

| Feature | What was seen |
|---|---|
| Several tariffs per client | Only Prix 1 is offered |
| TVA and HT prices | 0 % on the products seen |
| Free units (U.G) | The column exists; the one real note has none |
| Client fiscal fields (R.C, A.I, N.I.F, N.I.S) | In the new-client form; not checked on existing clients |
| Bons de commande | Module hidden on this install |

## What DistriGo already does better

A seller who moves to DistriGo also gains tournées with secteurs, van loading, returns, losses, expenses, six
reports, an Arabic interface and automatic backups. None of these needs the bridge. See
[the feature table](01-feature-table.md).

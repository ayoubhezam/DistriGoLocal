// What an attestation earns (docs/license_architecture.md §5.3). Decided here, on the server, so a false
// positive on some phone is fixed without an app update.

import type { Attestation } from "./attestation.ts";

export type Tier = "normal" | "reduced";

export type Verdict =
    | { accept: true; tier: Tier; reasons: string[] }
    | { accept: false; reason: string };

/**
 * Refused only on proof of foul play: a Google chain that does not hold, an attestation made for another
 * challenge, another app or another signer. Anything merely unproven — no attestation, an unknown root, an
 * unlocked or unverified phone, a revoked or expired attestation key — is accepted with a reduced offline
 * window (one day) and shows in the device's record, because refusing would strand a paying customer whose
 * phone is only old or odd.
 */
export function judge(attestation: Attestation): Verdict {
    if (attestation.keySpki === null) return { accept: false, reason: "unreadable_key" };
    if (attestation.brokenGoogleChain) return { accept: false, reason: "forged_chain" };
    if (!attestation.googleChain) return { accept: true, tier: "reduced", reasons: ["unknown_root"] };
    if (!attestation.attested) return { accept: true, tier: "reduced", reasons: ["not_attested"] };
    if (!attestation.challengeMatches) return { accept: false, reason: "stale_attestation" };
    if (attestation.packageMatches === false || attestation.signerMatches === false) {
        return { accept: false, reason: "repackaged" };
    }
    const reasons: string[] = [];
    if (attestation.packageMatches === null) reasons.push("no_application_id");
    if (attestation.revoked.length > 0) reasons.push("revoked_attestation_key");
    if (attestation.expired.length > 0) reasons.push("expired_certificate");
    if (attestation.securityLevel !== "tee" && attestation.securityLevel !== "strongbox") reasons.push("software_key");
    if (attestation.verifiedBootState !== "verified") reasons.push("unverified_boot");
    if (attestation.deviceLocked !== true) reasons.push("unlocked_bootloader");
    return { accept: true, tier: reasons.length > 0 ? "reduced" : "normal", reasons };
}

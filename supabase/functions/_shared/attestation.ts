// Android hardware key attestation (docs/license_architecture.md §5.1): what a device key's certificate chain
// proves about the key, the phone and the app that made it.
//
// Schema: https://source.android.com/docs/security/features/keystore/attestation#schema
// Roots and revocation list: https://developer.android.com/privacy-and-security/security-key-attestation

import { children, CONTEXT, type Der, DerError, enumerated, integer, octetString, readWhole, sequence, boolean } from "./der.ts";
import { base64Decode, keyHash } from "./jws.ts";
import { type Certificate, equalBytes, parseCertificate, signedBy } from "./x509.ts";

export const KEY_DESCRIPTION_OID = "1.3.6.1.4.1.11129.2.1.17";

/**
 * Google's attestation root keys, as SubjectPublicKeyInfo, from https://android.googleapis.com/attestation/root
 * (2026-10-10): the RSA-4096 key every earlier root shares, and the ECDSA P-384 "Key Attestation CA1" that
 * signs the chains of phones with remote key provisioning since 2026-04-10. A chain ending anywhere else
 * proves nothing about the hardware.
 */
export const GOOGLE_ROOT_KEYS: Uint8Array[] = [
    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdSSxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggjnar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGqC4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQoVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+OJtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/EgsTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRiigHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+MRPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9EaDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5UmAGMCAwEAAQ==",
    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEI9ojcU7fPlsFCjxy6IRqzgeOoK0b+YsV9FPQywiyw8EQRTkJ9u3qwfnI4DGoSLlBqClTXJfgfCcZvs60FikNMHnu4fkRzObfgDkU2KNXezT9/RQ+XvNslxPHrHCowhGr",
].map(base64Decode);

export type SecurityLevel = "software" | "tee" | "strongbox";
export type BootState = "verified" | "self_signed" | "unverified" | "failed";

/** What the caller expects the attestation to say. */
export interface Expected {
    /** The nonce the server gave for this activation. */
    challenge: Uint8Array;
    packageName: string;
    /** SHA-256 of the app's signing certificates, lowercase hex: release, and debug while developing. */
    signingDigests: string[];
    roots?: Uint8Array[];
    /** Serial (lowercase hex) → status, for certificates Google lists as revoked or suspended. */
    revoked?: Map<string, string>;
    now?: Date;
}

/** What a chain shows. Inspection never throws: what cannot be read is reported in [problems]. */
export interface Attestation {
    /** The device key: the first certificate's SubjectPublicKeyInfo, and its hash as licenses name it. */
    keySpki: Uint8Array | null;
    keyHash: string | null;
    /** The chain links up, certificate by certificate, to one of Google's roots. */
    googleChain: boolean;
    /** It ends at a Google root but a link does not verify: forged, not merely unattested. */
    brokenGoogleChain: boolean;
    /** The first certificate carries a KeyDescription. */
    attested: boolean;
    attestationVersion: number | null;
    securityLevel: SecurityLevel | null;
    challengeMatches: boolean;
    verifiedBootState: BootState | null;
    deviceLocked: boolean | null;
    osPatchLevel: number | null;
    packages: { name: string; version: number }[];
    signatureDigests: string[];
    /** null when the attestation names no application. */
    packageMatches: boolean | null;
    signerMatches: boolean | null;
    revoked: { serial: string; status: string }[];
    expired: string[];
    problems: string[];
}

const SECURITY_LEVELS: SecurityLevel[] = ["software", "tee", "strongbox"];
const BOOT_STATES: BootState[] = ["verified", "self_signed", "unverified", "failed"];
const ROOT_OF_TRUST = 704;
const OS_PATCH_LEVEL = 706;
const ATTESTATION_APPLICATION_ID = 709;

const hex = (bytes: Uint8Array) => [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");

export async function inspect(chainDer: Uint8Array[], expected: Expected): Promise<Attestation> {
    const result: Attestation = {
        keySpki: null, keyHash: null, googleChain: false, brokenGoogleChain: false, attested: false,
        attestationVersion: null, securityLevel: null, challengeMatches: false, verifiedBootState: null,
        deviceLocked: null, osPatchLevel: null, packages: [], signatureDigests: [], packageMatches: null,
        signerMatches: null, revoked: [], expired: [], problems: [],
    };
    let chain: Certificate[];
    try {
        chain = chainDer.map(parseCertificate);
    } catch (e) {
        result.problems.push(`unreadable certificate: ${(e as Error).message}`);
        return result;
    }
    if (chain.length === 0) {
        result.problems.push("empty chain");
        return result;
    }
    result.keySpki = chain[0].spki;
    result.keyHash = await keyHash(chain[0].spki);

    // The chain: each certificate signed by the next, the last by a pinned Google root key.
    const roots = expected.roots ?? GOOGLE_ROOT_KEYS;
    const last = chain[chain.length - 1];
    const endsAtGoogle = roots.some((root) => equalBytes(root, last.spki));
    let linked = true;
    for (let i = 0; i < chain.length - 1; i++) {
        if (!(await signedBy(chain[i], chain[i + 1].spki))) {
            linked = false;
            result.problems.push(`certificate ${i} is not signed by certificate ${i + 1}`);
        }
    }
    if (endsAtGoogle && !(await signedBy(last, last.spki))) {
        linked = false;
        result.problems.push("the root does not sign itself");
    }
    result.googleChain = endsAtGoogle && linked;
    result.brokenGoogleChain = endsAtGoogle && !linked;
    if (!endsAtGoogle) result.problems.push("the chain does not end at a Google root");

    // Revocation and validity. The key's own certificate is left out of the dates: Keystore fills them as it likes.
    const now = expected.now ?? new Date();
    for (const [index, certificate] of chain.entries()) {
        const status = expected.revoked?.get(certificate.serialHex);
        if (status) result.revoked.push({ serial: certificate.serialHex, status });
        if (index > 0 && (now < certificate.notBefore || now > certificate.notAfter)) result.expired.push(certificate.serialHex);
    }

    const description = chain[0].extensions.get(KEY_DESCRIPTION_OID);
    if (!description) {
        result.problems.push("no KeyDescription: the key is not attested");
        return result;
    }
    try {
        readKeyDescription(description, expected, result);
        result.attested = true;
    } catch (e) {
        result.problems.push(`unreadable KeyDescription: ${(e as Error).message}`);
    }
    return result;
}

function readKeyDescription(bytes: Uint8Array, expected: Expected, result: Attestation) {
    const fields = children(sequence(readWhole(bytes), "KeyDescription"));
    if (fields.length < 8) throw new DerError("KeyDescription too short");
    result.attestationVersion = integer(fields[0]);
    result.securityLevel = SECURITY_LEVELS[enumerated(fields[1])] ?? null;
    result.challengeMatches = equalBytes(octetString(fields[4]), expected.challenge);
    const softwareEnforced = authorizations(fields[6]);
    const hardwareEnforced = authorizations(fields[7]);

    // The root of trust means something only when the secure hardware vouches for it.
    const rootOfTrust = hardwareEnforced.get(ROOT_OF_TRUST);
    if (rootOfTrust) {
        const parts = children(sequence(children(rootOfTrust)[0], "RootOfTrust"));
        result.deviceLocked = boolean(parts[1]);
        result.verifiedBootState = BOOT_STATES[enumerated(parts[2])] ?? null;
    }
    const patch = hardwareEnforced.get(OS_PATCH_LEVEL);
    if (patch) result.osPatchLevel = integer(children(patch)[0]);

    const applicationId = softwareEnforced.get(ATTESTATION_APPLICATION_ID) ?? hardwareEnforced.get(ATTESTATION_APPLICATION_ID);
    if (applicationId) {
        const [packageInfos, digests] = children(sequence(readWhole(octetString(children(applicationId)[0])), "AttestationApplicationId"));
        for (const info of children(packageInfos)) {
            const [name, version] = children(sequence(info, "AttestationPackageInfo"));
            result.packages.push({ name: new TextDecoder().decode(octetString(name)), version: integer(version) });
        }
        result.signatureDigests = children(digests).map((d) => hex(octetString(d)));
        result.packageMatches = result.packages.some((p) => p.name === expected.packageName);
        const allowed = expected.signingDigests.map((d) => d.toLowerCase().replace(/[^0-9a-f]/g, ""));
        // Any of them: after a signing-key rotation the lineage lists the old certificate too, which only the
        // holder of our key could have produced.
        result.signerMatches = result.signatureDigests.some((d) => allowed.includes(d));
    }
}

/** An AuthorizationList: its explicitly tagged fields by tag number. Tags this code does not know are kept, unread. */
function authorizations(list: Der): Map<number, Der> {
    const out = new Map<number, Der>();
    for (const field of children(sequence(list, "AuthorizationList"))) {
        if (field.cls === CONTEXT) out.set(field.tag, field);
    }
    return out;
}

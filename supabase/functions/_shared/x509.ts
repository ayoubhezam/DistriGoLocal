// X.509 certificates, as far as checking an attestation chain needs: what is signed, by which algorithm, the
// subject's key, the validity and the extensions. Signatures are checked with WebCrypto.

import {
    bitString, boolean, children, CONTEXT, DerError, integerHex, octetString, oid, readWhole, sequence, time,
    UNIVERSAL,
} from "./der.ts";

export interface Certificate {
    der: Uint8Array;
    /** The TBSCertificate: the bytes the issuer signed. */
    tbs: Uint8Array;
    serialHex: string;
    signatureAlgorithm: string;
    signature: Uint8Array;
    notBefore: Date;
    notAfter: Date;
    /** SubjectPublicKeyInfo, DER: what Java's PublicKey.encoded returns. */
    spki: Uint8Array;
    /** Extension OID → the content of its extnValue OCTET STRING. */
    extensions: Map<string, Uint8Array>;
}

export function parseCertificate(der: Uint8Array): Certificate {
    const [tbsElement, algorithm, signature] = children(sequence(readWhole(der), "Certificate"));
    const tbsParts = children(sequence(tbsElement, "TBSCertificate"));
    let i = 0;
    if (tbsParts[i]?.cls === CONTEXT && tbsParts[i].tag === 0) i++; // version
    const serial = tbsParts[i++];
    i++; // signature algorithm, repeated
    i++; // issuer
    const [notBefore, notAfter] = children(sequence(tbsParts[i++], "Validity"));
    i++; // subject
    const spki = sequence(tbsParts[i++], "SubjectPublicKeyInfo");
    const extensions = new Map<string, Uint8Array>();
    for (; i < tbsParts.length; i++) {
        const part = tbsParts[i];
        if (part.cls !== CONTEXT || part.tag !== 3) continue; // issuer and subject unique ids
        for (const extension of children(sequence(children(part)[0], "Extensions"))) {
            const fields = children(sequence(extension, "Extension"));
            const value = fields[fields.length - 1];
            if (fields.length === 3) boolean(fields[1]); // critical
            extensions.set(oid(fields[0]), octetString(value));
        }
    }
    return {
        der,
        tbs: tbsElement.bytes,
        serialHex: integerHex(serial),
        signatureAlgorithm: oid(children(sequence(algorithm, "AlgorithmIdentifier"))[0]),
        signature: bitString(signature),
        notBefore: time(notBefore),
        notAfter: time(notAfter),
        spki: spki.bytes,
        extensions,
    };
}

const EC_PUBLIC_KEY = "1.2.840.10045.2.1";
const RSA_ENCRYPTION = "1.2.840.113549.1.1.1";

const CURVES: Record<string, { name: string; size: number }> = {
    "1.2.840.10045.3.1.7": { name: "P-256", size: 32 },
    "1.3.132.0.34": { name: "P-384", size: 48 },
    "1.3.132.0.35": { name: "P-521", size: 66 },
};

const SIGNATURES: Record<string, { kind: "ECDSA" | "RSA"; hash: string }> = {
    "1.2.840.10045.4.3.2": { kind: "ECDSA", hash: "SHA-256" },
    "1.2.840.10045.4.3.3": { kind: "ECDSA", hash: "SHA-384" },
    "1.2.840.10045.4.3.4": { kind: "ECDSA", hash: "SHA-512" },
    "1.2.840.113549.1.1.11": { kind: "RSA", hash: "SHA-256" },
    "1.2.840.113549.1.1.12": { kind: "RSA", hash: "SHA-384" },
    "1.2.840.113549.1.1.13": { kind: "RSA", hash: "SHA-512" },
};

/** The key type of a SubjectPublicKeyInfo: an EC curve, or RSA. */
export function keyType(spki: Uint8Array): { kind: "EC"; curve: string; size: number } | { kind: "RSA" } {
    const [algorithm] = children(sequence(readWhole(spki)));
    const [id, parameters] = children(sequence(algorithm));
    const type = oid(id);
    if (type === RSA_ENCRYPTION) return { kind: "RSA" };
    if (type === EC_PUBLIC_KEY) {
        const curve = CURVES[oid(parameters)];
        if (!curve) throw new DerError("unsupported curve");
        return { kind: "EC", curve: curve.name, size: curve.size };
    }
    throw new DerError(`unsupported key ${type}`);
}

/** An ECDSA signature from DER (what certificates and Java write) to R‖S (what WebCrypto reads). */
export function derToP1363(signature: Uint8Array, size: number): Uint8Array {
    const [r, s] = children(sequence(readWhole(signature), "ECDSA signature"));
    const out = new Uint8Array(size * 2);
    for (const [index, part] of [r, s].entries()) {
        if (part.cls !== UNIVERSAL || part.tag !== 0x02) throw new DerError("expected INTEGER");
        let value = part.content;
        while (value.length > 0 && value[0] === 0) value = value.subarray(1);
        if (value.length > size) throw new DerError("ECDSA integer too large");
        out.set(value, index * size + size - value.length);
    }
    return out;
}

/** Whether [signature] over [data] verifies with the key [spki], for the X.509 algorithm OID [algorithm]. */
export async function verifySignature(
    spki: Uint8Array,
    algorithm: string,
    data: Uint8Array,
    signature: Uint8Array,
): Promise<boolean> {
    const scheme = SIGNATURES[algorithm];
    if (!scheme) return false;
    try {
        const type = keyType(spki);
        if (scheme.kind === "ECDSA" && type.kind === "EC") {
            const key = await crypto.subtle.importKey("spki", owned(spki), { name: "ECDSA", namedCurve: type.curve }, false, ["verify"]);
            return await crypto.subtle.verify({ name: "ECDSA", hash: scheme.hash }, key, owned(derToP1363(signature, type.size)), owned(data));
        }
        if (scheme.kind === "RSA" && type.kind === "RSA") {
            const key = await crypto.subtle.importKey("spki", owned(spki), { name: "RSASSA-PKCS1-v1_5", hash: scheme.hash }, false, ["verify"]);
            return await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, owned(signature), owned(data));
        }
        return false;
    } catch (_) {
        // A malformed key or signature is a signature that does not verify.
        return false;
    }
}

/** Whether [certificate] was signed by the key [issuerSpki]. */
export function signedBy(certificate: Certificate, issuerSpki: Uint8Array): Promise<boolean> {
    return verifySignature(issuerSpki, certificate.signatureAlgorithm, certificate.tbs, certificate.signature);
}

/** A copy WebCrypto's types accept: ours are often views into a larger buffer. */
export const owned = (bytes: Uint8Array): Uint8Array<ArrayBuffer> => new Uint8Array(bytes);

export function equalBytes(a: Uint8Array, b: Uint8Array): boolean {
    if (a.length !== b.length) return false;
    let difference = 0;
    for (let i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
    return difference === 0;
}

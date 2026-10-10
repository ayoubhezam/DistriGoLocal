// The license as the phone verifies it (app: data/license/LicenseVerifier.kt, docs §4.1): a compact JWS,
// ES256, its signature as R‖S — which is what WebCrypto's ECDSA produces.

import { derToP1363, keyType, owned, verifySignature } from "./x509.ts";

export const LICENSE_TYPE = "distrigo-license+jwt";

export function base64UrlEncode(bytes: Uint8Array): string {
    let binary = "";
    for (const b of bytes) binary += String.fromCharCode(b);
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Standard or URL base64, padded or not. Throws on anything else. */
export function base64Decode(text: string): Uint8Array<ArrayBuffer> {
    let normal = text.replace(/\s+/g, "").replace(/-/g, "+").replace(/_/g, "/");
    if (!/^[A-Za-z0-9+/]*={0,2}$/.test(normal)) throw new Error("not base64");
    normal = normal.replace(/=+$/, "");
    if (normal.length % 4 === 1) throw new Error("not base64");
    normal += "=".repeat((4 - (normal.length % 4)) % 4);
    return Uint8Array.from(atob(normal), (c) => c.charCodeAt(0));
}

const utf8 = (text: string) => new TextEncoder().encode(text);

/** The private key a license is signed with: PKCS#8, PEM or base64. */
export function importSigningKey(pkcs8: string): Promise<CryptoKey> {
    const body = pkcs8.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "");
    return crypto.subtle.importKey("pkcs8", base64Decode(body), { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
}

/** A license: [claims] signed with [key], whose public half the app pins as [keyId]. */
export async function signLicense(claims: Record<string, unknown>, key: CryptoKey, keyId: string): Promise<string> {
    const header = { alg: "ES256", kid: keyId, typ: LICENSE_TYPE };
    const input = `${base64UrlEncode(utf8(JSON.stringify(header)))}.${base64UrlEncode(utf8(JSON.stringify(claims)))}`;
    const signature = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, utf8(input)));
    return `${input}.${base64UrlEncode(signature)}`;
}

/** How a license names a device key: base64url SHA-256 of its SubjectPublicKeyInfo (LicenseCrypto.keyHash). */
export async function keyHash(spki: Uint8Array): Promise<string> {
    return base64UrlEncode(new Uint8Array(await crypto.subtle.digest("SHA-256", owned(spki))));
}

/** Whether the device key [spki] signed [message]: SHA256withECDSA, DER, as DeviceKey.sign writes it. */
export async function verifyDeviceSignature(spki: Uint8Array, message: Uint8Array, signature: Uint8Array): Promise<boolean> {
    try {
        const type = keyType(spki);
        if (type.kind !== "EC" || type.curve !== "P-256") return false;
        derToP1363(signature, type.size); // malformed DER fails here rather than verifying as false later
    } catch (_) {
        return false;
    }
    return verifySignature(spki, "1.2.840.10045.4.3.2", message, signature);
}

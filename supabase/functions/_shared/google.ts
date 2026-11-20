// Google, for Play Billing (docs/play_billing.md): the Play Developer API as a service account, and the check of the
// token Google's Pub/Sub signs each push with. WebCrypto only, as the rest of _shared: it runs in Deno and in Node.

import { base64Decode, base64UrlEncode } from "./jws.ts";

const utf8 = (text: string) => new TextEncoder().encode(text);
const fromUtf8 = (bytes: Uint8Array) => new TextDecoder().decode(bytes);
const json64 = (value: unknown) => base64UrlEncode(utf8(JSON.stringify(value)));

/** A subscription purchase as Play Developer API subscriptionsv2.get describes it, reduced to what we use. */
export interface PlaySubscription {
    /** subscriptionState: SUBSCRIPTION_STATE_ACTIVE, _CANCELED, _IN_GRACE_PERIOD, _ON_HOLD, _PAUSED, _EXPIRED, … */
    state: string;
    productId: string;
    basePlanId: string;
    /** The end of the paid time; null when Google gives none (a pending purchase). */
    expiresAt: Date | null;
    linkedPurchaseToken: string | null;
    latestOrderId: string | null;
    acknowledged: boolean;
    /** obfuscatedExternalAccountId: what the app set at purchase, the business's hashed id. */
    accountId: string | null;
}

export interface PlayApi {
    /** The purchase behind [token], or null when Google knows none. */
    subscription(token: string): Promise<PlaySubscription | null>;
    /** Tells Google the purchase was granted: one not acknowledged within 3 days is refunded. */
    acknowledge(productId: string, token: string): Promise<void>;
}

/** subscriptionsv2.get's answer, as PlaySubscription. Exported for the tests. */
export function parseSubscription(body: Record<string, unknown>): PlaySubscription {
    const items = Array.isArray(body.lineItems) ? body.lineItems as Record<string, unknown>[] : [];
    // One line item per subscription today; the latest expiry if Google ever sends more
    const item = items.reduce<Record<string, unknown> | null>((best, it) =>
        best === null || String(it.expiryTime ?? "") > String(best.expiryTime ?? "") ? it : best, null) ?? {};
    const offer = (item.offerDetails ?? {}) as Record<string, unknown>;
    const ids = (body.externalAccountIdentifiers ?? {}) as Record<string, unknown>;
    const expiry = typeof item.expiryTime === "string" ? new Date(item.expiryTime) : null;
    return {
        state: String(body.subscriptionState ?? "SUBSCRIPTION_STATE_UNSPECIFIED"),
        productId: String(item.productId ?? ""),
        basePlanId: String(offer.basePlanId ?? ""),
        expiresAt: expiry && !Number.isNaN(expiry.getTime()) ? expiry : null,
        linkedPurchaseToken: typeof body.linkedPurchaseToken === "string" ? body.linkedPurchaseToken : null,
        latestOrderId: typeof body.latestOrderId === "string" ? body.latestOrderId : null,
        acknowledged: body.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
        accountId: typeof ids.obfuscatedExternalAccountId === "string" ? ids.obfuscatedExternalAccountId : null,
    };
}

interface ServiceAccount {
    client_email: string;
    private_key: string;
    token_uri?: string;
}

const SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";

/** The service account's signed request for an access token (RFC 7523). Exported for the tests. */
export async function serviceAccountAssertion(account: ServiceAccount, nowSeconds: number): Promise<string> {
    const key = await crypto.subtle.importKey(
        "pkcs8",
        base64Decode(account.private_key.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "")),
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
        false,
        ["sign"],
    );
    const head = json64({ alg: "RS256", typ: "JWT" });
    const claims = json64({
        iss: account.client_email,
        scope: SCOPE,
        aud: account.token_uri ?? "https://oauth2.googleapis.com/token",
        iat: nowSeconds,
        exp: nowSeconds + 3600,
    });
    const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, utf8(`${head}.${claims}`));
    return `${head}.${claims}.${base64UrlEncode(new Uint8Array(signature))}`;
}

/**
 * The Play Developer API for [packageName], as the service account [accountJson] (the key file from Google Cloud,
 * kept in the secret PLAY_SERVICE_ACCOUNT_JSON). The access token is kept until a minute before it ends.
 */
export function googlePlayApi(
    accountJson: string,
    packageName: string,
    fetcher: typeof fetch = fetch,
    now: () => Date = () => new Date(),
): PlayApi {
    const account = JSON.parse(accountJson) as ServiceAccount;
    if (!account.client_email || !account.private_key) throw new Error("PLAY_SERVICE_ACCOUNT_JSON is not a service account key");
    let token: { value: string; until: number } | null = null;

    async function accessToken(): Promise<string> {
        const seconds = Math.floor(now().getTime() / 1000);
        if (token && token.until > seconds + 60) return token.value;
        const response = await fetcher(account.token_uri ?? "https://oauth2.googleapis.com/token", {
            method: "POST",
            headers: { "content-type": "application/x-www-form-urlencoded" },
            body: new URLSearchParams({
                grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
                assertion: await serviceAccountAssertion(account, seconds),
            }),
        });
        if (!response.ok) throw new Error(`Google refused the service account: ${response.status}`);
        const body = await response.json() as { access_token: string; expires_in: number };
        token = { value: body.access_token, until: seconds + body.expires_in };
        return token.value;
    }

    const pkg = encodeURIComponent(packageName);
    return {
        async subscription(purchaseToken) {
            const response = await fetcher(`${API}/${pkg}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`, {
                headers: { authorization: `Bearer ${await accessToken()}` },
            });
            if (response.status === 404 || response.status === 410) return null;
            if (!response.ok) throw new Error(`subscriptionsv2.get: ${response.status}`);
            return parseSubscription(await response.json());
        },
        async acknowledge(productId, purchaseToken) {
            const response = await fetcher(
                `${API}/${pkg}/purchases/subscriptions/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`,
                { method: "POST", headers: { authorization: `Bearer ${await accessToken()}`, "content-type": "application/json" }, body: "{}" },
            );
            // 400 with « already acknowledged » is not a failure
            if (!response.ok && response.status !== 400) throw new Error(`acknowledge: ${response.status}`);
        },
    };
}

/** What a Pub/Sub push must carry: its OIDC token's audience, and the service account it is pushed as. */
export interface PushIdentity {
    audience: string;
    email: string;
}

type Jwk = JsonWebKey & { kid?: string };

/** Google's current signing keys for its OIDC tokens. */
export async function googleCerts(fetcher: typeof fetch = fetch): Promise<Jwk[]> {
    const response = await fetcher("https://www.googleapis.com/oauth2/v3/certs");
    if (!response.ok) throw new Error(`Google certs: ${response.status}`);
    return ((await response.json()) as { keys: Jwk[] }).keys;
}

/**
 * True when [authorization] is « Bearer <a token Google signed> » for [identity]: RS256 under one of [keys], issued by
 * accounts.google.com, for the expected audience and service account, verified, and current at [now].
 */
export async function verifyPushToken(
    authorization: string | null,
    identity: PushIdentity,
    keys: Jwk[],
    now: Date = new Date(),
): Promise<boolean> {
    if (!authorization?.startsWith("Bearer ")) return false;
    const parts = authorization.slice(7).trim().split(".");
    if (parts.length !== 3) return false;
    try {
        const header = JSON.parse(fromUtf8(base64Decode(parts[0])));
        const claims = JSON.parse(fromUtf8(base64Decode(parts[1])));
        if (header.alg !== "RS256") return false;
        const jwk = keys.find((k) => k.kid === header.kid);
        if (!jwk) return false;
        const key = await crypto.subtle.importKey("jwk", { kty: jwk.kty, n: jwk.n, e: jwk.e }, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
        const signed = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, base64Decode(parts[2]), utf8(`${parts[0]}.${parts[1]}`));
        if (!signed) return false;
        const seconds = Math.floor(now.getTime() / 1000);
        return ["accounts.google.com", "https://accounts.google.com"].includes(claims.iss)
            && claims.aud === identity.audience
            && claims.email === identity.email
            && claims.email_verified === true
            && typeof claims.exp === "number" && claims.exp > seconds - 60
            && (typeof claims.iat !== "number" || claims.iat < seconds + 300);
    } catch (_) {
        return false;
    }
}

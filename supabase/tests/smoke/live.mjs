// A smoke test of the deployed functions, after `supabase functions deploy`:
//
//   npx supabase@2.120.0 projects api-keys --project-ref <ref> -o json | node supabase/tests/smoke/live.mjs <ref>
//
// It plays a phone that cannot attest (reduced tier): a throwaway account, a throwaway P-256 key in a self-signed
// certificate. It activates, checks the license against the public key pinned in the app (LicenseKeys.kt), checks
// in, and deletes the account, which takes its business and device with it. It leaves a trial claim for its own
// made-up ANDROID_ID, by design. The project's keys arrive on stdin and are never printed.

import { createHash, createPublicKey, generateKeyPairSync, randomBytes, randomUUID, sign, verify, X509Certificate } from "node:crypto";
import { readFileSync } from "node:fs";

const ref = process.argv[2];
if (!/^[a-z0-9]{20}$/.test(ref ?? "")) throw new Error("usage: … | node live.mjs <project-ref>");
const base = `https://${ref}.supabase.co`;

const apiKeys = JSON.parse(readFileSync(0, "utf8"));
const list = Array.isArray(apiKeys) ? apiKeys : apiKeys.keys ?? apiKeys.api_keys ?? [];
const pick = (...names) => list.find((k) => names.includes(k.name) || names.includes(k.type))?.api_key;
const anonKey = pick("anon", "publishable");
const serviceKey = pick("service_role", "secret");
if (!anonKey || !serviceKey) throw new Error("the project's anon and service keys were not on stdin");

// The license key the app pins, read from the app's source.
const pinned = /"lic-2026-a" to "([A-Za-z0-9+/=]+)"/.exec(
    readFileSync(new URL("../../../app/src/main/java/com/distrigo/app/data/license/LicenseKeys.kt", import.meta.url), "utf8"),
)[1];
const licenseKey = createPublicKey({ key: Buffer.from(pinned, "base64"), format: "der", type: "spki" });

let failures = 0;
const check = (label, ok, detail = "") => {
    console.log(`${ok ? "ok  " : "FAIL"} ${label}${ok || !detail ? "" : `: ${detail}`}`);
    if (!ok) failures++;
};

async function call(path, { token, body, key = anonKey, method = "POST" } = {}) {
    const response = await fetch(`${base}${path}`, {
        method,
        headers: {
            apikey: key,
            ...(token ? { authorization: `Bearer ${token}` } : {}),
            ...(body ? { "content-type": "application/json" } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
    });
    const text = await response.text();
    let json = null;
    try {
        json = JSON.parse(text);
    } catch (_) { /* not JSON */ }
    return { status: response.status, json, text };
}

const fn = (name, token, body = {}) => call(`/functions/v1/${name}`, { token, body });

/** The claims of a license whose signature verifies with the pinned lic-2026-a, or null. */
function verifiedClaims(token) {
    const [header, payload, signature] = token.split(".");
    const ok = verify("sha256", Buffer.from(`${header}.${payload}`), { key: licenseKey, dsaEncoding: "ieee-p1363" }, Buffer.from(signature, "base64url"));
    return ok ? { header: JSON.parse(Buffer.from(header, "base64url")), claims: JSON.parse(Buffer.from(payload, "base64url")) } : null;
}

// ── A phone without attestation: a P-256 key in a self-signed certificate ──
const der = (tag, ...parts) => {
    const body = Buffer.concat(parts.map((p) => Buffer.from(p)));
    const n = body.length;
    const length = n < 128 ? [n] : n < 256 ? [0x81, n] : [0x82, n >> 8, n & 0xff];
    return Buffer.concat([Buffer.from([tag, ...length]), body]);
};
const ECDSA_SHA256 = der(0x30, der(0x06, [0x2a, 0x86, 0x48, 0xce, 0x3d, 0x04, 0x03, 0x02]));
const name = der(0x30, der(0x31, der(0x30, der(0x06, [0x55, 0x04, 0x03]), der(0x0c, Buffer.from("DistriGo smoke test")))));
const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });
const spki = publicKey.export({ type: "spki", format: "der" });
const tbs = der(0x30,
    der(0xa0, der(0x02, [2])), der(0x02, [1]), ECDSA_SHA256, name,
    der(0x30, der(0x17, Buffer.from("261001000000Z")), der(0x17, Buffer.from("361001000000Z"))),
    name, spki);
const certificate = der(0x30, tbs, ECDSA_SHA256, der(0x03, Buffer.concat([Buffer.from([0]), sign("sha256", tbs, privateKey)])));
new X509Certificate(certificate); // throws if the certificate is malformed
const keyHash = createHash("sha256").update(spki).digest("base64url");
const installationId = randomUUID();
const signed = (purpose, nonce) =>
    sign("sha256", Buffer.from(`distrigo-${purpose}\n${nonce}\n${installationId}`), privateKey).toString("base64");

// ── A throwaway account ──
const email = `smoke-${randomUUID()}@distrigo.invalid`;
const password = randomBytes(24).toString("base64url");
const created = await call("/auth/v1/admin/users", { key: serviceKey, token: serviceKey, body: { email, password, email_confirm: true } });
check("a throwaway account is created", created.status === 200 && created.json?.id, created.text);
const userId = created.json?.id;
const session = await call("/auth/v1/token?grant_type=password", { body: { email, password } });
const token = session.json?.access_token;
check("it signs in", Boolean(token), session.text);

try {
    const anonymous = await fn("license-nonce", null, { purpose: "activate" });
    check("without a session: 401", anonymous.status === 401 && anonymous.json?.status === "unauthenticated", anonymous.text);
    const malformed = await fn("license-nonce", token, { purpose: "steal" });
    check("a malformed request: 400 naming the field", malformed.status === 400 && malformed.json?.field === "purpose", malformed.text);

    const nonce = (await fn("license-nonce", token, { purpose: "activate" })).json?.nonce;
    check("a nonce: 32 bytes", Buffer.from(nonce ?? "", "base64url").length === 32);
    const activation = {
        nonce, installation_id: installationId, certificate_chain: [certificate.toString("base64")],
        signature: signed("activate", nonce), android_id: `smoke-${randomUUID()}`, model: "Node", os_version: "0", app_version: "smoke",
    };
    const noBusiness = await fn("license-activate", token, activation);
    check("an account without a business is asked its name", noBusiness.json?.status === "no_business", noBusiness.text);
    const activated = await fn("license-activate", token, { ...activation, business_name: "Smoke test" });
    check("with its name: activated, at the reduced tier of an unattested phone",
        activated.json?.status === "activated" && activated.json?.tier === "reduced" && activated.json?.trial === true, activated.text);

    const first = activated.json?.license ? verifiedClaims(activated.json.license) : null;
    check("the license verifies with lic-2026-a, the key pinned in the app", first !== null && first.header.kid === "lic-2026-a");
    if (first) {
        const c = first.claims;
        check("its claims name this phone, the trial and a one-day window",
            c.dev === keyHash && c.iid === installationId && c.sub === userId && c.plan === "trial" && c.seq === 1 && c.ou - c.iat === 86400,
            JSON.stringify(c));
    }

    const refreshNonce = (await fn("license-nonce", token, { purpose: "refresh" })).json?.nonce;
    const checkIn = { nonce: refreshNonce, installation_id: installationId, signature: signed("refresh", refreshNonce), app_version: "smoke" };
    const refreshed = await fn("license-refresh", token, checkIn);
    const second = refreshed.json?.license ? verifiedClaims(refreshed.json.license) : null;
    check("a check-in gets the next license", refreshed.json?.status === "ok" && second?.claims.seq === 2, refreshed.text);
    const replay = await fn("license-refresh", token, checkIn);
    check("the same check-in again is a replay", replay.json?.status === "bad_nonce", replay.text);

    // ── What the app itself can read through the API (row-level security, as deployed) ──
    const rest = (path, headers = {}, key = anonKey, bearer = token) =>
        fetch(`${base}/rest/v1/${path}`, { headers: { apikey: key, ...(bearer ? { authorization: `Bearer ${bearer}` } : {}), ...headers } })
            .then(async (r) => ({ status: r.status, json: await r.json().catch(() => null) }));
    const businesses = await rest("businesses?select=name");
    check("the account reads its own business, and only it", businesses.status === 200 && businesses.json?.length === 1 && businesses.json[0].name === "Smoke test", JSON.stringify(businesses));
    const plans = await rest("plans?select=id");
    check("it reads the plans", plans.status === 200 && plans.json?.length === 2, JSON.stringify(plans));
    const devices = await rest("devices?select=id,model,status");
    check("it reads its phone's public columns", devices.status === 200 && devices.json?.length === 1, JSON.stringify(devices));
    const key = await rest("devices?select=key_spki");
    check("not the device key", key.status >= 400, JSON.stringify(key));
    const extend = await fetch(`${base}/rest/v1/subscriptions?business_id=not.is.null`, {
        method: "PATCH",
        headers: { apikey: anonKey, authorization: `Bearer ${token}`, "content-type": "application/json", prefer: "return=representation" },
        body: JSON.stringify({ valid_to: "2099-12-31" }),
    });
    check("nor extend its own subscription", extend.status >= 400, `${extend.status}`);
    const hidden = await rest("nonces?select=value", { "accept-profile": "private" });
    check("the private schema is not reachable", hidden.status >= 400, JSON.stringify(hidden));
    const anonymousPlans = await rest("plans?select=id", {}, anonKey, null);
    check("without an account, not even the plans", anonymousPlans.status >= 400, JSON.stringify(anonymousPlans));
} finally {
    const deleted = await fn("account-delete", token, {});
    check("account-delete removes the account and its business", deleted.json?.status === "deleted", deleted.text);
    const gone = await call(`/auth/v1/admin/users/${userId}`, { key: serviceKey, token: serviceKey, method: "GET" });
    check("the auth user is gone", gone.status === 404, `${gone.status}`);
}

console.log(failures === 0 ? "live smoke test passed" : `${failures} check(s) failed`);
process.exit(failures === 0 ? 0 : 1);

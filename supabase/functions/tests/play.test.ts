// Google Play Billing (point 5): the Google client (service account, Pub/Sub tokens, subscriptionsv2's answer) on its
// own, then play-verify and play-rtdn end to end against the migration, with a fake Google. run.sh sets
// DISTRIGO_TEST_DB_URL.
import assert from "node:assert/strict";
import { generateKeyPairSync, sign, verify } from "node:crypto";
import { test } from "node:test";
import postgres from "postgres";
import {
    googlePlayApi, parseSubscription, type PlayApi, type PlaySubscription, serviceAccountAssertion, verifyPushToken,
} from "../_shared/google.ts";
import { base64UrlEncode } from "../_shared/jws.ts";
import { playNotification, playVerify } from "../_shared/play.ts";

const url = process.env.DISTRIGO_TEST_DB_URL;
const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const pem = privateKey.export({ type: "pkcs8", format: "pem" }).toString();
const b64json = (value: unknown) => base64UrlEncode(new TextEncoder().encode(JSON.stringify(value)));

// ── The Google client ──

test("subscriptionsv2's answer reads as state, product, base plan, expiry, order, acknowledgement and account", () => {
    const sub = parseSubscription({
        subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
        latestOrderId: "GPA.1111-2222-3333-44444",
        linkedPurchaseToken: "old-token",
        acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
        externalAccountIdentifiers: { obfuscatedExternalAccountId: "abc123" },
        lineItems: [
            { productId: "distroniq_solo", expiryTime: "2026-12-20T10:00:00.000Z", offerDetails: { basePlanId: "solo-monthly" } },
        ],
    });
    assert.deepEqual(sub, {
        state: "SUBSCRIPTION_STATE_ACTIVE", productId: "distroniq_solo", basePlanId: "solo-monthly",
        expiresAt: new Date("2026-12-20T10:00:00.000Z"), linkedPurchaseToken: "old-token",
        latestOrderId: "GPA.1111-2222-3333-44444", acknowledged: false, accountId: "abc123",
    });
    assert.equal(parseSubscription({ acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" }).acknowledged, true);
    assert.equal(parseSubscription({}).expiresAt, null);
});

test("the service account asks for a token with an RS256 assertion Google can check", async () => {
    const assertion = await serviceAccountAssertion({ client_email: "play@project.iam.gserviceaccount.com", private_key: pem }, 1_800_000_000);
    const [head, claims, signature] = assertion.split(".");
    assert.ok(verify("sha256", Buffer.from(`${head}.${claims}`), publicKey, Buffer.from(signature, "base64url")));
    const body = JSON.parse(Buffer.from(claims, "base64url").toString());
    assert.equal(body.iss, "play@project.iam.gserviceaccount.com");
    assert.equal(body.scope, "https://www.googleapis.com/auth/androidpublisher");
    assert.equal(body.aud, "https://oauth2.googleapis.com/token");
    assert.equal(body.exp - body.iat, 3600);
});

test("the Play API gets one access token, reads a purchase, knows a missing one, and acknowledges", async () => {
    const calls: { url: string; init?: RequestInit }[] = [];
    const fake = (async (input: string | URL | Request, init?: RequestInit) => {
        const url = String(input);
        calls.push({ url, init });
        if (url.includes("oauth2")) return new Response(JSON.stringify({ access_token: "at-1", expires_in: 3600 }));
        if (url.endsWith("/tokens/missing")) return new Response("{}", { status: 404 });
        if (url.includes("subscriptionsv2")) return new Response(JSON.stringify({ subscriptionState: "SUBSCRIPTION_STATE_ACTIVE" }));
        return new Response("{}");
    }) as typeof fetch;
    const api = googlePlayApi(JSON.stringify({ client_email: "play@x.iam.gserviceaccount.com", private_key: pem }), "com.distrigo.app", fake);
    assert.equal((await api.subscription("tok/1"))?.state, "SUBSCRIPTION_STATE_ACTIVE");
    assert.equal(await api.subscription("missing"), null);
    await api.acknowledge("distroniq_solo", "tok/1");
    assert.equal(calls.filter((c) => c.url.includes("oauth2")).length, 1, "the access token is reused");
    const read = calls.find((c) => c.url.includes("subscriptionsv2"))!;
    assert.equal(read.url, "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.distrigo.app/purchases/subscriptionsv2/tokens/tok%2F1");
    assert.equal((read.init?.headers as Record<string, string>).authorization, "Bearer at-1");
    assert.ok(calls.some((c) => c.url.endsWith("/purchases/subscriptions/distroniq_solo/tokens/tok%2F1:acknowledge") && c.init?.method === "POST"));
});

test("a Pub/Sub push is accepted only with Google's signed token for our audience and service account", async () => {
    const jwk = { ...publicKey.export({ format: "jwk" }), kid: "k1" } as JsonWebKey & { kid: string };
    const identity = { audience: "https://x.supabase.co/functions/v1/play-rtdn", email: "pubsub@p.iam.gserviceaccount.com" };
    const now = new Date("2026-11-20T10:00:00Z");
    const seconds = Math.floor(now.getTime() / 1000);
    const token = (claims: Record<string, unknown>, kid = "k1") => {
        const signing = `${b64json({ alg: "RS256", kid })}.${b64json(claims)}`;
        const signature = sign("sha256", Buffer.from(signing), privateKey).toString("base64url");
        return `Bearer ${signing}.${signature}`;
    };
    const good = { iss: "https://accounts.google.com", aud: identity.audience, email: identity.email, email_verified: true, iat: seconds - 10, exp: seconds + 3000 };
    assert.equal(await verifyPushToken(token(good), identity, [jwk], now), true);
    assert.equal(await verifyPushToken(token({ ...good, aud: "https://elsewhere" }), identity, [jwk], now), false);
    assert.equal(await verifyPushToken(token({ ...good, email: "someone@else.com" }), identity, [jwk], now), false);
    assert.equal(await verifyPushToken(token({ ...good, email_verified: false }), identity, [jwk], now), false);
    assert.equal(await verifyPushToken(token({ ...good, exp: seconds - 600 }), identity, [jwk], now), false);
    assert.equal(await verifyPushToken(token({ ...good, iss: "https://evil.example" }), identity, [jwk], now), false);
    assert.equal(await verifyPushToken(token(good, "unknown-kid"), identity, [jwk], now), false);
    const forged = token(good).replace(/.$/, (c) => (c === "A" ? "B" : "A"));
    assert.equal(await verifyPushToken(forged, identity, [jwk], now), false);
    assert.equal(await verifyPushToken(null, identity, [jwk], now), false);
});

// ── play-verify and play-rtdn, end to end ──

/** Google, as the tests tell it: purchases by token, and the acknowledgements it received. */
class FakePlay implements PlayApi {
    purchases = new Map<string, PlaySubscription>();
    acknowledged: string[] = [];
    async subscription(token: string) { return this.purchases.get(token) ?? null; }
    async acknowledge(_product: string, token: string) {
        this.acknowledged.push(token);
        const sub = this.purchases.get(token);
        if (sub) sub.acknowledged = true;
    }
}

const days = (n: number) => new Date(Date.now() + n * 86_400_000);
const day = (d: Date) => d.toLocaleDateString("en-CA", { timeZone: "Africa/Algiers" });

test("play-verify and play-rtdn: a purchase is read from Google, checked, recorded and acknowledged", { skip: !url && "no DISTRIGO_TEST_DB_URL" }, async (t) => {
    const sql = postgres(url!, { max: 2, onnotice: () => {} });
    t.after(() => sql.end());
    const play = new FakePlay();
    const config = { sql, packageName: "com.distrigo.app", play };

    const buyer = crypto.randomUUID();
    const coded = crypto.randomUUID();
    const nobody = crypto.randomUUID();
    await sql`insert into auth.users (id, email, is_anonymous) values
              (${buyer}, ${`buyer-${buyer}@example.com`}, false), (${coded}, null, true), (${nobody}, ${`n-${nobody}@example.com`}, false)`;
    const [{ created }] = await sql`select private.create_business(${buyer}::uuid, 'Dépôt Golfe', ${`phone-${buyer}`}) as created`;
    const business: string = created.business_id;
    await sql`select private.create_business(${coded}::uuid, 'Dépôt Code', ${`phone-${coded}`})`;
    const [{ account }] = await sql`select private.play_account_id(${business}::uuid) as account`;

    const purchase = (over: Partial<PlaySubscription> = {}): PlaySubscription => ({
        state: "SUBSCRIPTION_STATE_ACTIVE", productId: "distroniq_solo", basePlanId: "solo-monthly", expiresAt: days(40),
        linkedPurchaseToken: null, latestOrderId: `GPA.${crypto.randomUUID()}`, acknowledged: false, accountId: account, ...over,
    });
    const verifyAs = (user: string, token: string, product = "distroniq_solo") =>
        playVerify(config, user, { purchase_token: token, product_id: product });

    assert.deepEqual((await playVerify({ ...config, play: null }, buyer, { purchase_token: "t", product_id: "distroniq_solo" })),
        { status: 503, body: { status: "billing_unavailable" } });
    assert.equal((await verifyAs(coded, "t")).body.status, "email_required");
    assert.equal((await verifyAs(nobody, "t")).body.status, "no_business");
    assert.equal((await verifyAs(buyer, "nothing")).body.status, "unknown_purchase");
    play.purchases.set("wrong-plan", purchase({ basePlanId: "solo-weekly" }));
    assert.equal((await verifyAs(buyer, "wrong-plan")).body.status, "unknown_product");
    play.purchases.set("named", purchase());
    assert.equal((await verifyAs(buyer, "named", "distroniq_business_5")).body.status, "unknown_product");
    play.purchases.set("theirs", purchase({ accountId: "0".repeat(64) }));
    assert.equal((await verifyAs(buyer, "theirs")).body.status, "other_account");
    assert.equal(play.acknowledged.length, 0, "nothing refused was acknowledged");

    // The real thing: recorded, the license's last day moves, Google is told once
    play.purchases.set("tok-gulf", purchase({ expiresAt: days(40) }));
    const ok = await verifyAs(buyer, "tok-gulf");
    assert.equal(ok.body.status, "ok");
    assert.equal(ok.body.valid_to, day(days(40)));
    assert.deepEqual(play.acknowledged, ["tok-gulf"]);
    assert.equal((await verifyAs(buyer, "tok-gulf")).body.status, "ok");
    assert.deepEqual(play.acknowledged, ["tok-gulf"], "an acknowledged purchase is not acknowledged again");
    const [row] = await sql`select business_id, user_id, acknowledged from private.play_subscriptions where purchase_token = 'tok-gulf'`;
    assert.equal(row.business_id, business);
    assert.equal(row.user_id, buyer);
    assert.equal(row.acknowledged, true);

    // Notifications: Google is asked again; what it says now is recorded
    const notify = (body: Record<string, unknown>) => playNotification(config, { packageName: "com.distrigo.app", ...body });
    assert.equal((await notify({ testNotification: { version: "1.0" } })).body.status, "test");
    assert.equal((await playNotification(config, { packageName: "com.other.app", subscriptionNotification: { purchaseToken: "tok-gulf" } })).body.status, "ignored");
    assert.equal((await notify({ oneTimeProductNotification: {} })).body.status, "ignored");

    play.purchases.get("tok-gulf")!.expiresAt = days(70);
    play.purchases.get("tok-gulf")!.latestOrderId = "GPA.renewal-1";
    assert.equal((await notify({ subscriptionNotification: { notificationType: 2, purchaseToken: "tok-gulf" } })).body.status, "recorded");
    const [{ last }] = await sql`select private.play_valid_to(${business}::uuid) as last`;
    assert.equal(day(last), day(days(70)), "a renewal moves the end");

    // Refunded and revoked: Google now reports it expired; the voided-purchase notification brings it in too
    Object.assign(play.purchases.get("tok-gulf")!, { state: "SUBSCRIPTION_STATE_EXPIRED", expiresAt: new Date(Date.now() - 60_000) });
    assert.equal((await notify({ voidedPurchaseNotification: { purchaseToken: "tok-gulf", productType: 1 } })).body.status, "recorded");
    const [{ none }] = await sql`select private.play_valid_to(${business}::uuid) as none`;
    assert.equal(none, null, "a refunded purchase gives nothing more");

    // A purchase Google tells us about before the app could verify it still finds its business by its hashed id
    play.purchases.set("tok-early", purchase({ expiresAt: days(30) }));
    assert.equal((await notify({ subscriptionNotification: { notificationType: 4, purchaseToken: "tok-early" } })).body.status, "recorded");
    assert.ok(play.acknowledged.includes("tok-early"));
    const [early] = await sql`select business_id, user_id from private.play_subscriptions where purchase_token = 'tok-early'`;
    assert.equal(early.business_id, business);
    assert.equal(early.user_id, null);

    // And one naming no business we know writes nothing
    play.purchases.set("tok-stray", purchase({ accountId: "f".repeat(64) }));
    assert.equal((await notify({ subscriptionNotification: { notificationType: 4, purchaseToken: "tok-stray" } })).body.status, "unknown_token");
    assert.equal((await sql`select 1 from private.play_subscriptions where purchase_token = 'tok-stray'`).length, 0);

    // The payments: one per Google order
    const [{ n }] = await sql`select count(*)::int as n from private.payments where provider = 'google_play' and business_id = ${business}::uuid`;
    assert.equal(n, 3, "the first order, its renewal, and the early purchase");
});

// The operations end to end, against the migration on a local PostgreSQL: supabase/tests/local/run.sh starts
// one and sets DISTRIGO_TEST_DB_URL. With DISTRIGO_EXPORT_GOLDEN=1 the licenses issued are written to
// app/src/test/resources/license/server-golden.json, which the app's ServerTokensTest verifies.
import assert from "node:assert/strict";
import { createPrivateKey, createPublicKey, verify } from "node:crypto";
import { writeFileSync } from "node:fs";
import { test } from "node:test";
import postgres from "postgres";
import { importSigningKey } from "../_shared/jws.ts";
import { BadRequest } from "../_shared/protocol.ts";
import {
    activate, businessProfile, codesAdmin, type Config, createNonce, deleteAccount, refresh, release, renewWithCode,
} from "../_shared/service.ts";
import { DEBUG_SIGNING_DIGEST, fixture, testKeys } from "./support.ts";

const url = process.env.DISTRIGO_TEST_DB_URL;

/** The operations' configuration, as Supabase gives it, but with test-a and a fixed now. */
async function configFor(sql: postgres.Sql): Promise<Config> {
    return {
        sql,
        signingKey: await importSigningKey(testKeys["test-a"]),
        keyId: "test-a",
        packageName: "com.distrigo.app",
        signingDigests: [DEBUG_SIGNING_DIGEST],
        revocation: async () => ({ entries: new Map(), fresh: true }),
        now: () => new Date("2026-10-10T12:00:00Z"),
    };
}

/** The fixture's activation nonce, handed out (again) to this user, as license-nonce would have. */
async function handOutActivation(sql: postgres.Sql, user: string): Promise<void> {
    await sql`delete from private.nonces where value = decode(${fixture.activation_nonce_hex}, 'hex')`;
    await sql`insert into private.nonces (value, user_id, purpose, expires_at)
              values (decode(${fixture.activation_nonce_hex}, 'hex'), ${user}::uuid, 'activate', now() + interval '5 minutes')`;
}

/** The fixture phone's activation request, for this user, with what else the app sends. */
const activationBody = (user: string, extra: Record<string, unknown> = {}) => ({
    nonce: Buffer.from(fixture.activation_nonce_hex, "hex").toString("base64url"),
    installation_id: fixture.installation_id,
    certificate_chain: fixture.chain,
    signature: fixture.activation_signature,
    android_id: `m34-${user}`,
    model: fixture.model,
    os_version: "16",
    app_version: "1.0",
    ...extra,
});

/** The claims of a license, after checking its signature with the test key's public half. */
function claimsOf(token: string): Record<string, unknown> {
    const [header, payload, signature] = token.split(".");
    const key = createPublicKey(createPrivateKey(testKeys["test-a"]));
    assert.ok(verify("sha256", Buffer.from(`${header}.${payload}`), { key, dsaEncoding: "ieee-p1363" }, Buffer.from(signature, "base64url")));
    return JSON.parse(Buffer.from(payload, "base64url").toString());
}

test("activation, check-in, release and deletion, with a real phone's key", { skip: !url && "no DISTRIGO_TEST_DB_URL" }, async (t) => {
    const sql = postgres(url!, { max: 4, onnotice: () => {} });
    t.after(() => sql.end());
    const config = await configFor(sql);
    const owner = crypto.randomUUID();
    const stranger = crypto.randomUUID();
    await sql`insert into auth.users (id, email) values (${owner}, 'owner@example.com'), (${stranger}, 'other@example.com')`;

    /** The fixture's nonces, as license-nonce would have handed them to this user. */
    const handOut = async (hex: string, user: string, purpose: string) =>
        sql`insert into private.nonces (value, user_id, purpose, expires_at)
            values (decode(${hex}, 'hex'), ${user}::uuid, ${purpose}, now() + interval '5 minutes')`;
    const activation = (extra: Record<string, unknown> = {}) => activationBody(owner, extra);
    const checkIn = (nonceHex: string) => ({
        nonce: Buffer.from(nonceHex, "hex").toString("base64url"),
        installation_id: fixture.installation_id,
        signature: fixture.refresh_signature,
        app_version: "1.0",
        signals: { debuggable: false },
    });
    const golden: Record<string, string> = {};

    await t.test("a nonce is 32 random bytes, base64url", async () => {
        const answer = await createNonce(config, owner, { purpose: "refresh" });
        assert.equal(Buffer.from(answer.body.nonce as string, "base64url").length, 32);
    });

    await handOut(fixture.activation_nonce_hex, owner, "activate");

    await t.test("an account without a business is asked for its name, and the nonce stays usable", async () => {
        const answer = await activate(config, owner, activation());
        assert.deepEqual(answer.body, { status: "no_business" });
        const [{ used }] = await sql`select used_at is not null as used from private.nonces where value = decode(${fixture.activation_nonce_hex}, 'hex')`;
        assert.equal(used, false);
    });

    await t.test("another signing certificate is refused before anything is written", async () => {
        const answer = await activate({ ...config, signingDigests: ["00".repeat(32)] }, owner, activation({ business_name: "X" }));
        assert.deepEqual(answer.body, { status: "refused", reason: "repackaged" });
    });

    await t.test("a signature that is not the phone's is refused", async () => {
        const answer = await activate(config, owner, activation({ business_name: "X", signature: fixture.refresh_signature }));
        assert.deepEqual(answer.body, { status: "bad_signature" });
    });

    await t.test("an activity or a sales-rep count off the lists is a bad request, before anything is read", async () => {
        await assert.rejects(activate(config, owner, activation({ business_name: "X", business_activity: "épicerie" })), BadRequest);
        await assert.rejects(activate(config, owner, activation({ business_name: "X", business_sales_reps: "3" })), BadRequest);
    });

    let deviceId = "";
    await t.test("with its name, the business is made, its trial started, and the phone gets its first license", async () => {
        const answer = await activate(config, owner, activation({
            business_name: "Distribution M34",
            contact_phone: "0661 23 45 67",
            business_activity: "boissons",
            business_sales_reps: "2-5",
        }));
        assert.equal(answer.body.status, "activated", JSON.stringify(answer.body));
        assert.equal(answer.body.tier, "normal");
        assert.equal(answer.body.trial, true);
        deviceId = answer.body.device_id as string;
        const claims = claimsOf(answer.body.license as string);
        assert.equal(claims.dev, fixture.key_hash);
        assert.equal(claims.iid, fixture.installation_id);
        assert.equal(claims.sub, owner);
        assert.equal(claims.org, answer.body.business_id);
        assert.equal(claims.plan, "trial");
        assert.equal(claims.seq, 1);
        assert.equal((claims.ou as number) - (claims.iat as number), 14 * 86400);
        const [device] = await sql`select policy_tier, attestation from public.devices where id = ${deviceId}::uuid`;
        assert.equal(device.policy_tier, "normal");
        assert.equal(device.attestation.security_level, "tee");
        assert.equal(device.attestation.boot_state, "verified");
        const [business] = await sql`select contact_phone, activity, sales_reps from public.businesses where id = ${claims.org as string}::uuid`;
        assert.deepEqual({ ...business }, { contact_phone: "0661 23 45 67", activity: "boissons", sales_reps: "2-5" });
        assert.equal(answer.body.code, undefined);
        golden.activation = answer.body.license as string;
    });

    await t.test("the same activation again is a replay", async () => {
        assert.deepEqual((await activate(config, owner, activation({ business_name: "Distribution M34" }))).body, { status: "bad_nonce" });
    });

    await handOut(fixture.refresh_nonce_hex, owner, "refresh");

    await t.test("a check-in signed by the phone gets the next license", async () => {
        const answer = await refresh(config, owner, checkIn(fixture.refresh_nonce_hex));
        assert.equal(answer.body.status, "ok", JSON.stringify(answer.body));
        const claims = claimsOf(answer.body.license as string);
        assert.equal(claims.seq, 2);
        assert.equal(claims.dev, fixture.key_hash);
        const [device] = await sql`select last_integrity, policy_tier from public.devices where id = ${deviceId}::uuid`;
        assert.deepEqual(device.last_integrity, { debuggable: false });
        assert.equal(device.policy_tier, "normal");
        golden.refresh = answer.body.license as string;
    });

    await t.test("the same check-in again is a replay", async () => {
        assert.deepEqual((await refresh(config, owner, checkIn(fixture.refresh_nonce_hex))).body, { status: "bad_nonce" });
    });

    await t.test("a check-in whose signature covers another nonce is refused", async () => {
        const other = "ab".repeat(32);
        await handOut(other, owner, "refresh");
        assert.deepEqual((await refresh(config, owner, checkIn(other))).body, { status: "bad_signature" });
    });

    await t.test("another account cannot check in for this phone", async () => {
        const other = "cd".repeat(32);
        await handOut(other, stranger, "refresh");
        assert.deepEqual((await refresh(config, stranger, checkIn(other))).body, { status: "unknown_device" });
    });

    await t.test("a released phone learns it at its next check-in", async () => {
        assert.deepEqual((await release(config, stranger, { device_id: deviceId })).body, { status: "not_allowed" });
        assert.deepEqual((await release(config, owner, { device_id: deviceId })).body, { status: "released" });
        const other = "ef".repeat(32);
        await handOut(other, owner, "refresh");
        assert.deepEqual((await refresh(config, owner, checkIn(other))).body, { status: "revoked" });
    });

    await t.test("deleting the account takes the business with it, then the auth user", async () => {
        const deleted: string[] = [];
        const answer = await deleteAccount(config, owner, async (id) => {
            deleted.push(id);
        });
        assert.deepEqual(answer.body, { status: "deleted" });
        assert.deepEqual(deleted, [owner]);
        const [{ count }] = await sql`select count(*)::int as count from public.devices where id = ${deviceId}::uuid`;
        assert.equal(count, 0);
    });

    if (process.env.DISTRIGO_EXPORT_GOLDEN === "1") {
        const out = new URL("../../../app/src/test/resources/license/server-golden.json", import.meta.url);
        writeFileSync(out, JSON.stringify({
            note: "Licenses issued by supabase/functions (SQL claims, WebCrypto signature) for a real Galaxy M34, signed "
                + "with test-a. Regenerate: DISTRIGO_EXPORT_GOLDEN=1 supabase/tests/local/run.sh",
            installation_id: fixture.installation_id,
            device_hash: fixture.key_hash,
            tokens: golden,
        }, null, 2) + "\n");
    }
});

test("activation codes: refused, asked for a company, made, joined, renewed", { skip: !url && "no DISTRIGO_TEST_DB_URL" }, async (t) => {
    const sql = postgres(url!, { max: 4, onnotice: () => {} });
    t.after(() => sql.end());
    const config = await configFor(sql);
    const client = { ip: "203.0.113.42" };
    // Two anonymous accounts: the merchant's first phone, then the one after it
    const first = crypto.randomUUID();
    const next = crypto.randomUUID();
    await sql`insert into auth.users (id, is_anonymous) values (${first}, true), (${next}, true)`;
    const newCode = async (days = 182) =>
        (await sql`select code from private.create_codes(1, 'solo', ${days}, 'Tests Node')`)[0].code as string;
    const unknownCode = async () => (await sql`select private.format_code(private.new_code()) as code`)[0].code as string;
    const failures = async () =>
        (await sql`select count(*)::int as n from private.code_attempts where installation_id = ${fixture.installation_id}::uuid`)[0].n;
    const code = await newCode();

    await handOutActivation(sql, first);

    await t.test("an unknown code is refused, counted, and leaves the nonce usable", async () => {
        const answer = await activate(config, first, activationBody(first, { activation_code: await unknownCode() }), client);
        assert.deepEqual(answer.body, { status: "bad_code" });
        assert.equal(await failures(), 1);
        const [{ used }] = await sql`select used_at is not null as used from private.nonces where value = decode(${fixture.activation_nonce_hex}, 'hex')`;
        assert.equal(used, false);
    });

    await t.test("after five refusals an hour, the phone waits, even with a good code", async () => {
        for (let i = 0; i < 4; i++) await activate(config, first, activationBody(first, { activation_code: await unknownCode() }), client);
        assert.equal(await failures(), 5);
        const answer = await activate(config, first, activationBody(first, { activation_code: code }), client);
        assert.equal(answer.body.status, "too_many_attempts");
        assert.ok(new Date(answer.body.retry_at as string).getTime() > Date.now() + 59 * 60_000);
        await sql`delete from private.code_attempts`;
    });

    await t.test("a code without a company asks for one, and nothing is written", async () => {
        const answer = await activate(config, first, activationBody(first, { activation_code: code }), client);
        assert.deepEqual(answer.body, { status: "code_needs_business" });
        const [{ n }] = await sql`select count(*)::int as n from public.memberships where user_id = ${first}::uuid`;
        assert.equal(n, 0);
    });

    let business = "";
    await t.test("with the company, the business is made with the code's plan, and the phone activated", async () => {
        const answer = await activate(config, first, activationBody(first, {
            activation_code: code.toLowerCase().replaceAll("-", " "),
            business_name: "Épicerie Node",
            contact_phone: "0770 12 34 56",
        }), client);
        assert.equal(answer.body.status, "activated", JSON.stringify(answer.body));
        assert.equal(answer.body.code, "created");
        assert.equal(answer.body.trial, false);
        const claims = claimsOf(answer.body.license as string);
        assert.equal(claims.plan, "solo");
        assert.equal(claims.sub, first);
        business = claims.org as string;
        const [row] = await sql`select name, contact_phone from public.businesses where id = ${business}::uuid`;
        assert.deepEqual({ ...row }, { name: "Épicerie Node", contact_phone: "0770 12 34 56" });
        // 182 days from today, through the last one: the license's vt ends the 182nd day
        const [{ days }] = await sql`select valid_to - valid_from + 1 as days from public.subscriptions where business_id = ${business}::uuid`;
        assert.equal(days, 182);
    });

    await t.test("the same code on the next phone's account joins that business", async () => {
        await handOutActivation(sql, next);
        const answer = await activate(config, next, activationBody(next, { activation_code: code }), client);
        assert.equal(answer.body.status, "activated", JSON.stringify(answer.body));
        assert.equal(answer.body.code, "joined");
        const claims = claimsOf(answer.body.license as string);
        assert.equal(claims.org, business);
        assert.equal(claims.sub, next);
    });

    await t.test("« Saisir un code » adds a new code's days, once; a key or an unknown code adds nothing", async () => {
        const renewal = await newCode(30);
        const answer = await renewWithCode(config, next, { code: renewal }, client);
        assert.equal(answer.body.status, "renewed", JSON.stringify(answer.body));
        assert.equal(answer.body.business_id, business);
        assert.equal(answer.body.days, 30);
        const [{ days }] = await sql`select valid_to - valid_from + 1 as days from public.subscriptions where business_id = ${business}::uuid`;
        assert.equal(days, 182 + 30);
        assert.equal((await renewWithCode(config, next, { code: renewal }, client)).body.status, "code_used");
        assert.equal((await renewWithCode(config, next, { code }, client)).body.status, "code_used");
        assert.equal((await renewWithCode(config, next, { code: await unknownCode() }, client)).body.status, "bad_code");
        const [{ n }] = await sql`select count(*)::int as n from private.code_attempts where user_id = ${next}::uuid`;
        assert.equal(n, 1);
        await assert.rejects(renewWithCode(config, next, {}, client), BadRequest);
    });
});

test("« Informations de l'entreprise »: read, changed field by field, and checked", { skip: !url && "no DISTRIGO_TEST_DB_URL" }, async (t) => {
    const sql = postgres(url!, { max: 2, onnotice: () => {} });
    t.after(() => sql.end());
    const config = await configFor(sql);
    const owner = "c0ffee00-0000-4000-8000-000000000031";
    const stranger = "c0ffee00-0000-4000-8000-000000000032";
    await sql`insert into auth.users (id, email) values (${owner}::uuid, 'profil@example.com'), (${stranger}::uuid, 'x@example.com')`;
    await sql`select private.create_business(${owner}::uuid, 'Épicerie Profil', 'phone-profil', '0770 00 11 22', 'alimentation', '1')`;

    await t.test("an empty request reads the profile", async () => {
        const answer = await businessProfile(config, owner, {});
        assert.equal(answer.status, 200);
        assert.deepEqual(
            { ...answer.body, business_id: undefined },
            { status: "ok", business_id: undefined, name: "Épicerie Profil", contact_phone: "0770 00 11 22",
              activity: "alimentation", sales_reps: "1", can_edit: true },
        );
        assert.equal((await businessProfile(config, stranger, {})).body.status, "no_business");
    });

    await t.test("the fields sent are changed, the others kept; a blank phone clears it", async () => {
        let answer = await businessProfile(config, owner, { name: " Épicerie Profil Centre ", sales_reps: "2-5" });
        assert.equal(answer.body.name, "Épicerie Profil Centre");
        assert.equal(answer.body.sales_reps, "2-5");
        assert.equal(answer.body.activity, "alimentation");
        assert.equal(answer.body.contact_phone, "0770 00 11 22");
        answer = await businessProfile(config, owner, { contact_phone: "" });
        assert.equal(answer.body.contact_phone, null);
        answer = await businessProfile(config, owner, { contact_phone: "0661 22 33 44", activity: "boissons" });
        assert.equal(answer.body.contact_phone, "0661 22 33 44");
        assert.equal(answer.body.activity, "boissons");
    });

    await t.test("what the table would refuse is a bad request, and changes nothing", async () => {
        for (const body of [
            { name: "   " }, { name: null }, { name: "x".repeat(121) }, { activity: "piraterie" }, { activity: null },
            { sales_reps: "12" }, { contact_phone: "0".repeat(31) }, { contact_phone: 661 }, { business_id: "abc" },
        ]) {
            await assert.rejects(businessProfile(config, owner, body), BadRequest, JSON.stringify(body));
        }
        const answer = await businessProfile(config, owner, {});
        assert.equal(answer.body.name, "Épicerie Profil Centre");
        assert.equal(answer.body.activity, "boissons");
    });
});

test("« Administration »: codes created, followed and revoked by the owner alone", { skip: !url && "no DISTRIGO_TEST_DB_URL" }, async (t) => {
    const sql = postgres(url!, { max: 2, onnotice: () => {} });
    t.after(() => sql.end());
    const config = await configFor(sql);
    const owner = "c0ffee00-0000-4000-8000-000000000041";
    const merchant = "c0ffee00-0000-4000-8000-000000000042";
    await sql`insert into auth.users (id, email, raw_app_meta_data) values
        (${owner}::uuid, 'owner-node@example.com', '{"distrigo_admin": true}'),
        (${merchant}::uuid, 'merchant-node@example.com', '{}')`;
    const wellFormed = /^[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$/;

    await t.test("anyone else is told not_allowed, whatever they ask, and nothing is made", async () => {
        for (const body of [{ action: "batches" }, { action: "create", count: 5, plan: "solo", days: 30, batch: "Pirate" }]) {
            assert.deepEqual((await codesAdmin(config, merchant, body)).body, { status: "not_allowed" });
        }
        const [{ n }] = await sql`select count(*)::int as n from private.activation_codes where batch = 'Pirate'`;
        assert.equal(n, 0);
    });

    let code = "";
    await t.test("a batch is created and shown once; it expires in 12 months unless told otherwise", async () => {
        const answer = await codesAdmin(config, owner, { action: "create", count: 3, plan: "solo", days: 180, batch: "Revendeur Node" });
        assert.equal(answer.body.status, "created", JSON.stringify(answer.body));
        const codes = answer.body.codes as { code: string; expires_on: string | null }[];
        assert.equal(codes.length, 3);
        for (const c of codes) assert.match(c.code, wellFormed);
        const [{ inAYear }] = await sql`select (current_date + 365)::text as "inAYear"`;
        assert.equal(codes[0].expires_on, inAYear);
        code = codes[0].code;

        const never = await codesAdmin(config, owner, { action: "create", count: 1, plan: "solo", days: 30, batch: "Sans fin", expires_on: null });
        assert.equal((never.body.codes as { expires_on: string | null }[])[0].expires_on, null);
        const dated = await codesAdmin(config, owner, { action: "create", count: 1, plan: "solo", days: 30, batch: "Daté", expires_on: "2027-06-30", business_name: "Épicerie Datée", note: "salon" });
        assert.equal((dated.body.codes as { expires_on: string | null }[])[0].expires_on, "2027-06-30");

        // « Autre » on the screen: any whole number of days, up to the database's 1,100
        for (const days of [15, 45, 1100]) {
            const custom = await codesAdmin(config, owner, { action: "create", count: 1, plan: "solo", days, batch: `Durée ${days}` });
            assert.equal(custom.body.status, "created", JSON.stringify(custom.body));
        }
    });

    await t.test("the batches count what became of their codes", async () => {
        const answer = await codesAdmin(config, owner, { action: "batches" });
        assert.equal(answer.body.status, "ok");
        const batch = (answer.body.batches as Record<string, unknown>[]).find((b) => b.batch === "Revendeur Node");
        assert.ok(batch);
        assert.equal(batch.plan, "solo");
        assert.equal(batch.days, 180);
        assert.equal(batch.total, 3);
        assert.equal(batch.unused, 3);
        assert.equal(batch.revoked, 0);
        assert.match(batch.created_on as string, /^\d{4}-\d{2}-\d{2}$/);
    });

    await t.test("a code is checked and revoked; the batch counts it", async () => {
        assert.equal((await codesAdmin(config, owner, { action: "status", code })).body.status, "unused");
        assert.equal((await codesAdmin(config, owner, { action: "status", code: code.toLowerCase().replaceAll("-", " ") })).body.status, "unused");
        assert.equal((await codesAdmin(config, owner, { action: "revoke", code, reason: "Perdu" })).body.status, "revoked");
        const status = (await codesAdmin(config, owner, { action: "status", code })).body;
        assert.equal(status.status, "revoked");
        assert.equal(status.batch, "Revendeur Node");
        const batches = (await codesAdmin(config, owner, { action: "batches" })).body.batches as Record<string, unknown>[];
        const batch = batches.find((b) => b.batch === "Revendeur Node")!;
        assert.equal(batch.unused, 2);
        assert.equal(batch.revoked, 1);
        assert.equal((await codesAdmin(config, owner, { action: "revoke", code: "0000-0000-0000" })).body.status, "unknown");
    });

    await t.test("what the screen never sends is a bad request", async () => {
        const create = { action: "create", count: 3, plan: "solo", days: 30, batch: "X" };
        for (const body of [
            {}, { action: "drop" }, { ...create, count: 0 }, { ...create, count: 201 }, { ...create, count: "3" },
            { ...create, count: 2.5 }, { ...create, plan: "trial" }, { ...create, days: 0 }, { ...create, days: 1101 }, { ...create, batch: "  " },
            { ...create, expires_on: "2027-02-30" }, { ...create, expires_on: "30/06/2027" }, { action: "status" },
            { action: "revoke", code: "" },
        ]) {
            await assert.rejects(codesAdmin(config, owner, body), BadRequest, JSON.stringify(body));
        }
    });
});

test("« Comptes clients »: found, suspended, reactivated and deleted by the owner alone", { skip: !url && "no DISTRIGO_TEST_DB_URL" }, async (t) => {
    const sql = postgres(url!, { max: 2, onnotice: () => {} });
    t.after(() => sql.end());
    const config = await configFor(sql);
    const owner = "c0ffee00-0000-4000-8000-000000000051";
    const client = "c0ffee00-0000-4000-8000-000000000052";
    await sql`insert into auth.users (id, email, raw_app_meta_data) values
        (${owner}::uuid, 'owner-clients-node@example.com', '{"distrigo_admin": true}'),
        (${client}::uuid, 'client-node@example.com', '{}')`;
    await sql`select private.create_business(${owner}::uuid, 'Dépôt du Patron', 'phone-owner-node')`;
    await sql`select private.create_business(${client}::uuid, 'Superette Node', 'phone-client-node', '0550 98 76 54')`;
    let business = "";

    await t.test("a client is found by its phone, with its subscription and owner", async () => {
        assert.deepEqual((await codesAdmin(config, client, { action: "businesses" })).body, { status: "not_allowed" });
        const answer = await codesAdmin(config, owner, { action: "businesses", query: "0550 987" });
        assert.equal(answer.body.status, "ok");
        const found = answer.body.businesses as Record<string, unknown>[];
        assert.equal(found.length, 1);
        assert.equal(found[0].name, "Superette Node");
        assert.equal(found[0].owner_email, "client-node@example.com");
        assert.equal(found[0].status, "active");
        assert.equal(found[0].phones, 0);
        business = found[0].business_id as string;
        const latest = (await codesAdmin(config, owner, { action: "businesses" })).body.businesses as unknown[];
        assert.ok(latest.length >= 2 && latest.length <= 30);
    });

    await t.test("suspended with a note, then reactivated; never the owner's own", async () => {
        const suspended = await codesAdmin(config, owner, { action: "suspend", business_id: business, note: "Impayé" });
        assert.deepEqual(suspended.body, { status: "ok", subscription_status: "suspended" });
        const [{ status }] = await sql`select status from public.subscriptions where business_id = ${business}::uuid`;
        assert.equal(status, "suspended");
        assert.equal((await codesAdmin(config, owner, { action: "reactivate", business_id: business })).body.subscription_status, "active");
        const [{ own }] = await sql`select business_id::text as own from public.memberships where user_id = ${owner}::uuid`;
        assert.equal((await codesAdmin(config, owner, { action: "suspend", business_id: own })).body.status, "own_business");
        assert.equal((await codesAdmin(config, owner, { action: "delete_business", business_id: own })).body.status, "own_business");
    });

    await t.test("deleted, once", async () => {
        assert.equal((await codesAdmin(config, owner, { action: "delete_business", business_id: business })).body.status, "deleted");
        assert.equal((await codesAdmin(config, owner, { action: "delete_business", business_id: business })).body.status, "unknown_business");
        const found = (await codesAdmin(config, owner, { action: "businesses", query: "Superette Node" })).body.businesses as unknown[];
        assert.equal(found.length, 0);
    });

    await t.test("what the screen never sends is a bad request", async () => {
        for (const body of [
            { action: "suspend" }, { action: "suspend", business_id: "abc" }, { action: "delete_business" },
            { action: "businesses", query: "x".repeat(121) }, { action: "reactivate", business_id: 42 },
        ]) {
            await assert.rejects(codesAdmin(config, owner, body), BadRequest, JSON.stringify(body));
        }
    });
});


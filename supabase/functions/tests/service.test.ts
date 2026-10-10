// The operations end to end, against the migration on a local PostgreSQL: supabase/tests/local/run.sh starts
// one and sets DISTRIGO_TEST_DB_URL. With DISTRIGO_EXPORT_GOLDEN=1 the licenses issued are written to
// app/src/test/resources/license/server-golden.json, which the app's ServerTokensTest verifies.
import assert from "node:assert/strict";
import { createPrivateKey, createPublicKey, verify } from "node:crypto";
import { writeFileSync } from "node:fs";
import { test } from "node:test";
import postgres from "postgres";
import { importSigningKey } from "../_shared/jws.ts";
import { activate, type Config, createNonce, deleteAccount, refresh, release } from "../_shared/service.ts";
import { DEBUG_SIGNING_DIGEST, fixture, testKeys } from "./support.ts";

const url = process.env.DISTRIGO_TEST_DB_URL;

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
    const config: Config = {
        sql,
        signingKey: await importSigningKey(testKeys["test-a"]),
        keyId: "test-a",
        packageName: "com.distrigo.app",
        signingDigests: [DEBUG_SIGNING_DIGEST],
        revocation: async () => ({ entries: new Map(), fresh: true }),
        now: () => new Date("2026-10-10T12:00:00Z"),
    };
    const owner = crypto.randomUUID();
    const stranger = crypto.randomUUID();
    await sql`insert into auth.users (id, email) values (${owner}, 'owner@example.com'), (${stranger}, 'other@example.com')`;

    /** The fixture's nonces, as license-nonce would have handed them to this user. */
    const handOut = async (hex: string, user: string, purpose: string) =>
        sql`insert into private.nonces (value, user_id, purpose, expires_at)
            values (decode(${hex}, 'hex'), ${user}::uuid, ${purpose}, now() + interval '5 minutes')`;
    const activation = (extra: Record<string, unknown> = {}) => ({
        nonce: Buffer.from(fixture.activation_nonce_hex, "hex").toString("base64url"),
        installation_id: fixture.installation_id,
        certificate_chain: fixture.chain,
        signature: fixture.activation_signature,
        android_id: `m34-${owner}`,
        model: fixture.model,
        os_version: "16",
        app_version: "1.0",
        ...extra,
    });
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

    let deviceId = "";
    await t.test("with its name, the business is made, its trial started, and the phone gets its first license", async () => {
        const answer = await activate(config, owner, activation({ business_name: "Distribution M34" }));
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

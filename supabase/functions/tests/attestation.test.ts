// The attestation check, on a real chain: a Galaxy M34 (Android 16, TEE) captured by DeviceKeyTest.
import assert from "node:assert/strict";
import { createHash, X509Certificate } from "node:crypto";
import { test } from "node:test";
import { type Expected, GOOGLE_ROOT_KEYS, inspect } from "../_shared/attestation.ts";
import { base64Decode } from "../_shared/jws.ts";
import { judge } from "../_shared/policy.ts";
import { parseCertificate } from "../_shared/x509.ts";
import { DEBUG_SIGNING_DIGEST, fixture } from "./support.ts";

const chain = fixture.chain.map(base64Decode);
const challenge = Uint8Array.from(Buffer.from(fixture.activation_nonce_hex, "hex"));
// The chain's TEE certificate is short-lived (remote key provisioning): judged on the day it was captured.
const CAPTURED = new Date("2026-10-10T12:00:00Z");

const expected = (overrides: Partial<Expected> = {}): Expected => ({
    challenge,
    packageName: "com.distrigo.app",
    signingDigests: [DEBUG_SIGNING_DIGEST],
    revoked: new Map(),
    now: CAPTURED,
    ...overrides,
});

const flipLastByte = (der: Uint8Array) => {
    const copy = Uint8Array.from(der);
    copy[copy.length - 1] ^= 0x01;
    return copy;
};

test("the pinned roots are the two keys of Google's published roots", () => {
    const hashes = GOOGLE_ROOT_KEYS.map((key) => createHash("sha256").update(key).digest("hex").slice(0, 16));
    assert.deepEqual(hashes, ["feb2ea7551ee316e", "3ee44512a1af2beb"]);
});

test("our certificate reader agrees with Node's on every certificate of the chain", () => {
    for (const der of chain) {
        const ours = parseCertificate(der);
        const node = new X509Certificate(Buffer.from(der));
        assert.equal(ours.serialHex, node.serialNumber.toLowerCase().replace(/^0+/, ""));
        assert.equal(ours.notAfter.getTime(), Date.parse(node.validTo));
        assert.equal(ours.notBefore.getTime(), Date.parse(node.validFrom));
        assert.deepEqual(Buffer.from(ours.spki), node.publicKey.export({ type: "spki", format: "der" }));
    }
});

test("the Galaxy M34's attestation holds: Google's chain, TEE, verified boot, locked, our app and signer", async () => {
    const attestation = await inspect(chain, expected());
    assert.deepEqual(attestation.problems, []);
    assert.equal(attestation.googleChain, true);
    assert.equal(attestation.attested, true);
    assert.equal(attestation.challengeMatches, true);
    assert.equal(attestation.securityLevel, "tee");
    assert.equal(attestation.verifiedBootState, "verified");
    assert.equal(attestation.deviceLocked, true);
    assert.equal(attestation.packageMatches, true);
    assert.equal(attestation.signerMatches, true);
    assert.deepEqual(attestation.signatureDigests, [DEBUG_SIGNING_DIGEST]);
    assert.ok(attestation.attestationVersion! >= 100, `attestation version ${attestation.attestationVersion}`);
    assert.ok(attestation.osPatchLevel! >= 202001, `patch level ${attestation.osPatchLevel}`);
    // The hash the phone computed (DeviceKey, Kotlin) is the one the server computes.
    assert.equal(attestation.keyHash, fixture.key_hash);
    assert.deepEqual(judge(attestation), { accept: true, tier: "normal", reasons: [] });
});

test("an attestation made for another nonce is refused: it is a replay", async () => {
    const attestation = await inspect(chain, expected({ challenge: new Uint8Array(32) }));
    assert.equal(attestation.challengeMatches, false);
    assert.deepEqual(judge(attestation), { accept: false, reason: "stale_attestation" });
});

test("another signing certificate, or another package, is a repackaged app", async () => {
    const otherSigner = await inspect(chain, expected({ signingDigests: ["00".repeat(32)] }));
    assert.deepEqual(judge(otherSigner), { accept: false, reason: "repackaged" });
    const otherPackage = await inspect(chain, expected({ packageName: "com.distrigo.app.clone" }));
    assert.deepEqual(judge(otherPackage), { accept: false, reason: "repackaged" });
});

test("a broken link in a Google chain is refused as forged", async () => {
    const forgedLink = [chain[0], flipLastByte(chain[1]), ...chain.slice(2)];
    const attestation = await inspect(forgedLink, expected());
    assert.equal(attestation.brokenGoogleChain, true);
    assert.deepEqual(judge(attestation), { accept: false, reason: "forged_chain" });

    // The key's own certificate edited (here, its signature): the link to the TEE no longer holds.
    const forgedLeaf = [flipLastByte(chain[0]), ...chain.slice(1)];
    assert.deepEqual(judge(await inspect(forgedLeaf, expected())), { accept: false, reason: "forged_chain" });
});

test("a chain ending at a root that is not Google's proves nothing, and is reduced rather than refused", async () => {
    const unknownRoot = await inspect(chain, expected({ roots: [] }));
    assert.equal(unknownRoot.googleChain, false);
    assert.deepEqual(judge(unknownRoot), { accept: true, tier: "reduced", reasons: ["unknown_root"] });

    // The key's certificate alone, as from a phone that cannot attest.
    const alone = await inspect([chain[0]], expected());
    assert.deepEqual(judge(alone), { accept: true, tier: "reduced", reasons: ["unknown_root"] });
});

test("a revoked attestation key, or an expired certificate, reduces the phone", async () => {
    const intermediate = parseCertificate(chain[1]).serialHex;
    const revoked = await inspect(chain, expected({ revoked: new Map([[intermediate, "REVOKED"]]) }));
    assert.deepEqual(revoked.revoked, [{ serial: intermediate, status: "REVOKED" }]);
    assert.deepEqual(judge(revoked), { accept: true, tier: "reduced", reasons: ["revoked_attestation_key"] });

    const later = await inspect(chain, expected({ now: new Date("2040-01-01T00:00:00Z") }));
    assert.ok(later.expired.length > 0);
    assert.deepEqual(judge(later), { accept: true, tier: "reduced", reasons: ["expired_certificate"] });
});

test("what is not a certificate is refused, never thrown", async () => {
    const garbage = await inspect([Uint8Array.of(0x30, 0x03, 0x02, 0x01)], expected());
    assert.equal(garbage.keySpki, null);
    assert.ok(garbage.problems.length > 0);
    assert.deepEqual(judge(garbage), { accept: false, reason: "unreadable_key" });
    assert.deepEqual(judge(await inspect([], expected())), { accept: false, reason: "unreadable_key" });
});

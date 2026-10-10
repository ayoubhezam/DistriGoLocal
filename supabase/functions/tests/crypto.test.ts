// DER, the license signature, the device's signatures and the revocation list.
import assert from "node:assert/strict";
import { createPrivateKey, createPublicKey, sign, verify } from "node:crypto";
import { test } from "node:test";
import { children, DerError, oid, read, readWhole } from "../_shared/der.ts";
import {
    base64Decode, base64UrlEncode, importSigningKey, keyHash, LICENSE_TYPE, signLicense, verifyDeviceSignature,
} from "../_shared/jws.ts";
import { signedMessage } from "../_shared/protocol.ts";
import { revocationList } from "../_shared/revocation.ts";
import { derToP1363, parseCertificate } from "../_shared/x509.ts";
import { fixture, hexBytes, testKeys } from "./support.ts";

const deviceKey = parseCertificate(base64Decode(fixture.chain[0])).spki;
const activationNonce = hexBytes(fixture.activation_nonce_hex);
const refreshNonce = hexBytes(fixture.refresh_nonce_hex);

test("DER: long lengths, high tag numbers, object identifiers, and truncation", () => {
    // [704] EXPLICIT INTEGER 5, as an AuthorizationList writes it: BF 85 40.
    const tagged = read(Uint8Array.of(0xbf, 0x85, 0x40, 0x03, 0x02, 0x01, 0x05));
    assert.equal(tagged.cls, 2);
    assert.equal(tagged.tag, 704);
    assert.equal(children(tagged)[0].content[0], 5);
    const long = Uint8Array.from([0x04, 0x81, 0x80, ...new Array(128).fill(7)]);
    assert.equal(readWhole(long).content.length, 128);
    assert.equal(oid(read(Uint8Array.of(0x06, 0x0a, 0x2b, 0x06, 0x01, 0x04, 0x01, 0xd6, 0x79, 0x02, 0x01, 0x11))), "1.3.6.1.4.1.11129.2.1.17");
    assert.throws(() => read(Uint8Array.of(0x04, 0x05, 0x01)), DerError);
    assert.throws(() => read(Uint8Array.of(0x30, 0x80)), DerError);
    assert.throws(() => readWhole(Uint8Array.of(0x05, 0x00, 0x00)), DerError);
});

test("base64 both ways, and anything else refused", () => {
    const bytes = Uint8Array.of(251, 255, 0, 62, 63);
    assert.equal(base64UrlEncode(bytes), "-_8APj8");
    assert.deepEqual(base64Decode("-_8APj8"), bytes);
    assert.deepEqual(base64Decode("+/8APj8="), bytes);
    assert.throws(() => base64Decode("e30!"));
    assert.throws(() => base64Decode("abcde"));
});

test("a license signed here verifies with Node as ES256, header and claims intact", async () => {
    const key = await importSigningKey(testKeys["test-a"]);
    const claims = { iss: "distrigo-license", seq: 7, plan: "trial" };
    const token = await signLicense(claims, key, "test-a");
    const [header, payload, signature] = token.split(".");
    assert.deepEqual(JSON.parse(Buffer.from(header, "base64url").toString()), { alg: "ES256", kid: "test-a", typ: LICENSE_TYPE });
    assert.deepEqual(JSON.parse(Buffer.from(payload, "base64url").toString()), claims);
    const publicKey = createPublicKey(createPrivateKey(testKeys["test-a"]));
    const raw = Buffer.from(signature, "base64url");
    assert.equal(raw.length, 64);
    assert.ok(verify("sha256", Buffer.from(`${header}.${payload}`), { key: publicKey, dsaEncoding: "ieee-p1363" }, raw));
});

test("the phone's key hash is what the server computes from the certificate", async () => {
    assert.equal(await keyHash(deviceKey), fixture.key_hash);
});

test("the phone's activation and check-in signatures verify, each for its own message only", async () => {
    const activation = base64Decode(fixture.activation_signature);
    const checkIn = base64Decode(fixture.refresh_signature);
    const id = fixture.installation_id;
    assert.ok(await verifyDeviceSignature(deviceKey, signedMessage("activate", activationNonce, id), activation));
    assert.ok(await verifyDeviceSignature(deviceKey, signedMessage("refresh", refreshNonce, id), checkIn));
    // A signature never serves another purpose, nonce or installation.
    assert.equal(await verifyDeviceSignature(deviceKey, signedMessage("refresh", activationNonce, id), activation), false);
    assert.equal(await verifyDeviceSignature(deviceKey, signedMessage("activate", refreshNonce, id), activation), false);
    assert.equal(await verifyDeviceSignature(deviceKey, signedMessage("activate", activationNonce, "00000000-0000-4000-8000-000000000000"), activation), false);
    assert.equal(await verifyDeviceSignature(deviceKey, signedMessage("activate", activationNonce, id), Uint8Array.of(1, 2, 3)), false);
});

test("a DER ECDSA signature converts to the R‖S WebCrypto verifies, whatever its integers' lengths", async () => {
    const privateKey = createPrivateKey(testKeys["test-b"]);
    const spki = createPublicKey(privateKey).export({ type: "spki", format: "der" });
    for (let i = 0; i < 200; i++) {
        const data = Buffer.from(`message ${i}`);
        const der = sign("sha256", data, { key: privateKey, dsaEncoding: "der" });
        const raw = derToP1363(der, 32);
        assert.equal(raw.length, 64);
        assert.ok(verify("sha256", data, { key: createPublicKey(privateKey), dsaEncoding: "ieee-p1363" }, raw));
        assert.ok(await verifyDeviceSignature(spki, data, der));
    }
});

test("the revocation list is fetched once a day, and an outage keeps the last one", async () => {
    let calls = 0;
    let up = true;
    let now = 0;
    const list = revocationList(async () => {
        calls++;
        if (!up) throw new Error("offline");
        return new Response(JSON.stringify({ entries: { "8350192447815228107": { status: "REVOKED", reason: "KEY_COMPROMISE" } } }), {
            headers: { "cache-control": "public, max-age=86400" },
        });
    }, () => now);
    const first = await list();
    assert.equal(first.fresh, true);
    assert.equal(first.entries.get("8350192447815228107"), "REVOKED");
    now += 3600_000;
    await list();
    assert.equal(calls, 1);
    now += 86400_000;
    up = false;
    const stale = await list();
    assert.equal(calls, 2);
    assert.equal(stale.fresh, false);
    assert.equal(stale.entries.get("8350192447815228107"), "REVOKED");
});

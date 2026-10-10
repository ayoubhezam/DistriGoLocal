// What the tests share: the captured phone, and the test key that signs their licenses.
import { readFileSync } from "node:fs";

/** A Galaxy M34 activating and checking in, as DeviceKeyTest.write_an_attestation_fixture_when_asked wrote it. */
export const fixture: {
    model: string;
    installation_id: string;
    activation_nonce_hex: string;
    refresh_nonce_hex: string;
    key_hash: string;
    chain: string[];
    activation_signature: string;
    refresh_signature: string;
} = JSON.parse(readFileSync(new URL("./fixtures/galaxy-m34-attestation.json", import.meta.url), "utf8"));

/** SHA-256 of the debug signing certificate (~/.android/debug.keystore), which debug and benchmark builds share. */
export const DEBUG_SIGNING_DIGEST = "9141c319014f82b390c85ef87a8003e3c596d74f0bbd7fb336d320d2c8d14c28";

/** The test keys of tools/license/test-keys.json: test-a signs the server's licenses in these tests. */
export const testKeys: Record<string, string> = JSON.parse(
    readFileSync(new URL("../../../tools/license/test-keys.json", import.meta.url), "utf8"),
).keys;

export const hexBytes = (hex: string) => Uint8Array.from(Buffer.from(hex, "hex"));

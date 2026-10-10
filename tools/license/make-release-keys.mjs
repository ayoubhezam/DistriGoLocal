// Makes the server's license signing keys (docs/license_architecture.md §4.3), once, on your own machine:
//
//   node tools/license/make-release-keys.mjs [folder]
//
//  - lic-2026-a signs every license: it goes into Supabase's secrets and nowhere else;
//  - lic-2026-b is the standby, kept offline, uploaded only if lic-2026-a ever leaks.
//
// The private keys are written to [folder] (default: <home>/DistriGo-secrets/license-keys), which must be outside
// the repository, and are never printed. Everything printed is public: the two public keys the app pins in
// LicenseKeys.RELEASE. Existing keys are never overwritten.

import { createPublicKey, generateKeyPairSync, randomBytes, sign, verify } from "node:crypto";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, isAbsolute, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const KEY_IDS = ["lic-2026-a", "lic-2026-b"];

const repository = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const folder = resolve(process.argv[2] ?? join(homedir(), "DistriGo-secrets", "license-keys"));

const isInside = (path, parent) => {
    const between = relative(parent, path);
    return between === "" || (!between.startsWith("..") && !isAbsolute(between));
};

function refuse(reason) {
    console.error(`Nothing was made: ${reason}`);
    process.exit(1);
}

if (isInside(folder, repository)) refuse(`${folder} is inside the repository, where a private key could be committed.`);
for (const keyId of KEY_IDS) {
    if (existsSync(join(folder, `${keyId}.pem`))) refuse(`${keyId} already exists in ${folder}. The keys are made once.`);
}
mkdirSync(folder, { recursive: true });

const publicKeys = {};
for (const keyId of KEY_IDS) {
    const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });

    // Sign and verify once as the server and the app will: ES256, the signature as R‖S.
    const probe = randomBytes(32);
    const signature = sign("sha256", probe, { key: privateKey, dsaEncoding: "ieee-p1363" });
    if (!verify("sha256", probe, { key: publicKey, dsaEncoding: "ieee-p1363" }, signature)) refuse("the key failed its self-check.");

    const pkcs8 = privateKey.export({ type: "pkcs8", format: "der" }).toString("base64");
    // "wx": fails rather than overwrites. The mode keeps other accounts out where the file system supports it.
    writeFileSync(join(folder, `${keyId}.pem`), privateKey.export({ type: "pkcs8", format: "pem" }), { mode: 0o600, flag: "wx" });
    // For `supabase secrets set --env-file`: the key on one line, and the id the app pins it under.
    writeFileSync(join(folder, `${keyId}.env`), `LICENSE_SIGNING_KEY=${pkcs8}\nLICENSE_SIGNING_KID=${keyId}\n`, { mode: 0o600, flag: "wx" });
    publicKeys[keyId] = createPublicKey(privateKey).export({ type: "spki", format: "der" }).toString("base64");
}
writeFileSync(join(folder, "public-keys.json"), JSON.stringify(publicKeys, null, 2) + "\n", { flag: "wx" });

console.log(`Made in ${folder}:`);
console.log("  lic-2026-a.pem, lic-2026-a.env   signs the licenses: goes into Supabase's secrets, nowhere else");
console.log("  lic-2026-b.pem, lic-2026-b.env   the standby: keep it offline; upload it only if lic-2026-a leaks");
console.log("Back the folder up now, in two places (a password manager, a USB key kept apart).");
console.log("Whoever holds lic-2026-a can make licenses; if it is lost, the server switches to lic-2026-b.");
console.log("");
console.log("Public keys, for LicenseKeys.RELEASE (safe to share):");
for (const [keyId, key] of Object.entries(publicKeys)) console.log(`  "${keyId}" to "${key}",`);

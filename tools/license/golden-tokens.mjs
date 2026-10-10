// Writes the golden license tokens the app's unit tests verify: app/src/test/resources/license/golden.json.
//
//   node tools/license/golden-tokens.mjs
//
// Signed with Node's own crypto, the way the server signs (ES256, the signature as raw R‖S), so the tests
// check the app against tokens it did not make itself. The keys are TEST keys, kept in test-keys.json so
// that regenerating keeps the public halves pinned in LicenseKeys.TEST; they sign nothing real.
// See docs/license_architecture.md §4.

import { createHash, createHmac, createPrivateKey, createPublicKey, generateKeyPairSync, sign } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const keysFile = join(here, 'test-keys.json');
const outFile = join(here, '..', '..', 'app', 'src', 'test', 'resources', 'license', 'golden.json');

const KEY_NAMES = ['test-a', 'test-b', 'rogue', 'device'];

function loadKeys() {
  if (existsSync(keysFile)) {
    const stored = JSON.parse(readFileSync(keysFile, 'utf8')).keys;
    return Object.fromEntries(KEY_NAMES.map((name) => [name, createPrivateKey(stored[name])]));
  }
  const keys = Object.fromEntries(
    KEY_NAMES.map((name) => [name, generateKeyPairSync('ec', { namedCurve: 'P-256' }).privateKey]),
  );
  const pem = Object.fromEntries(KEY_NAMES.map((name) => [name, keys[name].export({ type: 'pkcs8', format: 'pem' })]));
  const note = 'TEST KEYS ONLY. They sign the golden tokens and debug builds trust test-a and test-b. Never a real license.';
  writeFileSync(keysFile, JSON.stringify({ note, keys: pem }, null, 2) + '\n');
  return keys;
}

const b64url = (bytes) => Buffer.from(bytes).toString('base64url');
const spki = (privateKey) => createPublicKey(privateKey).export({ type: 'spki', format: 'der' });
const keyHash = (privateKey) => b64url(createHash('sha256').update(spki(privateKey)).digest());
const seconds = (iso) => Math.floor(Date.parse(iso) / 1000);
const DAY = 24 * 60 * 60;

const keys = loadKeys();

// A trial taken on 10 October 2026, checked in at 09:00 in Algiers. It ends at the end of 9 November in
// Algiers (22:59:59 UTC), with three days of grace; the phone must check in again within 14 days.
const base = {
  iss: 'distrigo-license',
  sub: '0b6f4c1e-5d2a-4f7e-9c3b-1a2d3e4f5a6b',
  org: '9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d',
  dev: keyHash(keys.device),
  iid: '3f6c1a2e-8b4d-4c1e-9a7b-2d5e6f708192',
  pkg: 'com.distrigo.app',
  plan: 'trial',
  feat: [],
  vf: seconds('2026-10-10T07:55:00Z'),
  vt: seconds('2026-11-09T22:59:59Z'),
  gu: seconds('2026-11-12T22:59:59Z'),
  iat: seconds('2026-10-10T08:00:00Z'),
  mb: 30,
  seq: 7,
};
base.ou = Math.min(base.iat + 14 * DAY, base.gu);

// The same subscription checked in again on 5 November: its offline window now runs to the end of grace.
const late = { ...base, iat: seconds('2026-11-05T08:00:00Z'), seq: 8 };
late.ou = Math.min(late.iat + 14 * DAY, late.gu);

const TYPE = 'distrigo-license+jwt';
const header = (kid = 'test-a', extra = {}) => ({ alg: 'ES256', kid, typ: TYPE, ...extra });

function jws(head, claims, { key = null, encoding = 'ieee-p1363' } = {}) {
  const input = `${b64url(JSON.stringify(head))}.${b64url(typeof claims === 'string' ? claims : JSON.stringify(claims))}`;
  const signature = key ? sign('sha256', Buffer.from(input), { key, dsaEncoding: encoding }) : Buffer.alloc(0);
  return `${input}.${b64url(signature)}`;
}

const signed = (claims, kid = 'test-a', key = keys['test-a']) => jws(header(kid), claims, { key });

const valid = signed(base);
const [validHeader, , validSignature] = valid.split('.');
const without = (claim) => Object.fromEntries(Object.entries(base).filter(([name]) => name !== claim));

// The classic JWT forgery: alg HS256, "verified" with the public key as the HMAC secret.
const hsInput = `${b64url(JSON.stringify({ alg: 'HS256', kid: 'test-a', typ: TYPE }))}.${b64url(JSON.stringify(base))}`;
const publicPem = createPublicKey(keys['test-a']).export({ type: 'spki', format: 'pem' });
const hs256 = `${hsInput}.${b64url(createHmac('sha256', publicPem).update(hsInput).digest())}`;

const tokens = {
  // Accepted
  valid,
  valid_test_b: signed(base, 'test-b', keys['test-b']),
  late: signed(late),
  older_seq: signed({ ...base, seq: 6 }),
  extra_claims: signed({ ...base, feat: ['rapports-pro'], zzz: 'a claim from a later server' }),
  // Refused before the signature
  two_parts: valid.split('.').slice(0, 2).join('.'),
  four_parts: `${valid}.AAAA`,
  bad_base64: `${validHeader}.e30!.${validSignature}`,
  header_not_json: `${b64url('not json')}.${b64url(JSON.stringify(base))}.${validSignature}`,
  alg_none: jws({ alg: 'none', kid: 'test-a', typ: TYPE }, base),
  alg_hs256: hs256,
  wrong_typ: jws(header('test-a', { typ: 'JWT' }), base, { key: keys['test-a'] }),
  no_typ: jws({ alg: 'ES256', kid: 'test-a' }, base, { key: keys['test-a'] }),
  no_kid: jws({ alg: 'ES256', typ: TYPE }, base, { key: keys['test-a'] }),
  unknown_kid: signed(base, 'rogue', keys.rogue),
  // Refused by the signature
  kid_swapped: signed(base, 'test-a', keys['test-b']),
  tampered_payload: `${validHeader}.${b64url(JSON.stringify({ ...base, vt: base.vt + 365 * DAY }))}.${validSignature}`,
  der_signature: jws(header(), base, { key: keys['test-a'], encoding: 'der' }),
  truncated_signature: valid.slice(0, -4),
  // Signed, but its claims are refused
  payload_not_object: signed('[1,2,3]'),
  missing_claim: signed(without('dev')),
  claim_wrong_type: signed({ ...base, vt: '2026-11-09' }),
  wrong_issuer: signed({ ...base, iss: 'someone-else' }),
  other_package: signed({ ...base, pkg: 'com.distrigo.app.debug' }),
  other_installation: signed({ ...base, iid: 'c0ffee00-0000-4000-8000-000000000000' }),
  other_device: signed({ ...base, dev: keyHash(keys.rogue) }),
};

const golden = {
  note: 'Generated by tools/license/golden-tokens.mjs. Do not edit; regenerate.',
  trusted: {
    'test-a': spki(keys['test-a']).toString('base64'),
    'test-b': spki(keys['test-b']).toString('base64'),
  },
  device: { spki: spki(keys.device).toString('base64'), hash: base.dev },
  claims: base,
  late_claims: late,
  tokens,
};

mkdirSync(dirname(outFile), { recursive: true });
writeFileSync(outFile, JSON.stringify(golden, null, 2) + '\n');
console.log(`${Object.keys(tokens).length} tokens → ${outFile}`);
console.log(`test-a ${golden.trusted['test-a']}`);
console.log(`test-b ${golden.trusted['test-b']}`);

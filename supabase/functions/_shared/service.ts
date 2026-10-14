// The operations behind the Edge Functions (docs/license_architecture.md §6.3, docs/activation_codes.md). The
// rules that must hold under concurrency are in SQL (private.*); this file does the cryptography and composes the
// SQL calls in one transaction. Everything it needs comes in through Config, so the Node tests run it against a
// local PostgreSQL exactly as Supabase runs it.

import type { JSONValue, Sql, TransactionSql } from "npm:postgres@3.4.5";
import { inspect } from "./attestation.ts";
import { base64Decode, base64UrlEncode, signLicense, verifyDeviceSignature } from "./jws.ts";
import { judge } from "./policy.ts";
import {
    BadRequest, bytes, chain, optionalBoolean, optionalChoice, optionalObject, optionalText, optionalUuid, reply,
    type Reply, signedMessage, uuid,
} from "./protocol.ts";
import type { RevocationList } from "./revocation.ts";

export interface Config {
    sql: Sql;
    /** The license signing key, and the id under which the app pins its public half. */
    signingKey: CryptoKey;
    keyId: string;
    packageName: string;
    /** SHA-256 of the app's signing certificates, hex. */
    signingDigests: string[];
    /** Google's root keys; the tests may pass others. */
    roots?: Uint8Array[];
    revocation: () => Promise<RevocationList>;
    now?: () => Date;
}

/** Who asks, beyond the account: the connection's address, which failed codes are also counted against. */
export interface Client {
    ip: string | null;
}

const NO_CLIENT: Client = { ip: null };

/** The business's details the e-mail sign-up sends: the values public.businesses' checks accept. */
const ACTIVITIES = ["alimentation", "boissons", "frais", "hygiene", "droguerie", "materiaux", "pharmacie", "autre"] as const;
const SALES_REPS = ["1", "2-5", "6-10", "10+"] as const;

type Body = Record<string, unknown>;

const hex = (bytes: Uint8Array) => [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
const fromHex = (text: string) => Uint8Array.from(text.match(/../g) ?? [], (pair) => parseInt(pair, 16));

/** Ends a transaction, rolled back, with this reply. */
class Rollback extends Error {
    reply: Reply;
    constructor(answer: Reply) {
        super("rollback");
        this.reply = answer;
    }
}

/** The SQL functions' own refusals, raised as exceptions (private.issue_license). */
function refusal(error: unknown): string | null {
    const message = (error as { message?: unknown })?.message;
    return message === "subscription_inactive" || message === "device_not_active" ? message : null;
}

async function transaction(config: Config, work: (tx: TransactionSql) => Promise<Reply>): Promise<Reply> {
    try {
        return await config.sql.begin(work) as Reply;
    } catch (error) {
        if (error instanceof Rollback) return error.reply;
        const known = refusal(error);
        if (known) return reply({ status: known });
        throw error;
    }
}

/** POST license-nonce {purpose: "activate" | "refresh"} → {nonce} */
export async function createNonce(config: Config, userId: string, body: Body): Promise<Reply> {
    const purpose = body.purpose;
    if (purpose !== "activate" && purpose !== "refresh") throw new BadRequest("purpose");
    const [row] = await config.sql`select encode(private.create_nonce(${userId}::uuid, ${purpose}), 'hex') as nonce`;
    return reply({ status: "ok", nonce: base64UrlEncode(fromHex(row.nonce)) });
}

/**
 * POST license-activate. The phone made its device key with the nonce as attestation challenge, and signs
 * signedMessage("activate", …) with it.
 *
 * Replies activated (with the license), or what the phone must do: no_business (send business_name),
 * choose_business, seat_taken (ask, then send transfer), transfer_limit, refused, bad_signature, bad_nonce.
 * Every answer but activated rolls back, so the same nonce and attestation serve the phone's next attempt.
 *
 * With activation_code (docs/activation_codes.md), the code decides the business instead, in the same
 * transaction: a new code makes it (with business_name and contact_phone when the code names none), a key
 * joins it, and either way the phone is then activated in it, or nothing happens. More replies: bad_code,
 * code_expired, code_used, code_needs_business (send business_name and contact_phone), too_many_attempts
 * (with retry_at). activated then says what the code did: code = created, joined or renewed.
 */
export async function activate(config: Config, userId: string, body: Body, client: Client = NO_CLIENT): Promise<Reply> {
    const nonce = bytes(body, "nonce", 64);
    const installationId = uuid(body, "installation_id");
    const certificates = chain(body, "certificate_chain");
    const signature = bytes(body, "signature", 256);
    const androidId = optionalText(body, "android_id", 64);
    const model = optionalText(body, "model", 80);
    const osVersion = optionalText(body, "os_version", 20);
    const appVersion = optionalText(body, "app_version", 40);
    const businessName = optionalText(body, "business_name", 120);
    const businessId = optionalUuid(body, "business_id");
    const transfer = optionalBoolean(body, "transfer");
    const replaceDevice = optionalUuid(body, "replace_device");
    const activationCode = optionalText(body, "activation_code", 24);
    const contactPhone = optionalText(body, "contact_phone", 30);
    const activity = optionalChoice(body, "business_activity", ACTIVITIES);
    const salesReps = optionalChoice(body, "business_sales_reps", SALES_REPS);

    // Before any work: a phone, account or connection that keeps getting codes refused waits.
    if (activationCode !== null) {
        const [{ gate }] = await config.sql`
            select private.code_attempts_gate(${installationId}::uuid, ${userId}::uuid, ${client.ip}::text) as gate`;
        if (gate.blocked) return reply({ status: "too_many_attempts", retry_at: gate.retry_at });
    }

    const revocation = await config.revocation();
    const attestation = await inspect(certificates, {
        challenge: nonce,
        packageName: config.packageName,
        signingDigests: config.signingDigests,
        roots: config.roots,
        revoked: revocation.entries,
        now: config.now?.(),
    });
    if (attestation.keySpki === null || attestation.keyHash === null) return reply({ status: "refused", reason: "unreadable_key" });
    // Proof the phone holds the key now, attested or not.
    if (!(await verifyDeviceSignature(attestation.keySpki, signedMessage("activate", nonce, installationId), signature))) {
        return reply({ status: "bad_signature" });
    }
    const verdict = judge(attestation);
    if (!verdict.accept) return reply({ status: "refused", reason: verdict.reason });

    const summary = {
        google_chain: attestation.googleChain,
        attested: attestation.attested,
        version: attestation.attestationVersion,
        security_level: attestation.securityLevel,
        boot_state: attestation.verifiedBootState,
        device_locked: attestation.deviceLocked,
        os_patch_level: attestation.osPatchLevel,
        revoked: attestation.revoked,
        expired: attestation.expired,
        revocation_checked: revocation.fresh,
        reasons: verdict.reasons,
        problems: attestation.problems,
    };
    const keySpki = attestation.keySpki;
    const keyHash = attestation.keyHash;

    const answer = await transaction(config, async (tx) => {
        const [{ ok }] = await tx`select private.consume_nonce(decode(${hex(nonce)}, 'hex'), ${userId}::uuid, 'activate') as ok`;
        if (!ok) throw new Rollback(reply({ status: "bad_nonce" }));
        let business = businessId;
        let trial: boolean | null = null;
        let codeUse: string | null = null;
        if (activationCode !== null) {
            const [{ redeemed }] = await tx`
                select private.redeem_code(${userId}::uuid, ${activationCode}, ${businessName}, ${contactPhone}) as redeemed`;
            if (!["created", "joined", "renewed"].includes(redeemed.status)) throw new Rollback(reply(redeemed));
            business = redeemed.business_id;
            codeUse = redeemed.status;
            if (codeUse === "created") trial = redeemed.plan === "trial";
        } else if (businessName !== null && businessId === null) {
            const [{ created }] = await tx`
                select private.create_business(${userId}::uuid, ${businessName}, ${androidId}, ${contactPhone}, ${activity},
                                               ${salesReps}) as created`;
            if (created.status === "created") trial = created.trial;
        }
        const [{ result }] = await tx`
            select private.activate_device(
                ${userId}::uuid, ${business}::uuid, ${installationId}::uuid, ${keyHash}, decode(${hex(keySpki)}, 'hex'),
                ${androidId}, ${model}, ${osVersion}, ${appVersion}, ${tx.json(summary)}::jsonb, ${verdict.tier},
                ${transfer}, ${replaceDevice}::uuid) as result`;
        if (result.status !== "activated") throw new Rollback(reply(result));
        const [{ claims }] = await tx`select private.issue_license(${result.device_id}::uuid, ${config.packageName}) as claims`;
        return reply({
            status: "activated",
            license: await signLicense(claims, config.signingKey, config.keyId),
            device_id: result.device_id,
            business_id: claims.org,
            trial,
            tier: verdict.tier,
            reasons: verdict.reasons,
            ...(codeUse !== null ? { code: codeUse } : {}),
        });
    });
    // Counted after the rollback, which would otherwise take the count with it
    if (activationCode !== null && answer.body.status === "bad_code") {
        await config.sql`select private.record_code_failure(${installationId}::uuid, ${userId}::uuid, ${client.ip}::text)`;
    }
    return answer;
}

/**
 * POST code-redeem {code, business_id?}: « Saisir un code » in Compte. A new code adds its days to the account's
 * business (docs/activation_codes.md); the phone then checks in for the renewed license. Replies renewed
 * {business_id, valid_to, plan, days}, or too_many_attempts (retry_at), bad_code, code_expired, code_used,
 * no_business, choose_business, not_member.
 */
export async function renewWithCode(config: Config, userId: string, body: Body, client: Client = NO_CLIENT): Promise<Reply> {
    const code = optionalText(body, "code", 24);
    if (code === null) throw new BadRequest("code");
    const businessId = optionalUuid(body, "business_id");
    const [{ gate }] = await config.sql`
        select private.code_attempts_gate(${null}::uuid, ${userId}::uuid, ${client.ip}::text) as gate`;
    if (gate.blocked) return reply({ status: "too_many_attempts", retry_at: gate.retry_at });
    const [{ result }] = await config.sql`select private.renew_with_code(${userId}::uuid, ${code}, ${businessId}::uuid) as result`;
    if (result.status === "bad_code") {
        await config.sql`select private.record_code_failure(${null}::uuid, ${userId}::uuid, ${client.ip}::text)`;
    }
    return reply(result);
}

/**
 * POST license-refresh: the check-in. The phone signs signedMessage("refresh", …) with its device key.
 * Replies ok (with the next license), or revoked, not_member, unknown_device, subscription_inactive (all of which
 * the phone takes as the end of its license), bad_signature, bad_nonce.
 */
export async function refresh(config: Config, userId: string, body: Body): Promise<Reply> {
    const nonce = bytes(body, "nonce", 64);
    const installationId = uuid(body, "installation_id");
    const signature = bytes(body, "signature", 256);
    const appVersion = optionalText(body, "app_version", 40);
    const signals = optionalObject(body, "signals");

    return transaction(config, async (tx) => {
        const [{ ok }] = await tx`select private.consume_nonce(decode(${hex(nonce)}, 'hex'), ${userId}::uuid, 'refresh') as ok`;
        if (!ok) throw new Rollback(reply({ status: "bad_nonce" }));
        const [{ device }] = await tx`select private.device_for_check_in(${userId}::uuid, ${installationId}::uuid) as device`;
        if (device.status !== "ok") return reply({ status: device.status });
        const spki = base64Decode(device.key_spki);
        if (!(await verifyDeviceSignature(spki, signedMessage("refresh", nonce, installationId), signature))) {
            throw new Rollback(reply({ status: "bad_signature" }));
        }
        await tx`select private.record_check_in(${device.device_id}::uuid, ${null}::text, ${tx.json(signals as JSONValue)}::jsonb, ${appVersion})`;
        const [{ claims }] = await tx`select private.issue_license(${device.device_id}::uuid, ${config.packageName}) as claims`;
        return reply({ status: "ok", license: await signLicense(claims, config.signingKey, config.keyId) });
    });
}

/** POST device-release {device_id} → released, not_allowed, unknown_device */
export async function release(config: Config, userId: string, body: Body): Promise<Reply> {
    const deviceId = uuid(body, "device_id");
    const [{ result }] = await config.sql`select private.release_device(${userId}::uuid, ${deviceId}::uuid) as result`;
    return reply(result);
}

/** POST account-delete → deleted, or transfer_ownership_first. The data goes first, then the auth user. */
export async function deleteAccount(
    config: Config,
    userId: string,
    deleteAuthUser: (userId: string) => Promise<void>,
): Promise<Reply> {
    const [{ result }] = await config.sql`select private.delete_account_data(${userId}::uuid) as result`;
    if (result.status !== "ok") return reply(result);
    await deleteAuthUser(userId);
    return reply({ status: "deleted" });
}

/**
 * POST business-profile: « Informations de l'entreprise » in Compte (docs/activation_codes.md, step C5). Without
 * a field it reads; with any of name, contact_phone, activity, sales_reps it changes those (a null or blank
 * contact_phone clears it). Replies ok {business_id, name, contact_phone, activity, sales_reps, can_edit}, or
 * no_business, choose_business, not_member, not_allowed (an agent changing it).
 */
export async function businessProfile(config: Config, userId: string, body: Body): Promise<Reply> {
    const businessId = optionalUuid(body, "business_id");
    const changes: Record<string, string | null> = {};
    if ("name" in body) {
        const name = optionalText(body, "name", 120);
        if (name === null) throw new BadRequest("name");
        changes.name = name;
    }
    if ("contact_phone" in body) changes.contact_phone = optionalText(body, "contact_phone", 30);
    if ("activity" in body) {
        const activity = optionalChoice(body, "activity", ACTIVITIES);
        if (activity === null) throw new BadRequest("activity");
        changes.activity = activity;
    }
    if ("sales_reps" in body) {
        const salesReps = optionalChoice(body, "sales_reps", SALES_REPS);
        if (salesReps === null) throw new BadRequest("sales_reps");
        changes.sales_reps = salesReps;
    }
    const [{ result }] = Object.keys(changes).length === 0
        ? await config.sql`select private.business_profile(${userId}::uuid, ${businessId}::uuid) as result`
        : await config.sql`select private.update_business_profile(${userId}::uuid, ${config.sql.json(changes)}::jsonb, ${businessId}::uuid) as result`;
    return reply(result);
}

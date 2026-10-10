// What the app sends and signs. The app's side is Phase L4; this file is the reference it follows.

import { base64Decode, base64UrlEncode } from "./jws.ts";

/** A request the server cannot make sense of; the field is named in the reply. */
export class BadRequest extends Error {}

/** What a function answers: an HTTP status and a body whose `status` the app switches on. */
export interface Reply {
    status: number;
    body: Record<string, unknown>;
}

export const reply = (body: Record<string, unknown>, status = 200): Reply => ({ status, body });

/**
 * The bytes the device key signs to prove the phone holds it: the purpose, the nonce (base64url) and the
 * installation id, one per line. The purpose keeps an activation's signature from serving as a check-in's.
 */
export function signedMessage(purpose: "activate" | "refresh", nonce: Uint8Array, installationId: string): Uint8Array {
    return new TextEncoder().encode(`distrigo-${purpose}\n${base64UrlEncode(nonce)}\n${installationId}`);
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export function uuid(body: Record<string, unknown>, field: string): string {
    const value = body[field];
    if (typeof value !== "string" || !UUID.test(value.toLowerCase())) throw new BadRequest(field);
    return value.toLowerCase();
}

export function optionalUuid(body: Record<string, unknown>, field: string): string | null {
    return body[field] == null ? null : uuid(body, field);
}

export function bytes(body: Record<string, unknown>, field: string, maxLength = 4096): Uint8Array {
    const value = body[field];
    if (typeof value !== "string" || value.length === 0 || value.length > maxLength) throw new BadRequest(field);
    try {
        return base64Decode(value);
    } catch (_) {
        throw new BadRequest(field);
    }
}

export function chain(body: Record<string, unknown>, field: string): Uint8Array[] {
    const value = body[field];
    if (!Array.isArray(value) || value.length === 0 || value.length > 10) throw new BadRequest(field);
    return value.map((item) => {
        if (typeof item !== "string" || item.length === 0 || item.length > 8192) throw new BadRequest(field);
        try {
            return base64Decode(item);
        } catch (_) {
            throw new BadRequest(field);
        }
    });
}

export function optionalText(body: Record<string, unknown>, field: string, maxLength = 120): string | null {
    const value = body[field];
    if (value == null) return null;
    if (typeof value !== "string" || value.length > maxLength) throw new BadRequest(field);
    return value.trim() === "" ? null : value.trim();
}

export function optionalBoolean(body: Record<string, unknown>, field: string): boolean {
    const value = body[field];
    if (value == null) return false;
    if (typeof value !== "boolean") throw new BadRequest(field);
    return value;
}

export function optionalObject(body: Record<string, unknown>, field: string): Record<string, unknown> {
    const value = body[field];
    if (value == null) return {};
    if (typeof value !== "object" || Array.isArray(value) || JSON.stringify(value).length > 4096) throw new BadRequest(field);
    return value as Record<string, unknown>;
}

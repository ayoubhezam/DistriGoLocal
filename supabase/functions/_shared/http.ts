// The Edge Functions' shell, on Supabase (Deno): who is asking, the configuration from the environment, and
// replies as JSON. The operations themselves are in service.ts.
//
// Secrets (supabase secrets set …):
//   LICENSE_SIGNING_KEY          the license key, PKCS#8 PEM (never in the repository)
//   LICENSE_SIGNING_KID          the id the app pins its public half under: lic-2026-a
//   LICENSE_APP_SIGNING_DIGESTS  SHA-256 of the app's signing certificates, hex, comma-separated
// Provided by Supabase: SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_SERVICE_ROLE_KEY, SUPABASE_DB_URL.

import postgres from "npm:postgres@3.4.5";
import { importSigningKey } from "./jws.ts";
import { BadRequest, type Reply } from "./protocol.ts";
import { revocationList } from "./revocation.ts";
import type { Config } from "./service.ts";

function env(name: string): string {
    const value = Deno.env.get(name);
    if (!value) throw new Error(`${name} is not set`);
    return value;
}

let config: Promise<Config> | null = null;

function loadConfig(): Promise<Config> {
    config ??= (async () => ({
        // prepare: false — the transaction pooler does not keep prepared statements.
        sql: postgres(env("SUPABASE_DB_URL"), { prepare: false, max: 3 }),
        signingKey: await importSigningKey(env("LICENSE_SIGNING_KEY")),
        keyId: env("LICENSE_SIGNING_KID"),
        packageName: Deno.env.get("LICENSE_PACKAGE_NAME") ?? "com.distrigo.app",
        signingDigests: env("LICENSE_APP_SIGNING_DIGESTS").split(",").map((d) => d.trim()).filter((d) => d !== ""),
        revocation: revocationList(),
    }))();
    return config;
}

/** The signed-in user, as Supabase Auth vouches for the bearer token; null without a valid one. */
async function userOf(request: Request): Promise<string | null> {
    const authorization = request.headers.get("authorization");
    if (!authorization?.startsWith("Bearer ")) return null;
    const response = await fetch(`${env("SUPABASE_URL")}/auth/v1/user`, {
        headers: { authorization, apikey: env("SUPABASE_ANON_KEY") },
    });
    if (!response.ok) return null;
    const user = await response.json();
    return typeof user?.id === "string" ? user.id : null;
}

/** Deletes an account from Supabase Auth, which cascades to its profile and memberships. */
export async function deleteAuthUser(userId: string): Promise<void> {
    const key = env("SUPABASE_SERVICE_ROLE_KEY");
    const response = await fetch(`${env("SUPABASE_URL")}/auth/v1/admin/users/${userId}`, {
        method: "DELETE",
        headers: { apikey: key, authorization: `Bearer ${key}` },
    });
    if (!response.ok) throw new Error(`could not delete the auth user: ${response.status}`);
}

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

export function serve(operation: (config: Config, userId: string, body: Record<string, unknown>) => Promise<Reply>) {
    Deno.serve(async (request) => {
        if (request.method !== "POST") return json({ status: "method_not_allowed" }, 405);
        try {
            const userId = await userOf(request);
            if (!userId) return json({ status: "unauthenticated" }, 401);
            let body: unknown;
            try {
                body = await request.json();
            } catch (_) {
                return json({ status: "bad_request", field: "body" }, 400);
            }
            if (typeof body !== "object" || body === null || Array.isArray(body)) {
                return json({ status: "bad_request", field: "body" }, 400);
            }
            const answer = await operation(await loadConfig(), userId, body as Record<string, unknown>);
            return json(answer.body, answer.status);
        } catch (error) {
            if (error instanceof BadRequest) return json({ status: "bad_request", field: error.message }, 400);
            console.error(error);
            return json({ status: "error" }, 500);
        }
    });
}

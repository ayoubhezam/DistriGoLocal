// Google's list of revoked and suspended attestation keys, cached as long as its Cache-Control says (a day).

export const REVOCATION_URL = "https://android.googleapis.com/attestation/status";

export interface RevocationList {
    /** Certificate serial, lowercase hex → REVOKED or SUSPENDED. */
    entries: Map<string, string>;
    /** false when the list could not be fetched: the last one known, or none, is used. */
    fresh: boolean;
}

type Fetch = (url: string) => Promise<Response>;

/** A reader of the list with its own cache. An outage never fails an activation: it is noted instead. */
export function revocationList(fetcher: Fetch = fetch, clock: () => number = Date.now): () => Promise<RevocationList> {
    let cached: { at: number; maxAge: number; entries: Map<string, string> } | null = null;
    return async () => {
        const now = clock();
        if (cached && now - cached.at < cached.maxAge) return { entries: cached.entries, fresh: true };
        try {
            const response = await fetcher(REVOCATION_URL);
            if (!response.ok) throw new Error(`status ${response.status}`);
            const json = await response.json() as { entries?: Record<string, { status?: string }> };
            const entries = new Map<string, string>();
            for (const [serial, entry] of Object.entries(json.entries ?? {})) {
                entries.set(serial.toLowerCase(), entry?.status ?? "REVOKED");
            }
            const maxAge = Number(/max-age=(\d+)/.exec(response.headers.get("cache-control") ?? "")?.[1] ?? 3600);
            cached = { at: now, maxAge: maxAge * 1000, entries };
            return { entries, fresh: true };
        } catch (_) {
            return { entries: cached?.entries ?? new Map(), fresh: false };
        }
    };
}

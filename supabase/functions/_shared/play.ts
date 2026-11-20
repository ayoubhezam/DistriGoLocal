// Google Play Billing (docs/play_billing.md; migration 20261118140000_play_billing.sql):
//   play-verify  the app, after a purchase or to restore one: Google is asked, the purchase recorded and acknowledged
//   play-rtdn    Google's Real-time developer notifications (renewed, cancelled, refunded…), pushed by Pub/Sub
// Either way the purchase is read from Google, never taken from the caller's word.

import type { Sql } from "npm:postgres@3.4.5";
import type { PlayApi, PlaySubscription } from "./google.ts";
import { reply, type Reply } from "./protocol.ts";

type Body = Record<string, unknown>;

/**
 * What can be bought. Product IDs are permanent in the Play Console: Solo now; Business later as one product per
 * number of phones (distroniq_business_5, _10, _20), since Play subscriptions are not bought in quantities.
 */
export const CATALOGUE: Record<string, { plan: string; basePlans: Record<string, number> }> = {
    distroniq_solo: { plan: "solo", basePlans: { "solo-monthly": 30, "solo-yearly": 365 } },
};

const ENTITLED = new Set(["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"]);

export interface PlayConfig {
    sql: Sql;
    packageName: string;
    /** Null until the Play Console exists and PLAY_SERVICE_ACCOUNT_JSON is set: the functions then say so. */
    play?: PlayApi | null;
}

function text(body: Body, field: string, max: number): string | null {
    const value = body[field];
    return typeof value === "string" && value.length >= 1 && value.length <= max ? value : null;
}

/** The plan and the days a purchase pays for, or null for a product or base plan we do not sell. */
function priced(sub: PlaySubscription): { plan: string; days: number } | null {
    const product = CATALOGUE[sub.productId];
    const days = product?.basePlans[sub.basePlanId];
    return product && days ? { plan: product.plan, days } : null;
}

/** Writes what Google said, then acknowledges an entitled purchase not yet acknowledged. */
async function record(config: PlayConfig, play: PlayApi, token: string, sub: PlaySubscription,
                      price: { plan: string; days: number }, business: string | null, user: string | null) {
    const [{ result }] = await config.sql`
        select private.record_play_subscription(
            ${token}, ${business}::uuid, ${user}::uuid, ${sub.productId}, ${sub.basePlanId}, ${price.plan}, ${price.days},
            ${sub.state}, ${sub.expiresAt}, ${sub.linkedPurchaseToken}, ${sub.latestOrderId}, ${sub.acknowledged}
        ) as result`;
    if (result.status === "recorded" && ENTITLED.has(sub.state) && !sub.acknowledged) {
        await play.acknowledge(sub.productId, token);
        await config.sql`select private.play_acknowledged(${token})`;
    }
    return result as { status: string; valid_to?: string | null };
}

/**
 * POST play-verify {purchase_token, product_id} → ok {state, valid_to}, or:
 *   billing_unavailable (503)  Google's credentials are not set yet (no Play Console)
 *   email_required             an account made by a code: it must add an e-mail first, or the purchase dies with it
 *   no_business                the account owns no business to pay for
 *   unknown_purchase           Google knows no such purchase
 *   unknown_product            not a product or base plan we sell, or not the one the app named
 *   other_account              the purchase was made for another business (its obfuscated account id)
 *   other_business             the purchase is already recorded for another business
 */
export async function playVerify(config: PlayConfig, userId: string, body: Body): Promise<Reply> {
    const play = config.play;
    if (!play) return reply({ status: "billing_unavailable" }, 503);
    const token = text(body, "purchase_token", 1000);
    const productId = text(body, "product_id", 100);
    if (!token) return reply({ status: "bad_request", field: "purchase_token" }, 400);
    if (!productId) return reply({ status: "bad_request", field: "product_id" }, 400);

    const [user] = await config.sql`select is_anonymous from auth.users where id = ${userId}::uuid`;
    if (!user || user.is_anonymous) return reply({ status: "email_required" });
    const [owned] = await config.sql`
        select m.business_id, private.play_account_id(m.business_id) as account_id
        from public.memberships m
        where m.user_id = ${userId}::uuid and m.role = 'owner' and m.status = 'active'
        order by m.created_at limit 1`;
    if (!owned) return reply({ status: "no_business" });

    const sub = await play.subscription(token);
    if (!sub) return reply({ status: "unknown_purchase" });
    const price = priced(sub);
    if (!price || sub.productId !== productId) return reply({ status: "unknown_product" });
    if (sub.accountId !== owned.account_id) return reply({ status: "other_account" });

    const result = await record(config, play, token, sub, price, owned.business_id, userId);
    if (result.status !== "recorded") return reply({ status: result.status });
    return reply({ status: "ok", state: sub.state, valid_to: result.valid_to ?? null });
}

/**
 * One Real-time developer notification, as decoded from the Pub/Sub message's data. Answers what was done; every
 * answer is a 200 to Pub/Sub (a failure throws, and Pub/Sub sends the message again).
 *   ignored         another package, or a kind we do not use (one-time products)
 *   test            the Console's « Send test notification »
 *   unknown_purchase / unknown_product / unknown_token / other_business / recorded
 */
export async function playNotification(config: PlayConfig, notification: Body): Promise<Reply> {
    const play = config.play;
    if (!play) return reply({ status: "billing_unavailable" }, 503);
    if (notification.packageName !== config.packageName) return reply({ status: "ignored" });
    if (notification.testNotification) return reply({ status: "test" });

    // A renewal, a cancellation… or a voided (refunded, charged back) purchase: either way Google is asked again
    const subscription = notification.subscriptionNotification as Body | undefined;
    const voided = notification.voidedPurchaseNotification as Body | undefined;
    const token = typeof subscription?.purchaseToken === "string" ? subscription.purchaseToken
        : voided && voided.productType === 1 && typeof voided.purchaseToken === "string" ? voided.purchaseToken
        : null;
    if (!token) return reply({ status: "ignored" });

    const sub = await play.subscription(token);
    if (!sub) return reply({ status: "unknown_purchase" });
    const price = priced(sub);
    if (!price) return reply({ status: "unknown_product" });
    // A purchase nobody verified yet still finds its business by the hashed id the app put in it
    const [named] = sub.accountId
        ? await config.sql`select private.play_business_of(${sub.accountId}) as business_id`
        : [{ business_id: null }];
    const result = await record(config, play, token, sub, price, named?.business_id ?? null, null);
    return reply({ status: result.status });
}

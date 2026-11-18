-- Google Play Billing, the database side (Solo release point 5; docs/play_billing.md). Prepared before the Play Console
-- exists: nothing here runs until the Edge Functions play-verify and play-rtdn are given Google's credentials.
--
--   * private.play_subscriptions: one row per Play purchase token, as Google last described it.
--   * A license runs to the LATER of the subscription's own end (codes, manual extensions, the trial) and an entitled
--     Play subscription's expiry (owner, 2026-11-18: the later end wins, no stacking). A refund or a cancellation
--     then only shortens the Play part; a code's days are never touched.
--   * private.record_play_subscription: what the Edge Functions call after asking Google.

create table private.play_subscriptions (
    purchase_token         text primary key check (length(purchase_token) between 1 and 1000),
    business_id            uuid references public.businesses (id) on delete set null,
    -- Who bought it, when the app said so (play-verify); a notification alone does not know
    user_id                uuid references auth.users (id) on delete set null,
    product_id             text not null,
    base_plan_id           text not null,
    plan_id                text not null references public.plans (id),
    -- Google's subscriptionState (SUBSCRIPTION_STATE_…), or SUPERSEDED when a newer token replaced this one
    state                  text not null,
    expires_at             timestamptz,
    linked_purchase_token  text,
    latest_order_id        text,
    acknowledged           boolean not null default false,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now()
);
create index play_subscriptions_business on private.play_subscriptions (business_id);

-- The states in which Google still owes the user the service, until expires_at. CANCELED is « will not renew »:
-- paid time still runs. ON_HOLD, PAUSED, EXPIRED and the PENDING ones give nothing.
create function private.play_state_entitled(p_state text) returns boolean
language sql immutable set search_path = '' as $$
    select p_state in ('SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD', 'SUBSCRIPTION_STATE_CANCELED')
$$;

-- The last local day an entitled Play subscription of p_business covers, or null.
create function private.play_valid_to(p_business uuid) returns date
language sql stable security definer set search_path = '' as $$
    select max((p.expires_at at time zone b.time_zone)::date)
    from private.play_subscriptions p
    join public.businesses b on b.id = p.business_id
    where p.business_id = p_business and private.play_state_entitled(p.state) and p.expires_at > now()
$$;

-- What the app's purchase carries as obfuscatedAccountId: the business, hashed, so Google never holds our id.
create function private.play_account_id(p_business uuid) returns text
language sql immutable set search_path = '' as $$
    select encode(extensions.digest(p_business::text, 'sha256'), 'hex')
$$;

-- The business a purchase's obfuscatedAccountId names, or null.
create function private.play_business_of(p_account_id text) returns uuid
language sql stable security definer set search_path = '' as $$
    select b.id from public.businesses b where private.play_account_id(b.id) = p_account_id
$$;

-- The license, as before, but its last day is the later of the subscription's and the Play subscription's.
create or replace function private.issue_license(p_device uuid, p_package text default 'com.distrigo.app') returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_device   public.devices;
    v_sub      public.subscriptions;
    v_plan     public.plans;
    v_zone     text;
    v_iat      bigint := floor(extract(epoch from now()))::bigint;
    v_vf       bigint;
    v_vt       bigint;
    v_gu       bigint;
    v_ou       bigint;
    v_days     int;
    v_seq      bigint;
    v_last_day date;
begin
    select * into v_device from public.devices d where d.id = p_device for update;
    if v_device.id is null or v_device.status <> 'active' then
        raise exception 'device_not_active' using errcode = 'P0001';
    end if;
    select * into v_sub from public.subscriptions s where s.business_id = v_device.business_id;
    if v_sub.status <> 'active' then
        raise exception 'subscription_inactive' using errcode = 'P0001';
    end if;
    select * into v_plan from public.plans p where p.id = v_sub.plan_id;
    select b.time_zone into v_zone from public.businesses b where b.id = v_device.business_id;

    v_last_day := greatest(v_sub.valid_to, coalesce(private.play_valid_to(v_device.business_id), v_sub.valid_to));
    v_vf := private.day_start(v_sub.valid_from, v_zone);
    v_vt := private.day_end(v_last_day, v_zone);
    v_gu := v_vt + coalesce(v_sub.grace_days, v_plan.grace_days) * 86400;
    v_days := case when v_device.policy_tier = 'reduced' then 1
                   else coalesce(v_device.offline_days, v_sub.offline_days, v_plan.offline_days) end;
    v_ou := least(v_iat + v_days * 86400, v_gu);

    update public.devices d set seq = d.seq + 1, last_seen_at = now() where d.id = p_device returning d.seq into v_seq;
    insert into private.license_issuances (business_id, device_id, seq, issued_at, valid_to, offline_until, policy_tier)
    values (v_device.business_id, p_device, v_seq, to_timestamp(v_iat), to_timestamp(v_vt), to_timestamp(v_ou),
            v_device.policy_tier);

    return jsonb_build_object(
        'iss', 'distrigo-license',
        'sub', v_device.user_id,
        'org', v_device.business_id,
        'dev', v_device.key_hash,
        'iid', v_device.installation_id,
        'pkg', p_package,
        'plan', v_sub.plan_id,
        'feat', to_jsonb(v_plan.features),
        'vf', v_vf,
        'vt', v_vt,
        'gu', v_gu,
        'iat', v_iat,
        'ou', v_ou,
        'mb', coalesce(v_sub.max_reboots, v_plan.max_reboots),
        'seq', v_seq
    );
end
$$;

-- Records what Google says about one purchase token. p_business is the business the purchase belongs to: from the
-- signed-in buyer (play-verify) or from the purchase's obfuscatedAccountId (play-rtdn); null when neither names one,
-- in which case only a token already known is updated.
--   unknown_token     a notification for a purchase nobody has verified yet, naming no business: nothing written
--   other_business    the token is already another business's: nothing written
--   recorded          with valid_to, the license's last day now (null for an unknown business)
-- An entitled purchase also: puts a trial business on the paid plan, ends a refused trial's « no grace », and writes
-- one payments row per Google order (provider google_play, external_ref the order id), so a renewal counts once.
create function private.record_play_subscription(
    p_token         text,
    p_business      uuid,
    p_user          uuid,
    p_product       text,
    p_base_plan     text,
    p_plan          text,
    p_days          int,
    p_state         text,
    p_expires       timestamptz,
    p_linked_token  text,
    p_order_id      text,
    p_acknowledged  boolean
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_known    private.play_subscriptions;
    v_business uuid;
begin
    select * into v_known from private.play_subscriptions p where p.purchase_token = p_token for update;
    if v_known.purchase_token is not null and v_known.business_id is not null and p_business is not null
       and v_known.business_id <> p_business then
        return jsonb_build_object('status', 'other_business');
    end if;
    v_business := coalesce(v_known.business_id, p_business);
    if v_known.purchase_token is null and v_business is null then
        return jsonb_build_object('status', 'unknown_token');
    end if;

    insert into private.play_subscriptions as p (purchase_token, business_id, user_id, product_id, base_plan_id, plan_id,
                                                 state, expires_at, linked_purchase_token, latest_order_id, acknowledged)
    values (p_token, v_business, p_user, p_product, p_base_plan, p_plan, p_state, p_expires, p_linked_token, p_order_id,
            p_acknowledged)
    on conflict (purchase_token) do update
    set business_id = coalesce(p.business_id, excluded.business_id),
        user_id = coalesce(p.user_id, excluded.user_id),
        product_id = excluded.product_id, base_plan_id = excluded.base_plan_id, plan_id = excluded.plan_id,
        state = excluded.state, expires_at = excluded.expires_at,
        linked_purchase_token = coalesce(excluded.linked_purchase_token, p.linked_purchase_token),
        latest_order_id = coalesce(excluded.latest_order_id, p.latest_order_id),
        acknowledged = p.acknowledged or excluded.acknowledged,
        updated_at = now();

    -- A resubscription or a plan change: the token it replaces gives nothing any more
    if p_linked_token is not null then
        update private.play_subscriptions p set state = 'SUPERSEDED', updated_at = now()
        where p.purchase_token = p_linked_token and p.state <> 'SUPERSEDED';
    end if;

    if v_business is not null and private.play_state_entitled(p_state) and p_expires > now() then
        if p_order_id is not null then
            insert into private.payments (business_id, provider, external_ref, days, plan_id, note)
            values (v_business, 'google_play', p_order_id, p_days, p_plan, 'Google Play · ' || p_base_plan)
            on conflict (provider, external_ref) do nothing;
        end if;
        update public.subscriptions s
        set plan_id = case when s.plan_id = 'trial' then p_plan else s.plan_id end,
            grace_days = case when s.note in ('Essai déjà utilisé sur ce téléphone', 'Essai non accordé : téléphone non vérifié')
                                   and s.grace_days = 0 then null
                              else s.grace_days end
        where s.business_id = v_business;
    end if;

    return jsonb_build_object(
        'status', 'recorded',
        'valid_to', (select greatest(s.valid_to, coalesce(private.play_valid_to(v_business), s.valid_to))
                     from public.subscriptions s where s.business_id = v_business)
    );
end
$$;

-- Marks a token acknowledged once Google has been told (play-verify, play-rtdn).
create function private.play_acknowledged(p_token text) returns void
language sql security definer set search_path = '' as $$
    update private.play_subscriptions set acknowledged = true, updated_at = now() where purchase_token = p_token
$$;

-- Only the Edge Functions run them (Postgres grants EXECUTE to public by default)
revoke execute on function private.play_state_entitled(text), private.play_valid_to(uuid), private.play_account_id(uuid),
                           private.play_business_of(text),
                           private.record_play_subscription(text, uuid, uuid, text, text, text, int, text, timestamptz,
                                                            text, text, boolean),
                           private.play_acknowledged(text)
    from public, anon, authenticated;

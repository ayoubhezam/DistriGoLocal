-- DistriGo: accounts, subscriptions and licenses (docs/license_architecture.md §6).
--
-- Built for Solo, designed for Business:
--  - the tenant is the business. Every tenant-owned row carries business_id, even where a join would find
--    it, so each row-level policy is one indexed check;
--  - roles live in memberships (owner now; manager and agent later), never in user metadata, which the user
--    can write;
--  - the subscription belongs to the business, and so do the devices it pays for.
--
-- Two schemas. public is what the app may read through the API, under row-level security. private holds the
-- rest (nonces, issued licenses, transfers, trial claims, the audit log) and the functions the Edge Functions
-- call; the API does not expose it and the app's roles hold no privilege on its tables.
--
-- Business data (clients, sales, stock) is not here: it stays on the phone.

create schema if not exists private;
revoke all on schema private from public;
-- The policies call private.is_member and its siblings as the signed-in user.
grant usage on schema private to authenticated, service_role;
alter default privileges in schema private revoke execute on functions from public;

-- ───────────────────────────── Plans ─────────────────────────────

-- What a plan gives. A subscription may override any of it for one business (D7).
create table public.plans (
    id                   text primary key,
    name                 text not null,
    max_devices          int  not null check (max_devices >= 1),
    offline_days         int  not null check (offline_days between 1 and 365),
    grace_days           int  not null check (grace_days between 0 and 60),
    max_reboots          int  not null check (max_reboots >= 1),
    transfers_per_month  int  not null check (transfers_per_month >= 0),
    trial_days           int  check (trial_days between 1 and 365),
    features             text[] not null default '{}'
);

insert into public.plans (id, name, max_devices, offline_days, grace_days, max_reboots, transfers_per_month, trial_days)
values ('trial', 'Essai', 1, 14, 3, 30, 3, 30),
       ('solo', 'DistriGo Solo', 1, 14, 3, 30, 3, null);

-- ───────────────────────────── The tenant ─────────────────────────────

create table public.businesses (
    id          uuid primary key default gen_random_uuid(),
    name        text not null check (length(trim(name)) between 1 and 120),
    -- Where its days begin and end: a subscription ends at midnight there, never in the middle of a tournée.
    time_zone   text not null default 'Africa/Algiers',
    created_by  uuid references auth.users (id) on delete set null,
    created_at  timestamptz not null default now()
);

-- What another member may see of a user: auth.users is not readable from the app.
create table public.profiles (
    id            uuid primary key references auth.users (id) on delete cascade,
    display_name  text check (length(display_name) <= 80),
    created_at    timestamptz not null default now()
);

create table public.memberships (
    business_id  uuid not null references public.businesses (id) on delete cascade,
    user_id      uuid not null references auth.users (id) on delete cascade,
    role         text not null check (role in ('owner', 'manager', 'agent')),
    status       text not null default 'active' check (status in ('active', 'removed')),
    created_at   timestamptz not null default now(),
    primary key (business_id, user_id)
);
create index memberships_user on public.memberships (user_id);

-- One per business. Days are local to the business: valid through valid_to, inclusive.
create table public.subscriptions (
    business_id   uuid primary key references public.businesses (id) on delete cascade,
    plan_id       text not null references public.plans (id),
    -- suspended or canceled: no license is issued, whatever the dates.
    status        text not null default 'active' check (status in ('active', 'suspended', 'canceled')),
    valid_from    date not null,
    valid_to      date not null,
    -- Overrides of the plan for this business; null takes the plan's value.
    max_devices   int check (max_devices >= 1),
    offline_days  int check (offline_days between 1 and 365),
    grace_days    int check (grace_days between 0 and 60),
    max_reboots   int check (max_reboots >= 1),
    note          text,
    updated_at    timestamptz not null default now()
);

create table public.devices (
    id                uuid primary key default gen_random_uuid(),
    business_id       uuid not null references public.businesses (id) on delete cascade,
    user_id           uuid references auth.users (id) on delete set null,
    -- DeviceIdentity: one row per installation, reused if it activates again.
    installation_id   uuid not null unique,
    -- The license's dev: base64url SHA-256 of the Keystore key's SubjectPublicKeyInfo.
    key_hash          text not null unique,
    key_spki          bytea not null,
    -- Settings.Secure.ANDROID_ID: survives a reinstall on the same phone. A hint, never a check.
    android_id_hint   text,
    model             text,
    os_version        text,
    app_version       text,
    -- What the attestation showed at activation, and the signals of the last check-in.
    attestation       jsonb not null default '{}',
    last_integrity    jsonb not null default '{}',
    -- normal: the plan's offline window; reduced: one day (§5.3).
    policy_tier       text not null default 'normal' check (policy_tier in ('normal', 'reduced')),
    status            text not null default 'active' check (status in ('active', 'released', 'revoked')),
    -- The seq of the last license issued to it.
    seq               bigint not null default 0,
    -- A tester's phone may stay offline longer (D7); null takes the subscription's or the plan's.
    offline_days      int check (offline_days between 1 and 365),
    note              text,
    activated_at      timestamptz not null default now(),
    last_seen_at      timestamptz not null default now(),
    released_at       timestamptz
);
create index devices_business on public.devices (business_id) where status = 'active';
create index devices_user on public.devices (user_id);

-- ───────────────────────────── Private ─────────────────────────────

create table private.nonces (
    value       bytea primary key,
    user_id     uuid not null references auth.users (id) on delete cascade,
    purpose     text not null check (purpose in ('activate', 'refresh')),
    created_at  timestamptz not null default now(),
    expires_at  timestamptz not null,
    used_at     timestamptz
);

create table private.license_issuances (
    id             bigint generated always as identity primary key,
    business_id    uuid not null references public.businesses (id) on delete cascade,
    device_id      uuid not null references public.devices (id) on delete cascade,
    seq            bigint not null,
    issued_at      timestamptz not null,
    valid_to       timestamptz not null,
    offline_until  timestamptz not null,
    policy_tier    text not null,
    unique (device_id, seq)
);

create table private.device_transfers (
    id           bigint generated always as identity primary key,
    business_id  uuid not null references public.businesses (id) on delete cascade,
    from_device  uuid references public.devices (id) on delete set null,
    to_device    uuid references public.devices (id) on delete set null,
    -- false: the same phone reinstalled, which costs no transfer.
    counted      boolean not null,
    at           timestamptz not null default now()
);
create index device_transfers_recent on private.device_transfers (business_id, at) where counted;

-- One trial per phone, kept when the account is deleted, so deleting and signing up again earns none.
create table private.trial_claims (
    android_id_hint  text primary key,
    business_id      uuid references public.businesses (id) on delete set null,
    claimed_at       timestamptz not null default now()
);

-- Every change to a subscription, from a function, a webhook or Supabase Studio.
create table private.subscription_events (
    id           bigint generated always as identity primary key,
    business_id  uuid references public.businesses (id) on delete set null,
    before       jsonb,
    after        jsonb not null,
    changed_by   text not null default current_user,
    user_id      uuid,
    at           timestamptz not null default now()
);

-- Payments from any gateway or by hand. (provider, external_ref) makes a retried webhook harmless.
create table private.payments (
    id            bigint generated always as identity primary key,
    business_id   uuid references public.businesses (id) on delete set null,
    provider      text not null,
    external_ref  text,
    days          int not null check (days > 0),
    plan_id       text not null references public.plans (id),
    note          text,
    recorded_at   timestamptz not null default now(),
    unique (provider, external_ref)
);

-- ───────────────────────────── Helpers ─────────────────────────────

-- Security definer, so a policy can read memberships without recursing into memberships' own policy.
create function private.is_member(p_business uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.memberships m
        where m.business_id = p_business and m.user_id = (select auth.uid()) and m.status = 'active'
    )
$$;

create function private.has_role(p_business uuid, p_roles text[]) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.memberships m
        where m.business_id = p_business and m.user_id = (select auth.uid())
          and m.status = 'active' and m.role = any (p_roles)
    )
$$;

-- Whether the signed-in user and p_user are active members of a common business.
create function private.shares_business(p_user uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.memberships mine
        join public.memberships theirs on theirs.business_id = mine.business_id
        where mine.user_id = (select auth.uid()) and mine.status = 'active'
          and theirs.user_id = p_user and theirs.status = 'active'
    )
$$;

-- Today, where the business is.
create function private.local_today(p_time_zone text) returns date
language sql stable set search_path = '' as $$
    select (now() at time zone p_time_zone)::date
$$;

-- The last second of a local day, as epoch seconds: 9 November in Algiers ends at 22:59:59 UTC.
create function private.day_end(p_day date, p_time_zone text) returns bigint
language sql stable set search_path = '' as $$
    select extract(epoch from (((p_day + 1)::timestamp at time zone p_time_zone) - interval '1 second'))::bigint
$$;

create function private.day_start(p_day date, p_time_zone text) returns bigint
language sql stable set search_path = '' as $$
    select extract(epoch from (p_day::timestamp at time zone p_time_zone))::bigint
$$;

-- ───────────────────────────── Triggers ─────────────────────────────

-- A profile for every account. The name is only a default for display: user metadata is never trusted.
create function private.create_profile() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    insert into public.profiles (id, display_name)
    values (new.id, left(coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name'), 80))
    on conflict (id) do nothing;
    return new;
end
$$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function private.create_profile();

create function private.touch_subscription() returns trigger
language plpgsql set search_path = '' as $$
begin
    new.updated_at := now();
    return new;
end
$$;

create trigger subscriptions_touch
    before update on public.subscriptions
    for each row execute function private.touch_subscription();

create function private.log_subscription() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    insert into private.subscription_events (business_id, before, after, changed_by, user_id)
    values (new.business_id,
            case when tg_op = 'UPDATE' then to_jsonb(old) end,
            to_jsonb(new),
            current_user,
            (select auth.uid()));
    return null;
end
$$;

create trigger subscriptions_log
    after insert or update on public.subscriptions
    for each row execute function private.log_subscription();

-- ───────────────────────────── What the Edge Functions call ─────────────────────────────

-- A single-use challenge, valid five minutes.
create function private.create_nonce(p_user uuid, p_purpose text) returns bytea
language plpgsql security definer set search_path = '' as $$
declare
    v_value bytea := extensions.gen_random_bytes(32);
begin
    delete from private.nonces where expires_at < now() - interval '1 day';
    insert into private.nonces (value, user_id, purpose, expires_at)
    values (v_value, p_user, p_purpose, now() + interval '5 minutes');
    return v_value;
end
$$;

-- True once: for this user and purpose, before it expires. A replay gets false.
create function private.consume_nonce(p_value bytea, p_user uuid, p_purpose text) returns boolean
language sql security definer set search_path = '' as $$
    with used as (
        update private.nonces set used_at = now()
        where value = p_value and user_id = p_user and purpose = p_purpose
          and used_at is null and expires_at > now()
        returning 1
    )
    select exists (select 1 from used)
$$;

-- The business of a new account, with its trial (D7: 30 days), unless this phone already had one.
-- Explicit, never a trigger on sign-up: an agent invited to a business later must not get one of their own.
create function private.create_business(p_user uuid, p_name text, p_android_id_hint text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_existing  uuid;
    v_business  public.businesses;
    v_today     date;
    v_trial     boolean := true;
begin
    perform pg_advisory_xact_lock(hashtext('create_business:' || p_user::text));
    select m.business_id into v_existing
    from public.memberships m
    where m.user_id = p_user and m.status = 'active'
    order by m.created_at
    limit 1;
    if v_existing is not null then
        return jsonb_build_object('status', 'already_member', 'business_id', v_existing);
    end if;

    insert into public.businesses (name, created_by) values (trim(p_name), p_user) returning * into v_business;
    insert into public.memberships (business_id, user_id, role) values (v_business.id, p_user, 'owner');

    if p_android_id_hint is not null then
        insert into private.trial_claims (android_id_hint, business_id) values (p_android_id_hint, v_business.id)
        on conflict (android_id_hint) do nothing;
        v_trial := found;
    end if;

    v_today := private.local_today(v_business.time_zone);
    insert into public.subscriptions (business_id, plan_id, valid_from, valid_to, note)
    select v_business.id, 'trial', v_today,
           case when v_trial then v_today + p.trial_days else v_today - 1 end,
           case when v_trial then null else 'Essai déjà utilisé sur ce téléphone' end
    from public.plans p where p.id = 'trial';

    return jsonb_build_object('status', 'created', 'business_id', v_business.id, 'trial', v_trial);
end
$$;

-- Gives this installation a seat in a business (docs §4.5–4.6). The attestation was checked by the caller,
-- which passes what it found. Returns {status: activated | seat_taken | transfer_limit | no_business |
-- choose_business | not_member}. Activations of one business are serialized on its subscription row.
create function private.activate_device(
    p_user             uuid,
    p_business         uuid,
    p_installation     uuid,
    p_key_hash         text,
    p_key_spki         bytea,
    p_android_id_hint  text,
    p_model            text,
    p_os_version       text,
    p_app_version      text,
    p_attestation      jsonb,
    p_policy_tier      text,
    p_transfer         boolean,
    p_replace          uuid default null
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_business   uuid := p_business;
    v_list       uuid[];
    v_sub        public.subscriptions;
    v_plan       public.plans;
    v_current    public.devices;
    v_freed      public.devices;
    v_active     int;
    v_transfers  int;
    v_counted    boolean;
    v_device     uuid;
begin
    if v_business is null then
        select array_agg(m.business_id) into v_list
        from public.memberships m where m.user_id = p_user and m.status = 'active';
        if coalesce(cardinality(v_list), 0) = 0 then
            return jsonb_build_object('status', 'no_business');
        elsif cardinality(v_list) > 1 then
            return jsonb_build_object('status', 'choose_business');
        end if;
        v_business := v_list[1];
    elsif not exists (select 1 from public.memberships m
                      where m.business_id = v_business and m.user_id = p_user and m.status = 'active') then
        return jsonb_build_object('status', 'not_member');
    end if;

    select * into v_sub from public.subscriptions s where s.business_id = v_business for update;
    select * into v_plan from public.plans p where p.id = v_sub.plan_id;

    select * into v_current from public.devices d where d.installation_id = p_installation for update;

    -- The same installation, active in this business, with a new key (the Keystore lost the old one).
    if v_current.id is not null and v_current.business_id = v_business and v_current.status = 'active' then
        update public.devices d
        set user_id = p_user, key_hash = p_key_hash, key_spki = p_key_spki, android_id_hint = p_android_id_hint,
            model = p_model, os_version = p_os_version, app_version = p_app_version,
            attestation = p_attestation, policy_tier = p_policy_tier, last_seen_at = now()
        where d.id = v_current.id;
        return jsonb_build_object('status', 'activated', 'device_id', v_current.id, 'rekeyed', true);
    end if;

    select count(*) into v_active from public.devices d where d.business_id = v_business and d.status = 'active';

    if v_active >= coalesce(v_sub.max_devices, v_plan.max_devices) then
        -- The same phone, reinstalled: its old installation gives its seat back, and no transfer is counted.
        select * into v_freed from public.devices d
        where d.business_id = v_business and d.status = 'active'
          and p_android_id_hint is not null and d.android_id_hint = p_android_id_hint
          and d.model is not distinct from p_model
        order by d.last_seen_at desc
        limit 1;
        if v_freed.id is not null then
            update public.devices d set status = 'released', released_at = now() where d.id = v_freed.id;
            v_counted := false;
        elsif not p_transfer then
            return jsonb_build_object(
                'status', 'seat_taken',
                'devices', (select jsonb_agg(jsonb_build_object('id', d.id, 'model', d.model, 'last_seen_at', d.last_seen_at)
                                             order by d.last_seen_at desc)
                            from public.devices d where d.business_id = v_business and d.status = 'active'));
        else
            select count(*) into v_transfers from private.device_transfers t
            where t.business_id = v_business and t.counted and t.at > now() - interval '30 days';
            if v_transfers >= v_plan.transfers_per_month then
                return jsonb_build_object(
                    'status', 'transfer_limit',
                    'next_at', (select min(t.at) + interval '30 days' from private.device_transfers t
                                where t.business_id = v_business and t.counted and t.at > now() - interval '30 days'));
            end if;
            -- The device the user chose, or the one seen least recently.
            select * into v_freed from public.devices d
            where d.business_id = v_business and d.status = 'active' and (p_replace is null or d.id = p_replace)
            order by d.last_seen_at asc
            limit 1;
            if v_freed.id is null then
                return jsonb_build_object('status', 'seat_taken');
            end if;
            update public.devices d set status = 'revoked', released_at = now() where d.id = v_freed.id;
            v_counted := true;
        end if;
    end if;

    if v_current.id is not null then
        update public.devices d
        set business_id = v_business, user_id = p_user, key_hash = p_key_hash, key_spki = p_key_spki,
            android_id_hint = p_android_id_hint, model = p_model, os_version = p_os_version,
            app_version = p_app_version, attestation = p_attestation, policy_tier = p_policy_tier,
            status = 'active', activated_at = now(), last_seen_at = now(), released_at = null
        where d.id = v_current.id
        returning d.id into v_device;
    else
        insert into public.devices (business_id, user_id, installation_id, key_hash, key_spki, android_id_hint,
                                    model, os_version, app_version, attestation, policy_tier)
        values (v_business, p_user, p_installation, p_key_hash, p_key_spki, p_android_id_hint,
                p_model, p_os_version, p_app_version, p_attestation, p_policy_tier)
        returning id into v_device;
    end if;

    if v_freed.id is not null then
        insert into private.device_transfers (business_id, from_device, to_device, counted)
        values (v_business, v_freed.id, v_device, v_counted);
    end if;

    return jsonb_build_object('status', 'activated', 'device_id', v_device);
end
$$;

-- What a check-in needs before it may issue: the device of this installation, if this user may still use it.
-- Locks the row until the transaction ends. Returns {status: ok | unknown_device | not_member | revoked, ...}.
create function private.device_for_check_in(p_user uuid, p_installation uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_device public.devices;
begin
    select * into v_device from public.devices d where d.installation_id = p_installation for update;
    if v_device.id is null or v_device.user_id is distinct from p_user then
        return jsonb_build_object('status', 'unknown_device');
    end if;
    if not exists (select 1 from public.memberships m
                   where m.business_id = v_device.business_id and m.user_id = p_user and m.status = 'active') then
        return jsonb_build_object('status', 'not_member');
    end if;
    if v_device.status <> 'active' then
        return jsonb_build_object('status', 'revoked');
    end if;
    return jsonb_build_object('status', 'ok', 'device_id', v_device.id, 'key_spki', encode(v_device.key_spki, 'base64'),
                              'seq', v_device.seq);
end
$$;

-- What the last check-in showed, for the tiers of §5.3 and the list of flagged devices. A null tier keeps the
-- device's: a check-in without new evidence changes nothing, nor undoes a tier set by hand in Studio.
create function private.record_check_in(p_device uuid, p_policy_tier text, p_integrity jsonb, p_app_version text)
returns void
language sql security definer set search_path = '' as $$
    update public.devices d
    set policy_tier = coalesce(p_policy_tier, d.policy_tier), last_integrity = p_integrity,
        app_version = coalesce(p_app_version, d.app_version), last_seen_at = now()
    where d.id = p_device
$$;

-- The claims of the next license for a device (docs §4.1), recorded and ready to sign. Times are epoch
-- seconds: iat is now, vt and gu end a local day, ou is the offline window capped at gu. A subscription past
-- its grace still gets a license, which the phone reads as expired.
create function private.issue_license(p_device uuid, p_package text default 'com.distrigo.app') returns jsonb
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

    v_vf := private.day_start(v_sub.valid_from, v_zone);
    v_vt := private.day_end(v_sub.valid_to, v_zone);
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

-- « Libérer cet appareil »: by its user, or by an owner or manager of its business.
create function private.release_device(p_user uuid, p_device uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_device public.devices;
begin
    select * into v_device from public.devices d where d.id = p_device for update;
    if v_device.id is null then
        return jsonb_build_object('status', 'unknown_device');
    end if;
    if v_device.user_id is distinct from p_user and not exists (
        select 1 from public.memberships m
        where m.business_id = v_device.business_id and m.user_id = p_user
          and m.status = 'active' and m.role in ('owner', 'manager')) then
        return jsonb_build_object('status', 'not_allowed');
    end if;
    update public.devices d set status = 'released', released_at = now() where d.id = p_device and d.status = 'active';
    return jsonb_build_object('status', 'released');
end
$$;

-- Before an account is deleted: the businesses it alone owns go with it (their subscription and devices too).
-- A business with other active members must get another owner first. The caller then deletes the auth user.
create function private.delete_account_data(p_user uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_blocked uuid[];
begin
    select array_agg(m.business_id) into v_blocked
    from public.memberships m
    where m.user_id = p_user and m.role = 'owner' and m.status = 'active'
      and exists (select 1 from public.memberships other
                  where other.business_id = m.business_id and other.user_id <> p_user and other.status = 'active');
    if v_blocked is not null then
        return jsonb_build_object('status', 'transfer_ownership_first', 'businesses', to_jsonb(v_blocked));
    end if;
    delete from public.businesses b
    where b.id in (select m.business_id from public.memberships m
                   where m.user_id = p_user and m.role = 'owner' and m.status = 'active');
    return jsonb_build_object('status', 'ok');
end
$$;

-- A payment, or days given by hand (Supabase Studio: select private.extend_subscription(...)). The days run
-- from the end of the subscription, or from today if it has ended. A webhook passes its provider and
-- reference, so a retry changes nothing.
create function private.extend_subscription(
    p_business      uuid,
    p_days          int,
    p_plan          text default 'solo',
    p_note          text default null,
    p_provider      text default 'manual',
    p_external_ref  text default null
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_sub   public.subscriptions;
    v_zone  text;
    v_from  date;
begin
    select * into v_sub from public.subscriptions s where s.business_id = p_business for update;
    if v_sub.business_id is null then
        return jsonb_build_object('status', 'unknown_business');
    end if;
    insert into private.payments (business_id, provider, external_ref, days, plan_id, note)
    values (p_business, p_provider, p_external_ref, p_days, p_plan, p_note)
    on conflict (provider, external_ref) do nothing;
    if not found then
        return jsonb_build_object('status', 'already_applied', 'valid_to', v_sub.valid_to);
    end if;
    select b.time_zone into v_zone from public.businesses b where b.id = p_business;
    v_from := greatest(v_sub.valid_to, private.local_today(v_zone) - 1);
    update public.subscriptions s
    set plan_id = p_plan, status = 'active', valid_to = v_from + p_days, note = coalesce(p_note, s.note)
    where s.business_id = p_business;
    return jsonb_build_object('status', 'extended', 'valid_to', v_from + p_days);
end
$$;

-- Only the Edge Functions (as postgres or service_role) run the functions above; the policies' helpers
-- are the exception.
revoke execute on all functions in schema private from public, anon, authenticated;
grant execute on function private.is_member(uuid), private.has_role(uuid, text[]), private.shares_business(uuid)
    to authenticated;
revoke all on all tables in schema private from public, anon, authenticated;

-- ───────────────────────────── What the app may read ─────────────────────────────

alter table public.plans         enable row level security;
alter table public.businesses    enable row level security;
alter table public.profiles      enable row level security;
alter table public.memberships   enable row level security;
alter table public.subscriptions enable row level security;
alter table public.devices       enable row level security;

revoke all on public.plans, public.businesses, public.profiles, public.memberships, public.subscriptions,
              public.devices
    from anon, authenticated;

grant select on public.plans to authenticated;
create policy "plans: readable" on public.plans for select to authenticated using (true);

grant select on public.businesses to authenticated;
grant update (name) on public.businesses to authenticated;
create policy "businesses: members read" on public.businesses for select to authenticated
    using ((select private.is_member(id)));
create policy "businesses: owners and managers rename" on public.businesses for update to authenticated
    using ((select private.has_role(id, array['owner', 'manager'])))
    with check ((select private.has_role(id, array['owner', 'manager'])));

grant select on public.profiles to authenticated;
grant update (display_name) on public.profiles to authenticated;
create policy "profiles: self and fellow members read" on public.profiles for select to authenticated
    using (id = (select auth.uid()) or (select private.shares_business(id)));
create policy "profiles: self edits" on public.profiles for update to authenticated
    using (id = (select auth.uid())) with check (id = (select auth.uid()));

grant select on public.memberships to authenticated;
create policy "memberships: members read their business's" on public.memberships for select to authenticated
    using ((select private.is_member(business_id)));

grant select on public.subscriptions to authenticated;
create policy "subscriptions: members read" on public.subscriptions for select to authenticated
    using ((select private.is_member(business_id)));

-- Not the key, the attestation, the installation id or the hints: only what the Compte screen shows.
grant select (id, business_id, user_id, model, os_version, app_version, status, activated_at, last_seen_at)
    on public.devices to authenticated;
create policy "devices: own, or all of the business for owners and managers" on public.devices
    for select to authenticated
    using (user_id = (select auth.uid()) or (select private.has_role(business_id, array['owner', 'manager'])));

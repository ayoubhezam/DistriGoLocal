-- License security, audit 2026-11-09:
--
--  - LIC-03: the live server stops trusting the debug signing certificate for everyone. A build signed with it is
--    accepted only for the accounts in private.test_accounts (the owner's own and his emulator's), filled by hand in
--    Studio. The Edge Function asks private.is_test_account.
--  - LIC-04: a trial needs a device check: an ANDROID_ID and a key Google attests on a phone with a locked,
--    verified boot (the « normal » tier). The Edge Function decides and passes p_trial_allowed; test accounts are
--    exempt. A trial refused this way is like one already used: the license ends at once, with no grace, until a code.
--  - LIC-01: a phone that says it is the same phone reinstalled gets the seat back without a counted transfer only
--    when the old installation has not checked in for 48 hours.

-- ── Test accounts ──
create table private.test_accounts (
    user_id   uuid primary key references auth.users (id) on delete cascade,
    note      text,
    added_at  timestamptz not null default now()
);
revoke all on private.test_accounts from public, anon, authenticated;

create function private.is_test_account(p_user uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (select 1 from private.test_accounts t where t.user_id = p_user)
$$;

-- ── create_business: as 20261117120000_trial_thirty_days.sql, with p_trial_allowed and the reason a trial is refused ──
drop function private.create_business(uuid, text, text, text, text, text);
create or replace function private.create_business(
    p_user             uuid,
    p_name             text,
    p_android_id_hint  text,
    p_contact_phone    text default null,
    p_activity         text default null,
    p_sales_reps       text default null,
    p_trial_allowed    boolean default true
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_existing  uuid;
    v_business  public.businesses;
    v_today     date;
    v_trial     boolean := p_trial_allowed;
    v_refused   text;
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

    insert into public.businesses (name, created_by, contact_phone, activity, sales_reps)
    values (trim(p_name), p_user, nullif(trim(p_contact_phone), ''), p_activity, p_sales_reps)
    returning * into v_business;
    insert into public.memberships (business_id, user_id, role) values (v_business.id, p_user, 'owner');

    if not v_trial then
        -- The Edge Function found no device check to trust: no ANDROID_ID, or a key Google does not vouch for (LIC-04)
        v_refused := 'device_check';
    elsif p_android_id_hint is not null then
        insert into private.trial_claims (android_id_hint, business_id) values (p_android_id_hint, v_business.id)
        on conflict (android_id_hint) do nothing;
        v_trial := found;
        if not v_trial then v_refused := 'already_used'; end if;
    end if;

    v_today := private.local_today(v_business.time_zone);
    insert into public.subscriptions (business_id, plan_id, valid_from, valid_to, note, grace_days)
    select v_business.id, 'trial', v_today,
           -- Both ends count: a 30-day trial runs from today through today + 29 (it was today + 30, 31 days)
           case when v_trial then v_today + p.trial_days - 1 else v_today - 1 end,
           case v_refused when 'device_check' then 'Essai non accordé : téléphone non vérifié'
                          when 'already_used' then 'Essai déjà utilisé sur ce téléphone' end,
           -- No grace after a refused trial: it was never a subscription that ran out
           case when v_trial then null else 0 end
    from public.plans p where p.id = 'trial';

    return jsonb_build_object('status', 'created', 'business_id', v_business.id, 'trial', v_trial,
                              'trial_refused', v_refused);
end
$$;

-- ── extend_subscription: as 20261103120000_refused_trial_no_grace.sql; a trial refused for the device check gets its
-- grace back with the first payment too ──
create or replace function private.extend_subscription(
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
    set plan_id = p_plan, status = 'active', valid_to = v_from + p_days, note = coalesce(p_note, s.note),
        -- A refused trial's « no grace » (create_business) ends with the first payment; any other value stays
        grace_days = case when s.note in ('Essai déjà utilisé sur ce téléphone', 'Essai non accordé : téléphone non vérifié')
                               and s.grace_days = 0 then null
                          else s.grace_days end
    where s.business_id = p_business;
    return jsonb_build_object('status', 'extended', 'valid_to', v_from + p_days);
end
$$;

-- ── activate_device: as 20261010120000_accounts_and_licenses.sql, the reinstalled-phone release after 48 h of silence ──
create or replace function private.activate_device(
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
        -- The same phone, reinstalled: its old installation gives its seat back, and no transfer is counted — but only
        -- once that installation has been silent for 48 hours. android_id and model are sent by the phone and can be
        -- copied (LIC-01, audit 2026-11-09); a working phone checks in every 12 hours, so a silent one is gone.
        select * into v_freed from public.devices d
        where d.business_id = v_business and d.status = 'active'
          and p_android_id_hint is not null and d.android_id_hint = p_android_id_hint
          and d.model is not distinct from p_model
          and d.last_seen_at < now() - interval '48 hours'
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

-- Only the Edge Functions run them, as every function in private (Postgres grants EXECUTE to public by default)
revoke execute on function private.is_test_account(uuid),
                           private.create_business(uuid, text, text, text, text, text, boolean)
    from public, anon, authenticated;

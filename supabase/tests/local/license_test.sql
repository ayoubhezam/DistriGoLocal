-- The accounts and licenses schema, tested on a plain PostgreSQL with supabase_stub.sql: run.sh.
-- Every check prints "ok: …"; the first failure stops the script.

\set ON_ERROR_STOP on
set client_min_messages = notice;

-- ── People ──
-- owner_a  aaaaaaaa-…01   owns business A, on the phone with hint 'phone-a'
-- owner_b  bbbbbbbb-…02   signs up on the same phone: no second trial
-- agent_a  cccccccc-…03   an agent of A (Business, later)
-- solo_d   dddddddd-…04   owns business D, then deletes the account
insert into auth.users (id, email, raw_user_meta_data) values
    ('aaaaaaaa-0000-4000-8000-000000000001', 'a@example.com', '{"full_name": "Ayoub Test"}'),
    ('bbbbbbbb-0000-4000-8000-000000000002', 'b@example.com', '{}'),
    ('cccccccc-0000-4000-8000-000000000003', 'c@example.com', '{"name": "Agent C"}'),
    ('dddddddd-0000-4000-8000-000000000004', 'd@example.com', '{}');

-- Activation as an Edge Function calls it, with the device's details filled in.
create function pg_temp.activate(p_user uuid, p_installation text, p_key text, p_hint text,
                                 p_model text default 'SM-M346B', p_transfer boolean default false,
                                 p_replace uuid default null, p_business uuid default null)
returns jsonb language sql as $$
    select private.activate_device(p_user, p_business, p_installation::uuid, p_key, convert_to(p_key, 'UTF8'),
                                   p_hint, p_model, '16', '1.0', '{"security_level": "tee"}'::jsonb, 'normal',
                                   p_transfer, p_replace)
$$;

create function pg_temp.business_of(p_user uuid) returns uuid language sql as $$
    select business_id from public.memberships where user_id = p_user and role = 'owner'
$$;

create function pg_temp.device(p_installation text) returns uuid language sql as $$
    select id from public.devices where installation_id = p_installation::uuid
$$;

create function pg_temp.counted_transfers(p_business uuid) returns int language sql as $$
    select count(*)::int from private.device_transfers where business_id = p_business and counted
$$;

-- ───────────── Accounts and businesses ─────────────

do $$ begin
    assert (select display_name from public.profiles where id = 'aaaaaaaa-0000-4000-8000-000000000001') = 'Ayoub Test';
    assert (select display_name from public.profiles where id = 'cccccccc-0000-4000-8000-000000000003') = 'Agent C';
    assert (select display_name from public.profiles where id = 'bbbbbbbb-0000-4000-8000-000000000002') is null;
    raise notice 'ok: every account gets a profile, named from the sign-up when it has a name';
end $$;

do $$
declare r jsonb;
begin
    r := private.create_business('aaaaaaaa-0000-4000-8000-000000000001', '  Distribution A  ', 'phone-a');
    assert r ->> 'status' = 'created' and (r ->> 'trial')::boolean, r::text;
    assert (select name from public.businesses where id = (r ->> 'business_id')::uuid) = 'Distribution A';
    assert (select role from public.memberships where user_id = 'aaaaaaaa-0000-4000-8000-000000000001') = 'owner';
    assert (select plan_id = 'trial' and valid_from = private.local_today('Africa/Algiers')
                   and valid_to = private.local_today('Africa/Algiers') + 30
            from public.subscriptions where business_id = (r ->> 'business_id')::uuid);
    raise notice 'ok: a new business starts a 30-day trial, through the 30th day after today';

    r := private.create_business('aaaaaaaa-0000-4000-8000-000000000001', 'Encore', 'phone-a');
    assert r ->> 'status' = 'already_member', r::text;
    assert (select count(*) from public.businesses) = 1;
    raise notice 'ok: an account that already has a business gets no second one';
end $$;

do $$
declare r jsonb;
begin
    r := private.create_business('bbbbbbbb-0000-4000-8000-000000000002', 'Distribution B', 'phone-a');
    assert r ->> 'status' = 'created' and not (r ->> 'trial')::boolean, r::text;
    assert (select valid_to < valid_from from public.subscriptions where business_id = (r ->> 'business_id')::uuid);
    raise notice 'ok: a second account on the same phone gets its business but no second trial';
end $$;

do $$ begin
    assert pg_temp.activate('dddddddd-0000-4000-8000-000000000004', 'e0000000-0000-4000-8000-0000000000d1',
                            'key-d1', 'phone-d') ->> 'status' = 'no_business';
    assert pg_temp.activate('dddddddd-0000-4000-8000-000000000004', 'e0000000-0000-4000-8000-0000000000d1',
                            'key-d1', 'phone-d', p_business => pg_temp.business_of('aaaaaaaa-0000-4000-8000-000000000001'))
           ->> 'status' = 'not_member';
    raise notice 'ok: no seat without a business, nor in a business one is not a member of';
end $$;

-- ───────────── Activation and the license ─────────────

do $$
declare
    r      jsonb;
    c      jsonb;
    a      uuid := pg_temp.business_of('aaaaaaaa-0000-4000-8000-000000000001');
    today  date := private.local_today('Africa/Algiers');
begin
    r := pg_temp.activate('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001', 'key-1', 'phone-a');
    assert r ->> 'status' = 'activated', r::text;
    c := private.issue_license((r ->> 'device_id')::uuid);

    assert c ->> 'iss' = 'distrigo-license' and c ->> 'pkg' = 'com.distrigo.app' and c ->> 'plan' = 'trial', c::text;
    assert c ->> 'sub' = 'aaaaaaaa-0000-4000-8000-000000000001' and (c ->> 'org')::uuid = a, c::text;
    assert c ->> 'dev' = 'key-1' and c ->> 'iid' = 'e0000000-0000-4000-8000-000000000001', c::text;
    assert c -> 'feat' = '[]'::jsonb and (c ->> 'mb')::int = 30 and (c ->> 'seq')::int = 1, c::text;
    -- Whole seconds, as the phone reads them.
    assert (select bool_and(jsonb_typeof(c -> k) = 'number' and (c -> k)::text ~ '^\d+$')
            from unnest(array['vf', 'vt', 'gu', 'iat', 'ou', 'mb', 'seq']) k), c::text;
    -- vf at the start of today, vt at the end of the 30th day after it, in Algiers.
    assert to_char(to_timestamp((c ->> 'vf')::bigint) at time zone 'Africa/Algiers', 'YYYY-MM-DD HH24:MI:SS')
           = to_char(today, 'YYYY-MM-DD') || ' 00:00:00', c::text;
    assert to_char(to_timestamp((c ->> 'vt')::bigint) at time zone 'Africa/Algiers', 'YYYY-MM-DD HH24:MI:SS')
           = to_char(today + 30, 'YYYY-MM-DD') || ' 23:59:59', c::text;
    assert (c ->> 'gu')::bigint - (c ->> 'vt')::bigint = 3 * 86400, c::text;
    assert (c ->> 'ou')::bigint - (c ->> 'iat')::bigint = 14 * 86400, c::text;
    assert abs((c ->> 'iat')::bigint - extract(epoch from now())) < 2, c::text;
    assert (select count(*) from private.license_issuances where device_id = (r ->> 'device_id')::uuid and seq = 1) = 1;
    raise notice 'ok: the first license: the claims the phone verifies, with the trial''s dates and windows';

    c := private.issue_license((r ->> 'device_id')::uuid);
    assert (c ->> 'seq')::int = 2;
    raise notice 'ok: every license has the next seq';
end $$;

do $$
declare
    c  jsonb;
    b  uuid := pg_temp.business_of('bbbbbbbb-0000-4000-8000-000000000002');
    r  jsonb;
begin
    r := pg_temp.activate('bbbbbbbb-0000-4000-8000-000000000002', 'e0000000-0000-4000-8000-0000000000b1', 'key-b1', 'phone-a');
    c := private.issue_license((r ->> 'device_id')::uuid);
    assert (c ->> 'vt')::bigint < (c ->> 'iat')::bigint and (c ->> 'ou')::bigint = (c ->> 'gu')::bigint, c::text;
    raise notice 'ok: a business without a trial still gets a license, one the phone reads as ended';
end $$;

-- ───────────── Seats and transfers (D6) ─────────────

do $$
declare
    a   uuid := pg_temp.business_of('aaaaaaaa-0000-4000-8000-000000000001');
    r   jsonb;
begin
    r := pg_temp.activate('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000002', 'key-2', 'phone-x', 'Redmi Note');
    assert r ->> 'status' = 'seat_taken' and jsonb_array_length(r -> 'devices') = 1
           and r -> 'devices' -> 0 ->> 'model' = 'SM-M346B', r::text;
    raise notice 'ok: a second phone is told which phone holds the seat';

    r := pg_temp.activate('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000002', 'key-2', 'phone-x', 'Redmi Note', true);
    assert r ->> 'status' = 'activated', r::text;
    assert (select status from public.devices where id = pg_temp.device('e0000000-0000-4000-8000-000000000001')) = 'revoked';
    assert (select count(*) from public.devices where business_id = a and status = 'active') = 1;
    assert pg_temp.counted_transfers(a) = 1;
    raise notice 'ok: a transfer revokes the first phone and costs one transfer';

    begin
        perform private.issue_license(pg_temp.device('e0000000-0000-4000-8000-000000000001'));
        assert false, 'a revoked device got a license';
    exception when raise_exception then
        assert sqlerrm = 'device_not_active', sqlerrm;
    end;
    assert private.device_for_check_in('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001') ->> 'status' = 'revoked';
    raise notice 'ok: the revoked phone gets no license, and its check-in learns it was revoked';

    -- Back and forth: the second and third transfers, then the limit.
    assert (pg_temp.activate('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001', 'key-1b', 'phone-a', p_transfer => true)) ->> 'status' = 'activated';
    assert (pg_temp.activate('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000002', 'key-2b', 'phone-x', 'Redmi Note', true)) ->> 'status' = 'activated';
    assert pg_temp.counted_transfers(a) = 3;
    r := pg_temp.activate('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001', 'key-1c', 'phone-a', p_transfer => true);
    assert r ->> 'status' = 'transfer_limit' and (r ->> 'next_at')::timestamptz > now() + interval '29 days', r::text;
    assert (select status from public.devices where id = pg_temp.device('e0000000-0000-4000-8000-000000000002')) = 'active';
    raise notice 'ok: three transfers in 30 days, then the limit, with the date the next one is possible';
end $$;

do $$
declare
    a    uuid := pg_temp.business_of('aaaaaaaa-0000-4000-8000-000000000001');
    r    jsonb;
    old  uuid := pg_temp.device('e0000000-0000-4000-8000-000000000002');
begin
    -- The Redmi reinstalls DistriGo: a new installation id, the same ANDROID_ID and model.
    r := pg_temp.activate('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000003', 'key-3', 'phone-x', 'Redmi Note');
    assert r ->> 'status' = 'activated', r::text;
    assert (select status from public.devices where id = old) = 'released';
    assert pg_temp.counted_transfers(a) = 3;
    assert (select count(*) from private.device_transfers where business_id = a and not counted and from_device = old) = 1;
    raise notice 'ok: the same phone reinstalled takes its seat back without a transfer, even at the limit';

    -- Its Keystore loses the key: the same installation activates again with a new one.
    assert (private.issue_license((r ->> 'device_id')::uuid) ->> 'seq')::int = 1;
    r := pg_temp.activate('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000003', 'key-3b', 'phone-x', 'Redmi Note');
    assert r ->> 'status' = 'activated' and (r ->> 'rekeyed')::boolean, r::text;
    assert (select key_hash from public.devices where id = (r ->> 'device_id')::uuid) = 'key-3b';
    assert (private.issue_license((r ->> 'device_id')::uuid) ->> 'seq')::int = 2;
    assert (select count(*) from public.devices where business_id = a and status = 'active') = 1;
    raise notice 'ok: a new key on the same installation keeps its seat and its seq';
end $$;

-- ───────────── Nonces ─────────────

do $$
declare n bytea;
begin
    n := private.create_nonce('aaaaaaaa-0000-4000-8000-000000000001', 'refresh');
    assert length(n) = 32;
    assert not private.consume_nonce(n, 'bbbbbbbb-0000-4000-8000-000000000002', 'refresh');
    assert not private.consume_nonce(n, 'aaaaaaaa-0000-4000-8000-000000000001', 'activate');
    assert private.consume_nonce(n, 'aaaaaaaa-0000-4000-8000-000000000001', 'refresh');
    assert not private.consume_nonce(n, 'aaaaaaaa-0000-4000-8000-000000000001', 'refresh');
    n := private.create_nonce('aaaaaaaa-0000-4000-8000-000000000001', 'refresh');
    update private.nonces set expires_at = now() - interval '1 second' where value = n;
    assert not private.consume_nonce(n, 'aaaaaaaa-0000-4000-8000-000000000001', 'refresh');
    raise notice 'ok: a nonce serves once, for its user and purpose, before it expires';
end $$;

-- ───────────── Overrides, payments, the audit (D7) ─────────────

do $$
declare
    a   uuid := pg_temp.business_of('aaaaaaaa-0000-4000-8000-000000000001');
    d   uuid := pg_temp.device('e0000000-0000-4000-8000-000000000003');
    c   jsonb;
begin
    update public.subscriptions set grace_days = 7 where business_id = a;
    update public.devices set offline_days = 60 where id = d;
    c := private.issue_license(d);
    assert (c ->> 'gu')::bigint - (c ->> 'vt')::bigint = 7 * 86400, c::text;
    assert (c ->> 'ou')::bigint = (c ->> 'gu')::bigint, c::text;
    update public.devices set offline_days = null, policy_tier = 'reduced' where id = d;
    c := private.issue_license(d);
    assert (c ->> 'ou')::bigint - (c ->> 'iat')::bigint = 86400, c::text;
    update public.devices set policy_tier = 'normal' where id = d;
    update public.subscriptions set grace_days = null where business_id = a;
    raise notice 'ok: a business''s grace and a phone''s offline window override the plan; a reduced phone gets one day';
end $$;

do $$
declare
    b       uuid := pg_temp.business_of('bbbbbbbb-0000-4000-8000-000000000002');
    today   date := private.local_today('Africa/Algiers');
    events  int := (select count(*) from private.subscription_events where business_id = b);
    r       jsonb;
begin
    r := private.extend_subscription(b, 30, p_provider => 'cash', p_external_ref => 'recu-0001', p_note => 'Payé en espèces');
    assert r ->> 'status' = 'extended' and (r ->> 'valid_to')::date = today + 29, r::text;
    assert (select plan_id = 'solo' and status = 'active' from public.subscriptions where business_id = b);
    raise notice 'ok: 30 days paid after the end run from today: through today and 29 more';

    r := private.extend_subscription(b, 30, p_provider => 'cash', p_external_ref => 'recu-0001');
    assert r ->> 'status' = 'already_applied' and (select valid_to from public.subscriptions where business_id = b) = today + 29, r::text;
    raise notice 'ok: the same payment twice extends once';

    r := private.extend_subscription(b, 90, p_plan => 'trial', p_note => 'Testeur');
    assert (r ->> 'valid_to')::date = today + 119, r::text;
    raise notice 'ok: days given before the end add to it: a tester''s three months';

    update public.subscriptions set valid_to = valid_to + 1 where business_id = b;
    assert (select count(*) from private.subscription_events where business_id = b) = events + 3;
    assert (select changed_by = current_user and (before ->> 'valid_to')::date = today + 119
                   and (after ->> 'valid_to')::date = today + 120
            from private.subscription_events where business_id = b order by id desc limit 1);
    raise notice 'ok: every change is in the audit, an edit by hand in Studio as well';
end $$;

do $$
declare b uuid := pg_temp.business_of('bbbbbbbb-0000-4000-8000-000000000002');
begin
    update public.subscriptions set status = 'suspended' where business_id = b;
    begin
        perform private.issue_license(pg_temp.device('e0000000-0000-4000-8000-0000000000b1'));
        assert false, 'a suspended subscription got a license';
    exception when raise_exception then
        assert sqlerrm = 'subscription_inactive', sqlerrm;
    end;
    update public.subscriptions set status = 'active' where business_id = b;
    raise notice 'ok: a suspended subscription gets no license';
end $$;

-- ───────────── Business: an agent, several businesses ─────────────

do $$
declare
    a  uuid := pg_temp.business_of('aaaaaaaa-0000-4000-8000-000000000001');
    b  uuid := pg_temp.business_of('bbbbbbbb-0000-4000-8000-000000000002');
    r  jsonb;
begin
    insert into public.memberships (business_id, user_id, role) values (a, 'cccccccc-0000-4000-8000-000000000003', 'agent');
    insert into public.memberships (business_id, user_id, role) values (b, 'cccccccc-0000-4000-8000-000000000003', 'agent');
    r := pg_temp.activate('cccccccc-0000-4000-8000-000000000003', 'e0000000-0000-4000-8000-0000000000c1', 'key-c1', 'phone-c');
    assert r ->> 'status' = 'choose_business', r::text;
    update public.memberships set status = 'removed' where business_id = b and user_id = 'cccccccc-0000-4000-8000-000000000003';

    update public.subscriptions set max_devices = 3 where business_id = a;
    r := pg_temp.activate('cccccccc-0000-4000-8000-000000000003', 'e0000000-0000-4000-8000-0000000000c1', 'key-c1', 'phone-c');
    assert r ->> 'status' = 'activated', r::text;
    assert (select count(*) from public.devices where business_id = a and status = 'active') = 2;
    raise notice 'ok: an agent with two businesses must choose; with more seats, owner and agent each hold one';
end $$;

do $$
declare agent_device uuid := pg_temp.device('e0000000-0000-4000-8000-0000000000c1');
begin
    assert private.release_device('bbbbbbbb-0000-4000-8000-000000000002', agent_device) ->> 'status' = 'not_allowed';
    assert private.release_device('cccccccc-0000-4000-8000-000000000003', pg_temp.device('e0000000-0000-4000-8000-000000000003')) ->> 'status' = 'not_allowed';
    assert private.release_device('aaaaaaaa-0000-4000-8000-000000000001', agent_device) ->> 'status' = 'released';
    assert (select status from public.devices where id = agent_device) = 'released';
    assert pg_temp.activate('cccccccc-0000-4000-8000-000000000003', 'e0000000-0000-4000-8000-0000000000c1', 'key-c1', 'phone-c') ->> 'status' = 'activated';
    raise notice 'ok: a phone is released by its user or an owner, not by another agent or a stranger';
end $$;

-- ───────────── Row-level security: what the app can read ─────────────

set role authenticated;
select set_config('request.jwt.claim.sub', 'aaaaaaaa-0000-4000-8000-000000000001', false);

do $$
declare n int;
begin
    assert (select count(*) from public.businesses) = 1 and (select name from public.businesses) = 'Distribution A';
    assert (select count(*) from public.subscriptions) = 1;
    assert (select count(*) from public.memberships) = 2;
    -- The owner sees every phone of the business, but only what the Compte screen shows.
    assert (select count(*) from (select id, model, status from public.devices) d) = 4;
    assert (select count(*) from public.profiles) = 2;
    assert (select count(*) from public.plans) = 2;
    update public.businesses set name = 'Distribution A Nord';
    get diagnostics n = row_count;
    assert n = 1;
    update public.profiles set display_name = 'Ayoub';
    get diagnostics n = row_count;
    assert n = 1;
    raise notice 'ok: an owner reads their business, its members and phones, and renames both';
end $$;

do $$ begin
    begin
        perform key_spki from public.devices;
        assert false, 'the device key was readable';
    exception when insufficient_privilege then null;
    end;
    begin
        perform android_id_hint, installation_id, attestation from public.devices;
        assert false, 'the hints were readable';
    exception when insufficient_privilege then null;
    end;
    begin
        update public.subscriptions set valid_to = valid_to + 365;
        assert false, 'a subscription was extended from the app';
    exception when insufficient_privilege then null;
    end;
    begin
        insert into public.memberships (business_id, user_id, role)
        select id, 'dddddddd-0000-4000-8000-000000000004', 'owner' from public.businesses;
        assert false, 'a membership was created from the app';
    exception when insufficient_privilege then null;
    end;
    begin
        update public.devices set status = 'active';
        assert false, 'a device was changed from the app';
    exception when insufficient_privilege then null;
    end;
    raise notice 'ok: the app cannot read keys or hints, nor change a subscription, a membership or a device';
end $$;

do $$ begin
    begin
        perform * from private.nonces;
        assert false, 'private tables were readable';
    exception when insufficient_privilege then null;
    end;
    begin
        perform private.issue_license(gen_random_uuid());
        assert false, 'issue_license ran for the app';
    exception when insufficient_privilege then null;
    end;
    begin
        perform private.extend_subscription(gen_random_uuid(), 365);
        assert false, 'extend_subscription ran for the app';
    exception when insufficient_privilege then null;
    end;
    begin
        perform private.create_nonce(auth.uid(), 'refresh');
        assert false, 'create_nonce ran for the app';
    exception when insufficient_privilege then null;
    end;
    raise notice 'ok: nothing private is reachable from the app, tables or functions';
end $$;

select set_config('request.jwt.claim.sub', 'cccccccc-0000-4000-8000-000000000003', false);
do $$
declare n int;
begin
    assert (select count(*) from public.businesses) = 1;
    assert (select count(*) from public.devices) = 1;
    assert (select count(*) from public.profiles) = 2;
    update public.businesses set name = 'Pris par un agent';
    get diagnostics n = row_count;
    assert n = 0;
    update public.profiles set display_name = 'x' where id = 'aaaaaaaa-0000-4000-8000-000000000001';
    get diagnostics n = row_count;
    assert n = 0;
    raise notice 'ok: an agent sees the business, their own phone and their colleagues'' names, and renames nothing but themselves';
end $$;

select set_config('request.jwt.claim.sub', 'bbbbbbbb-0000-4000-8000-000000000002', false);
do $$ begin
    assert (select name from public.businesses) = 'Distribution B';
    assert (select count(*) from public.devices) = 1;
    assert not exists (select 1 from public.profiles where id = 'aaaaaaaa-0000-4000-8000-000000000001');
    assert not exists (select 1 from public.profiles where id = 'cccccccc-0000-4000-8000-000000000003');
    raise notice 'ok: another business sees none of A, nor the agent who left it';
end $$;

reset role;
set role anon;
select set_config('request.jwt.claim.sub', '', false);
do $$ begin
    begin
        perform * from public.businesses;
        assert false, 'anon read businesses';
    exception when insufficient_privilege then null;
    end;
    begin
        perform * from public.plans;
        assert false, 'anon read plans';
    exception when insufficient_privilege then null;
    end;
    raise notice 'ok: without an account, nothing is readable';
end $$;
reset role;

-- ───────────── Deleting an account (Play's requirement) ─────────────

do $$
declare
    r  jsonb;
    d  uuid;
begin
    r := private.delete_account_data('aaaaaaaa-0000-4000-8000-000000000001');
    assert r ->> 'status' = 'transfer_ownership_first', r::text;
    raise notice 'ok: an owner with colleagues must hand the business over before deleting the account';

    r := private.create_business('dddddddd-0000-4000-8000-000000000004', 'Distribution D', 'phone-d');
    assert (r ->> 'trial')::boolean;
    d := (r ->> 'business_id')::uuid;
    perform pg_temp.activate('dddddddd-0000-4000-8000-000000000004', 'e0000000-0000-4000-8000-0000000000d1', 'key-d1', 'phone-d');
    assert private.delete_account_data('dddddddd-0000-4000-8000-000000000004') ->> 'status' = 'ok';
    delete from auth.users where id = 'dddddddd-0000-4000-8000-000000000004';
    assert not exists (select 1 from public.businesses where id = d);
    assert not exists (select 1 from public.devices where business_id = d);
    assert not exists (select 1 from public.subscriptions where business_id = d);
    assert not exists (select 1 from public.profiles where id = 'dddddddd-0000-4000-8000-000000000004');
    assert (select business_id is null from private.trial_claims where android_id_hint = 'phone-d');
    raise notice 'ok: a sole owner''s account goes with its business, phones and subscription; the trial claim stays';

    insert into auth.users (id, email) values ('dddddddd-0000-4000-8000-000000000004', 'd@example.com');
    r := private.create_business('dddddddd-0000-4000-8000-000000000004', 'Distribution D bis', 'phone-d');
    assert not (r ->> 'trial')::boolean, r::text;
    raise notice 'ok: signing up again on that phone earns no second trial';
end $$;

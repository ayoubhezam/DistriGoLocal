-- A trial refused on a phone that already had one gets no grace (migration 20261103120000_refused_trial_no_grace.sql),
-- and a code still renews it from today with the plan's grace back. Run by run.sh after the other tests. Every check
-- prints "ok: …"; the first failure stops the script.

\set ON_ERROR_STOP on
set client_min_messages = notice;

-- ── People ──
-- first_r   71…01  signs up first on the phone 'phone-r': gets the trial
-- again_r   72…02  a new account on the same phone (app data cleared): trial refused
-- again_r2  73…03  yet another account on that phone
-- fresh_n   74…04  a really new phone 'phone-n'
insert into auth.users (id, email) values
    ('71000000-0000-4000-8000-000000000001', 'first-r@example.com'),
    ('72000000-0000-4000-8000-000000000002', 'again-r@example.com'),
    ('73000000-0000-4000-8000-000000000003', 'again-r2@example.com'),
    ('74000000-0000-4000-8000-000000000004', 'fresh-n@example.com');

create function pg_temp.activate(p_user uuid, p_installation text, p_key text, p_hint text) returns jsonb
language sql as $$
    select private.activate_device(p_user, null, p_installation::uuid, p_key, convert_to(p_key, 'UTF8'),
                                   p_hint, 'SM-M346B', '16', '1.0', '{"security_level": "tee"}'::jsonb, 'normal',
                                   false, null)
$$;

create function pg_temp.today() returns date language sql as $$ select private.local_today('Africa/Algiers') $$;

-- The license a business's phone gets: its validity end (vt) and grace end (gu), in seconds
create function pg_temp.license(p_user uuid, p_installation text, p_key text, p_hint text) returns jsonb
language plpgsql as $$
declare r jsonb;
begin
    r := pg_temp.activate(p_user, p_installation, p_key, p_hint);
    if r ->> 'status' <> 'activated' then raise exception 'not activated: %', r; end if;
    return private.issue_license((r ->> 'device_id')::uuid);
end $$;

do $$
declare
    r jsonb;
    c jsonb;
    s public.subscriptions;
begin
    -- The first account on the phone: a real trial, with the plan's grace after it
    r := private.create_business('71000000-0000-4000-8000-000000000001', 'Distribution R', 'phone-r');
    assert (r ->> 'trial')::boolean, r::text;
    select * into s from public.subscriptions where business_id = (r ->> 'business_id')::uuid;
    assert s.grace_days is null and s.valid_to = pg_temp.today() + 30, row_to_json(s)::text;
    c := pg_temp.license('71000000-0000-4000-8000-000000000001', 'e7000000-0000-4000-8000-000000000001', 'key-r1', 'phone-r');
    assert (c ->> 'gu')::bigint - (c ->> 'vt')::bigint = 3 * 86400, c::text;
    raise notice 'ok: a first sign-up on a phone gets the trial, and the plan''s 3 days of grace after it';
end $$;

do $$
declare
    r jsonb;
    c jsonb;
    s public.subscriptions;
begin
    -- Data cleared, new account, same phone: trial refused, and no grace at all
    r := private.create_business('72000000-0000-4000-8000-000000000002', 'Distribution R bis', 'phone-r');
    assert not (r ->> 'trial')::boolean, r::text;
    select * into s from public.subscriptions where business_id = (r ->> 'business_id')::uuid;
    assert s.valid_to = pg_temp.today() - 1, row_to_json(s)::text;
    assert s.grace_days = 0, row_to_json(s)::text;
    assert s.note = 'Essai déjà utilisé sur ce téléphone', row_to_json(s)::text;
    c := pg_temp.license('72000000-0000-4000-8000-000000000002', 'e7000000-0000-4000-8000-000000000002', 'key-r2', 'phone-r');
    assert (c ->> 'gu')::bigint = (c ->> 'vt')::bigint, c::text;
    assert (c ->> 'gu')::bigint < (c ->> 'iat')::bigint, c::text;
    raise notice 'ok: a refused trial''s license has no grace: it has already ended when it is issued';

    -- And again with a third account: still no grace, so no endless 3 days
    r := private.create_business('73000000-0000-4000-8000-000000000003', 'Distribution R ter', 'phone-r');
    assert not (r ->> 'trial')::boolean, r::text;
    c := pg_temp.license('73000000-0000-4000-8000-000000000003', 'e7000000-0000-4000-8000-000000000003', 'key-r3', 'phone-r');
    assert (c ->> 'gu')::bigint = (c ->> 'vt')::bigint and (c ->> 'gu')::bigint < (c ->> 'iat')::bigint, c::text;
    raise notice 'ok: each new account on that phone is refused again, with no grace each time';
end $$;

do $$
declare
    v_code text := (select c.code from private.create_codes(1, 'solo', 30, 'Tests refused trial') c);
    v_biz  uuid := (select business_id from public.memberships where user_id = '72000000-0000-4000-8000-000000000002');
    r      jsonb;
    c      jsonb;
    s      public.subscriptions;
begin
    -- A valid code on the refused business: renewed from today, with the plan's grace back
    r := private.redeem_code('72000000-0000-4000-8000-000000000002', v_code);
    assert r ->> 'status' = 'renewed' and (r ->> 'business_id')::uuid = v_biz, r::text;
    select * into s from public.subscriptions where business_id = v_biz;
    assert s.plan_id = 'solo' and s.status = 'active', row_to_json(s)::text;
    -- From the day before today, as extend_subscription counts: today is the code's first day, so 30 days end at today + 29
    assert s.valid_to = pg_temp.today() - 1 + 30, row_to_json(s)::text;
    assert s.grace_days is null, row_to_json(s)::text;
    c := private.issue_license((select id from public.devices where installation_id = 'e7000000-0000-4000-8000-000000000002'));
    assert (c ->> 'vt')::bigint > (c ->> 'iat')::bigint, c::text;
    assert (c ->> 'gu')::bigint - (c ->> 'vt')::bigint = 3 * 86400, c::text;
    raise notice 'ok: a code renews a refused trial from today for its days, and the plan''s grace comes back';
end $$;

do $$
declare
    r jsonb;
    c jsonb;
begin
    -- A really new phone: its own trial, untouched by the rule
    r := private.create_business('74000000-0000-4000-8000-000000000004', 'Distribution N', 'phone-n');
    assert (r ->> 'trial')::boolean, r::text;
    c := pg_temp.license('74000000-0000-4000-8000-000000000004', 'e7000000-0000-4000-8000-000000000004', 'key-n1', 'phone-n');
    assert (c ->> 'gu')::bigint - (c ->> 'vt')::bigint = 3 * 86400 and (c ->> 'vt')::bigint > (c ->> 'iat')::bigint, c::text;
    raise notice 'ok: a new phone still gets its trial and its grace';
end $$;

do $$
declare
    v_biz uuid := (select business_id from public.memberships where user_id = '73000000-0000-4000-8000-000000000003');
begin
    -- A subscription made the old way (grace null) is closed by the migration's update
    update public.subscriptions set grace_days = null where business_id = v_biz;
    update public.subscriptions set grace_days = 0
    where note = 'Essai déjà utilisé sur ce téléphone' and grace_days is null;
    assert (select grace_days from public.subscriptions where business_id = v_biz) = 0;
    raise notice 'ok: refused trials created before the fix lose their grace too';
end $$;

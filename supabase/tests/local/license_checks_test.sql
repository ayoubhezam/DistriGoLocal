-- License security (migration 20261117140000_license_device_checks.sql): test accounts (LIC-03), the trial's device
-- check (LIC-04), and the reinstalled-phone release after 48 hours of silence (LIC-01). Run by run.sh after the other
-- tests. Every check prints "ok: …"; the first failure stops the script.

\set ON_ERROR_STOP on
set client_min_messages = notice;

insert into auth.users (id, email) values
    ('91000000-0000-4000-8000-000000000001', 'tester@example.com'),
    ('92000000-0000-4000-8000-000000000002', 'reduced@example.com'),
    ('93000000-0000-4000-8000-000000000003', 'genuine@example.com'),
    ('94000000-0000-4000-8000-000000000004', 'seat@example.com');
insert into private.test_accounts (user_id, note) values ('91000000-0000-4000-8000-000000000001', 'the owner''s phone');

create function pg_temp.today() returns date language sql as $$ select private.local_today('Africa/Algiers') $$;

-- One activation of user 94 in its business, from installation p_installation of the phone p_hint
create function pg_temp.seat(p_installation text, p_hint text, p_transfer boolean) returns jsonb
language sql as $$
    select private.activate_device('94000000-0000-4000-8000-000000000004', null, p_installation::uuid,
                                   'key-' || p_installation, convert_to(p_installation, 'UTF8'), p_hint, 'SM-M346B',
                                   '16', '1.0', '{}'::jsonb, 'normal', p_transfer, null)
$$;

do $$
begin
    assert private.is_test_account('91000000-0000-4000-8000-000000000001');
    assert not private.is_test_account('92000000-0000-4000-8000-000000000002');
    raise notice 'ok: an account is a test account only when listed in private.test_accounts';
end $$;

do $$
declare
    r jsonb;
    s public.subscriptions;
begin
    -- A phone the Edge Function could not vouch for: no trial, and nothing claimed for its ANDROID_ID
    r := private.create_business('92000000-0000-4000-8000-000000000002', 'Dépôt Réduit', 'phone-q',
                                 null, null, null, false);
    assert r ->> 'status' = 'created' and not (r ->> 'trial')::boolean and r ->> 'trial_refused' = 'device_check', r::text;
    select * into s from public.subscriptions where business_id = (r ->> 'business_id')::uuid;
    assert s.valid_to = pg_temp.today() - 1 and s.grace_days = 0
           and s.note = 'Essai non accordé : téléphone non vérifié', row_to_json(s)::text;
    assert not exists (select 1 from private.trial_claims where android_id_hint = 'phone-q');
    raise notice 'ok: a trial refused for the device check ends at once, with no grace, and claims nothing';

    -- A code then pays for it, and the grace comes back
    r := private.extend_subscription(s.business_id, 30, 'solo', null, 'code', 'device-check-1');
    select * into s from public.subscriptions where business_id = s.business_id;
    assert r ->> 'status' = 'extended' and s.grace_days is null and s.valid_to = pg_temp.today() - 1 + 30, row_to_json(s)::text;
    raise notice 'ok: a code renews a business refused the trial for the device check, with the plan''s grace back';
end $$;

do $$
declare
    r jsonb;
begin
    -- A genuine phone gets its trial; the same phone again is told it was used, not that it failed the check
    r := private.create_business('93000000-0000-4000-8000-000000000003', 'Dépôt Vrai', 'phone-g',
                                 null, null, null, true);
    assert (r ->> 'trial')::boolean and r ->> 'trial_refused' is null, r::text;
    r := private.create_business('91000000-0000-4000-8000-000000000001', 'Dépôt Testeur', 'phone-g',
                                 null, null, null, true);
    assert not (r ->> 'trial')::boolean and r ->> 'trial_refused' = 'already_used', r::text;
    raise notice 'ok: a checked phone gets its 30 days; the same phone again is refused as already used';
end $$;

do $$
declare
    r      jsonb;
    first  uuid;
    biz    uuid;
begin
    biz := (private.create_business('94000000-0000-4000-8000-000000000004', 'Dépôt Siège', 'phone-s') ->> 'business_id')::uuid;
    r := pg_temp.seat('c4000000-0000-4000-8000-000000000001', 'phone-s', false);
    assert r ->> 'status' = 'activated', r::text;
    first := (r ->> 'device_id')::uuid;

    -- Another installation sends the same ANDROID_ID and model while the first still checks in: the seat is taken
    r := pg_temp.seat('c4000000-0000-4000-8000-000000000002', 'phone-s', false);
    assert r ->> 'status' = 'seat_taken', r::text;
    assert (select status from public.devices where id = first) = 'active';
    raise notice 'ok: copying a working phone''s ANDROID_ID and model no longer takes its seat for free';

    -- Moved anyway: a counted transfer, and the first phone revoked
    r := pg_temp.seat('c4000000-0000-4000-8000-000000000002', 'phone-s', true);
    assert r ->> 'status' = 'activated', r::text;
    assert (select status from public.devices where id = first) = 'revoked';
    assert (select counted from private.device_transfers where from_device = first);
    raise notice 'ok: taking the seat from a working phone is a counted transfer that revokes it';

    -- The second phone, reinstalled after 48 hours of silence: its seat back, nothing counted
    update public.devices set last_seen_at = now() - interval '49 hours'
    where installation_id = 'c4000000-0000-4000-8000-000000000002';
    r := pg_temp.seat('c4000000-0000-4000-8000-000000000003', 'phone-s', false);
    assert r ->> 'status' = 'activated', r::text;
    assert (select status from public.devices where installation_id = 'c4000000-0000-4000-8000-000000000002') = 'released';
    assert not (select counted from private.device_transfers
                where to_device = (r ->> 'device_id')::uuid);
    raise notice 'ok: the same phone reinstalled after 48 hours of silence gets the seat back, not counted';
end $$;

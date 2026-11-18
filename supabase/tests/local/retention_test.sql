-- Retention and deletion (migration 20261118120000_retention_and_deletion.sql): the nightly purge keeps check-ins,
-- old phones and phone changes 12 months at most; a deleted account leaves no business name on its codes. Run by
-- run.sh after the other tests. Every check prints "ok: …"; the first failure stops the script.

\set ON_ERROR_STOP on
set client_min_messages = notice;

insert into auth.users (id, email) values
    ('a7000000-0000-4000-8000-000000000071', 'old@example.com'),
    ('a8000000-0000-4000-8000-000000000081', 'named@example.com');

-- One activation for user 71 from installation p_installation
create function pg_temp.seat(p_installation text, p_transfer boolean) returns jsonb
language sql as $$
    select private.activate_device('a7000000-0000-4000-8000-000000000071', null, p_installation::uuid,
                                   'key-' || p_installation, convert_to(p_installation, 'UTF8'), 'hint-' || p_installation,
                                   'SM-M346B', '16', '1.0', '{}'::jsonb, 'normal', p_transfer, null)
$$;

do $$
declare
    r        jsonb;
    business uuid;
    old_dev  uuid;
    new_dev  uuid;
    recent   uuid;
begin
    r := private.create_business('a7000000-0000-4000-8000-000000000071', 'Dépôt Ancien', 'phone-old', null, null, null, true);
    business := (r ->> 'business_id')::uuid;

    -- A first phone, checked in; then moved to a second phone, which revokes the first
    r := pg_temp.seat('a7000000-0000-4000-8000-0000000000a1', false);
    old_dev := (r ->> 'device_id')::uuid;
    perform private.issue_license(old_dev);
    r := pg_temp.seat('a7000000-0000-4000-8000-0000000000a2', true);
    assert r ->> 'status' = 'activated', r::text;
    new_dev := (r ->> 'device_id')::uuid;
    perform private.issue_license(new_dev);
    perform private.issue_license(new_dev);
    assert (select status from public.devices where id = old_dev) = 'revoked';

    -- Thirteen months later for the first phone, its check-in and the phone change; the second phone's first
    -- check-in is as old, its second is today
    update public.devices set released_at = now() - interval '13 months' where id = old_dev;
    update private.license_issuances set issued_at = now() - interval '13 months' where device_id = old_dev;
    update private.license_issuances set issued_at = now() - interval '13 months'
    where device_id = new_dev and seq = (select min(seq) from private.license_issuances where device_id = new_dev);
    update private.device_transfers set at = now() - interval '13 months' where business_id = business;

    -- A third phone released only last month: kept
    r := pg_temp.seat('a7000000-0000-4000-8000-0000000000a3', true);
    recent := (r ->> 'device_id')::uuid;
    perform private.release_device('a7000000-0000-4000-8000-000000000071', recent);
    update public.devices set released_at = now() - interval '1 month' where id = recent;
    -- the transfer to it is recent too
    perform pg_temp.seat('a7000000-0000-4000-8000-0000000000a2', true);

    r := private.purge_old_records();
    assert (r ->> 'devices')::int = 1 and (r ->> 'transfers')::int = 1, r::text;
    assert not exists (select 1 from public.devices where id = old_dev);
    assert not exists (select 1 from private.license_issuances where device_id = old_dev);
    raise notice 'ok: a phone released or revoked over 12 months ago goes, with its check-ins';

    assert (select status from public.devices where id = new_dev) = 'active';
    assert (select count(*) from private.license_issuances where device_id = new_dev) = 1;
    assert not exists (select 1 from private.license_issuances where issued_at < now() - interval '12 months');
    raise notice 'ok: check-ins over 12 months old go, an active phone and its recent check-ins stay';

    assert exists (select 1 from public.devices where id = recent and status = 'released');
    assert not exists (select 1 from private.device_transfers where at < now() - interval '12 months');
    assert exists (select 1 from private.device_transfers where business_id = business);
    raise notice 'ok: a phone released last month and recent phone changes stay; older changes go';

    r := private.purge_old_records();
    assert (r ->> 'issuances')::int = 0 and (r ->> 'devices')::int = 0 and (r ->> 'transfers')::int = 0, r::text;
    raise notice 'ok: a second purge finds nothing more';
end $$;

do $$
declare
    r        jsonb;
    code     text;
    business uuid;
    other    text;
begin
    -- A code made for « Épicerie Nommée », redeemed by user 81; another code for a business nobody deletes
    code := (select c.code from private.create_codes(1, 'solo', 30, 'Retention', 'Épicerie Nommée') c);
    other := (select c.code from private.create_codes(1, 'solo', 30, 'Retention', 'Épicerie Gardée') c);
    r := private.redeem_code('a8000000-0000-4000-8000-000000000081', code);
    assert r ->> 'status' = 'created', r::text;
    business := (r ->> 'business_id')::uuid;
    assert (select business_name from private.activation_codes where digest = private.code_digest(code)) = 'Épicerie Nommée';

    r := private.delete_account_data('a8000000-0000-4000-8000-000000000081');
    assert r ->> 'status' = 'ok', r::text;
    assert not exists (select 1 from public.businesses where id = business);
    assert (select business_name is null and business_id is null and use = 'key' and redeemed_at is not null
            from private.activation_codes where digest = private.code_digest(code));
    raise notice 'ok: a deleted account''s code keeps no business name, and stays used';

    assert (select business_name from private.activation_codes where digest = private.code_digest(other)) = 'Épicerie Gardée';
    raise notice 'ok: a code made for another business keeps its name';
end $$;

do $$
begin
    assert not has_function_privilege('anon', 'private.purge_old_records(interval)', 'execute');
    assert not has_function_privilege('authenticated', 'private.delete_account_data(uuid)', 'execute');
    raise notice 'ok: neither the purge nor the deletion can be called from the app''s roles';
end $$;

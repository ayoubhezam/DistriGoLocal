-- « Comptes clients » (migration 20261015090000_clients_admin.sql), tested on a plain PostgreSQL after
-- codes_admin_test.sql: run.sh. Every check prints "ok: …"; the first failure stops the script.

\set ON_ERROR_STOP on
set client_min_messages = notice;

-- ── People ──
-- admin_1   e1…51  the owner, member of his own « Dépôt Central »
-- lynda_2   e2…52  e-mail client: « Épicerie Lynda », phone 0699 81 72 63
-- kader_3   e3…53  anonymous client: « Boissons Kader », opened with a code
insert into auth.users (id, email, is_anonymous, raw_app_meta_data) values
    ('e1111111-0000-4000-8000-000000000051', 'admin-clients@example.com', false, '{"distrigo_admin": true}'),
    ('e2222222-0000-4000-8000-000000000052', 'lynda@example.com', false, '{}'),
    ('e3333333-0000-4000-8000-000000000053', null, true, '{}');

select private.create_business('e1111111-0000-4000-8000-000000000051', 'Dépôt Central', 'phone-e1');
select private.create_business('e2222222-0000-4000-8000-000000000052', 'Épicerie Lynda', 'phone-e2', '0699 81 72 63', 'alimentation', '1');

create temp table kader_code as
select code from private.create_codes(1, 'solo', 182, 'Clients test', 'Boissons Kader');
select private.redeem_code('e3333333-0000-4000-8000-000000000053', (select code from kader_code));

create function pg_temp.business(p_name text) returns uuid language sql as $$
    select id from public.businesses where name = p_name
$$;

create function pg_temp.names(p_found jsonb) returns text[] language sql as $$
    select coalesce(array_agg(f ->> 'name' order by f ->> 'name'), '{}') from jsonb_array_elements(p_found) f
$$;

-- ───────────── Finding a client ─────────────

do $$
declare
    v jsonb;
begin
    v := private.find_businesses('lynda');
    assert pg_temp.names(v) = array['Épicerie Lynda'], v::text;
    assert v -> 0 ->> 'contact_phone' = '0699 81 72 63' and v -> 0 ->> 'owner_email' = 'lynda@example.com', v::text;
    assert v -> 0 ->> 'status' = 'active' and v -> 0 ->> 'plan' = 'trial' and (v -> 0 ->> 'by_code')::boolean = false, v::text;
    assert pg_temp.names(private.find_businesses('06998172')) = array['Épicerie Lynda'], 'by the phone''s digits';
    assert pg_temp.names(private.find_businesses('0699 8172 63')) = array['Épicerie Lynda'], 'whatever the spaces';
    assert pg_temp.names(private.find_businesses('LYNDA@EXAMPLE')) = array['Épicerie Lynda'], 'by the e-mail';
    raise notice 'ok: a client is found by a part of its name, its phone''s digits or its e-mail';
end $$;

do $$
declare
    v jsonb := private.find_businesses((select lower(replace(code, '-', ' ')) from kader_code));
begin
    assert pg_temp.names(v) = array['Boissons Kader'], v::text;
    assert (v -> 0 ->> 'by_code')::boolean and v -> 0 ->> 'owner_email' is null and v -> 0 ->> 'plan' = 'solo', v::text;
    assert jsonb_array_length(private.find_businesses('')) >= 3, 'empty: the latest';
    assert jsonb_array_length(private.find_businesses(null)) <= 30;
    assert private.find_businesses('personne ici') = '[]', 'nothing found is an empty list';
    assert private.find_businesses('123') = '[]', 'three digits are no phone';
    raise notice 'ok: a client opened with a code is found by its code, typed loosely; an empty search lists the latest';
end $$;

-- ───────────── Suspending, reactivating ─────────────

do $$
declare
    v jsonb := private.set_business_suspended('e1111111-0000-4000-8000-000000000051', pg_temp.business('Épicerie Lynda'), true, 'Impayé');
begin
    assert v = '{"status": "ok", "subscription_status": "suspended"}', v::text;
    assert (select status from public.subscriptions where business_id = pg_temp.business('Épicerie Lynda')) = 'suspended';
    assert (select note from public.subscriptions where business_id = pg_temp.business('Épicerie Lynda')) like '%Impayé%';
    assert private.find_businesses('lynda') -> 0 ->> 'status' = 'suspended';
    v := private.set_business_suspended('e1111111-0000-4000-8000-000000000051', pg_temp.business('Épicerie Lynda'), false);
    assert v ->> 'subscription_status' = 'active', v::text;
    assert (select status from public.subscriptions where business_id = pg_temp.business('Épicerie Lynda')) = 'active';
    raise notice 'ok: a client''s subscription is suspended with a note, then reactivated';
end $$;

do $$ begin
    assert private.set_business_suspended('e1111111-0000-4000-8000-000000000051', pg_temp.business('Dépôt Central'), true)
           = '{"status": "own_business"}';
    assert private.delete_business('e1111111-0000-4000-8000-000000000051', pg_temp.business('Dépôt Central'))
           = '{"status": "own_business"}';
    assert (select status from public.subscriptions where business_id = pg_temp.business('Dépôt Central')) = 'active';
    assert private.set_business_suspended('e1111111-0000-4000-8000-000000000051', '00000000-0000-4000-8000-000000000000', true)
           = '{"status": "unknown_business"}';
    raise notice 'ok: the owner''s own business can be neither suspended nor deleted; an unknown one is said so';
end $$;

-- ───────────── Deleting ─────────────

do $$
declare
    v_kader uuid := pg_temp.business('Boissons Kader');
begin
    assert private.delete_business('e1111111-0000-4000-8000-000000000051', v_kader) = '{"status": "deleted"}';
    assert not exists (select 1 from public.businesses where id = v_kader);
    assert not exists (select 1 from public.subscriptions where business_id = v_kader);
    assert not exists (select 1 from public.memberships where business_id = v_kader);
    assert exists (select 1 from auth.users where id = 'e3333333-0000-4000-8000-000000000053'), 'the account stays';
    assert private.redeem_code('e3333333-0000-4000-8000-000000000053', (select code from kader_code)) = '{"status": "bad_code"}',
           'its key opens nothing';
    assert private.delete_business('e1111111-0000-4000-8000-000000000051', v_kader) = '{"status": "unknown_business"}';
    raise notice 'ok: a deleted client''s business, subscription and memberships go; the account stays; its key opens nothing';
end $$;

-- ───────────── Out of the app's reach ─────────────

set role authenticated;
select set_config('request.jwt.claim.sub', 'e1111111-0000-4000-8000-000000000051', false);
do $$ begin
    begin
        perform private.find_businesses('lynda');
        assert false, 'a signed-in user searched the clients directly';
    exception when insufficient_privilege then null;
    end;
    begin
        perform private.delete_business('e1111111-0000-4000-8000-000000000051', '00000000-0000-4000-8000-000000000000');
        assert false, 'a signed-in user deleted a business directly';
    exception when insufficient_privilege then null;
    end;
    raise notice 'ok: the clients'' functions are the server''s alone';
end $$;
reset role;

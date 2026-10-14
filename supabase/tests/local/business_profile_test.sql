-- « Informations de l'entreprise » (migration 20261014180000_business_profile.sql), tested on a plain
-- PostgreSQL after codes_test.sql: run.sh. Every check prints "ok: …"; the first failure stops the script.

\set ON_ERROR_STOP on
set client_min_messages = notice;

-- ── People ──
-- owner_1    b1…21  owner of « Dépôt Profil », made at sign-up with its details
-- manager_2  b2…22  manager of « Dépôt Profil »
-- agent_3    b3…23  agent of « Dépôt Profil »
-- alone_4    b4…24  no business
-- double_5   b5…25  member of « Dépôt Profil » (agent) and owner of « Second Dépôt »
insert into auth.users (id, email) values
    ('b1111111-0000-4000-8000-000000000021', 'b1@example.com'),
    ('b2222222-0000-4000-8000-000000000022', 'b2@example.com'),
    ('b3333333-0000-4000-8000-000000000023', 'b3@example.com'),
    ('b4444444-0000-4000-8000-000000000024', 'b4@example.com'),
    ('b5555555-0000-4000-8000-000000000025', 'b5@example.com');

select private.create_business('b1111111-0000-4000-8000-000000000021', 'Dépôt Profil', 'phone-b1',
                               '0550 11 22 33', 'boissons', '2-5');
select private.create_business('b5555555-0000-4000-8000-000000000025', 'Second Dépôt', 'phone-b5');

-- Through its owner: the checks rename it
create function pg_temp.profil() returns uuid language sql as $$
    select business_id from public.memberships where user_id = 'b1111111-0000-4000-8000-000000000021'
$$;

insert into public.memberships (business_id, user_id, role) values
    (pg_temp.profil(), 'b2222222-0000-4000-8000-000000000022', 'manager'),
    (pg_temp.profil(), 'b3333333-0000-4000-8000-000000000023', 'agent'),
    (pg_temp.profil(), 'b5555555-0000-4000-8000-000000000025', 'agent');

-- ───────────── Reading ─────────────

do $$
declare
    v jsonb := private.business_profile('b1111111-0000-4000-8000-000000000021');
begin
    assert v = jsonb_build_object('status', 'ok', 'business_id', pg_temp.profil(), 'name', 'Dépôt Profil',
                                  'contact_phone', '0550 11 22 33', 'activity', 'boissons', 'sales_reps', '2-5',
                                  'can_edit', true), v::text;
    assert private.business_profile('b2222222-0000-4000-8000-000000000022') ->> 'can_edit' = 'true';
    v := private.business_profile('b3333333-0000-4000-8000-000000000023');
    assert v ->> 'name' = 'Dépôt Profil' and v ->> 'can_edit' = 'false', v::text;
    raise notice 'ok: the profile reads the business''s four details; owners and managers may change them, agents only read';
end $$;

do $$
declare
    v jsonb;
begin
    assert private.business_profile('b4444444-0000-4000-8000-000000000024') = '{"status": "no_business"}';
    assert private.business_profile('b5555555-0000-4000-8000-000000000025') = '{"status": "choose_business"}';
    v := private.business_profile('b5555555-0000-4000-8000-000000000025',
                                  (select id from public.businesses where name = 'Second Dépôt'));
    assert v ->> 'name' = 'Second Dépôt' and v ->> 'contact_phone' is null and v ->> 'can_edit' = 'true', v::text;
    assert private.business_profile('b4444444-0000-4000-8000-000000000024', pg_temp.profil()) = '{"status": "not_member"}';
    raise notice 'ok: no business, several and none named, or another''s are refused; a named one of several is read';
end $$;

-- ───────────── Changing ─────────────

do $$
declare
    v jsonb := private.update_business_profile('b1111111-0000-4000-8000-000000000021',
                                               '{"name": "  Dépôt Profil Est  ", "sales_reps": "6-10"}');
begin
    assert v ->> 'status' = 'ok', v::text;
    assert v ->> 'name' = 'Dépôt Profil Est', v::text;
    assert v ->> 'sales_reps' = '6-10' and v ->> 'activity' = 'boissons' and v ->> 'contact_phone' = '0550 11 22 33', v::text;
    assert (select name from public.businesses where id = (v ->> 'business_id')::uuid) = 'Dépôt Profil Est';
    raise notice 'ok: a change touches what it names only, trimmed, and answers with the whole profile';
end $$;

do $$
declare
    v jsonb;
begin
    v := private.update_business_profile('b2222222-0000-4000-8000-000000000022', '{"contact_phone": null, "activity": "frais"}');
    assert v ->> 'contact_phone' is null and v ->> 'activity' = 'frais', v::text;
    v := private.update_business_profile('b2222222-0000-4000-8000-000000000022', '{"contact_phone": "0661 00 00 00"}');
    assert v ->> 'contact_phone' = '0661 00 00 00', v::text;
    v := private.update_business_profile('b2222222-0000-4000-8000-000000000022', '{"contact_phone": "   "}');
    assert v ->> 'contact_phone' is null, v::text;
    raise notice 'ok: a manager changes them too; a null or blank phone clears it';
end $$;

do $$
declare
    v jsonb := private.update_business_profile('b3333333-0000-4000-8000-000000000023', '{"name": "Pris"}');
begin
    assert v = '{"status": "not_allowed"}', v::text;
    assert private.update_business_profile('b4444444-0000-4000-8000-000000000024', '{"name": "Pris"}') = '{"status": "no_business"}';
    assert private.update_business_profile('b5555555-0000-4000-8000-000000000025', '{"name": "Pris"}') = '{"status": "choose_business"}';
    assert private.update_business_profile('b5555555-0000-4000-8000-000000000025', '{"name": "Pris"}', pg_temp.profil())
           = '{"status": "not_allowed"}';
    assert not exists (select 1 from public.businesses where name = 'Pris');
    raise notice 'ok: an agent, an account without a business or with several unnamed change nothing';
end $$;

do $$ begin
    begin
        perform private.update_business_profile('b1111111-0000-4000-8000-000000000021', '{"name": "   "}');
        assert false, 'a blank name was kept';
    exception when check_violation then null;
    end;
    begin
        perform private.update_business_profile('b1111111-0000-4000-8000-000000000021', '{"activity": "piraterie"}');
        assert false, 'an unknown activity was kept';
    exception when check_violation then null;
    end;
    assert (select name from public.businesses where id = pg_temp.profil()) = 'Dépôt Profil Est';
    raise notice 'ok: behind the Edge Function''s checks, the table refuses a blank name or an unknown activity';
end $$;

-- ───────────── Out of the app's reach ─────────────

set role authenticated;
select set_config('request.jwt.claim.sub', 'b1111111-0000-4000-8000-000000000021', false);
do $$ begin
    begin
        perform private.business_profile('b1111111-0000-4000-8000-000000000021');
        assert false, 'a signed-in user read a profile directly';
    exception when insufficient_privilege then null;
    end;
    begin
        perform private.update_business_profile('b3333333-0000-4000-8000-000000000023', '{"name": "Pris"}');
        assert false, 'a signed-in user changed a profile directly';
    exception when insufficient_privilege then null;
    end;
    raise notice 'ok: the profile functions are the Edge Function''s alone';
end $$;
reset role;

-- « Lots » (migration 20261015140000_batch_admin.sql), tested on a plain PostgreSQL after clients_admin_test.sql:
-- run.sh. Every check prints "ok: …"; the first failure stops the script.

\set ON_ERROR_STOP on
set client_min_messages = notice;

-- ── People ──
-- opener_1   b1…61  anonymous: opens « Épicerie Sétif » with a code of « Lot Sétif », which becomes its key
-- renewer_2  b2…62  e-mail account with « Dépôt Renouvelé »: a code of « Lot Sétif » adds its days
insert into auth.users (id, email, is_anonymous) values
    ('b1111111-0000-4000-8000-000000000061', null, true),
    ('b2222222-0000-4000-8000-000000000062', 'renew-batch@example.com', false);
select private.create_business('b2222222-0000-4000-8000-000000000062', 'Dépôt Renouvelé', 'phone-b2');

-- « Lot Sétif »: five codes of a year, to activate within 30 days, paid in cash; « Lot vide »: two codes, never used
create temp table setif as select code from private.create_codes(5, 'solo', 365, 'Lot Sétif', null, current_date + 30, 'payé en espèces');
create temp table vide as select code from private.create_codes(2, 'solo', 45, 'Lot vide');
select private.redeem_code('b1111111-0000-4000-8000-000000000061', (select min(code) from setif), 'Épicerie Sétif', '0661 00 00 01');
select private.redeem_code('b2222222-0000-4000-8000-000000000062', (select max(code) from setif));

create temp table renewed_until as
select s.valid_to from public.subscriptions s join public.businesses b on b.id = s.business_id where b.name = 'Dépôt Renouvelé';

-- ───────────── A batch's details ─────────────

do $$
declare
    v jsonb := private.batch_details('Lot Sétif', 'solo', 365);
begin
    assert v ->> 'status' = 'ok', v::text;
    assert (v ->> 'total')::int = 5 and (v ->> 'unused')::int = 3 and (v ->> 'keys')::int = 1 and (v ->> 'renewals')::int = 1, v::text;
    assert (v ->> 'live_keys')::int = 1, v::text;
    assert v ->> 'note' = 'payé en espèces' and v ->> 'expires_on' = (current_date + 30)::text, v::text;
    assert v ->> 'created_on' = current_date::text, v::text;
    assert (select array_agg(u ->> 'use' || ':' || (u ->> 'business_name') order by u ->> 'use')
            from jsonb_array_elements(v -> 'uses') u) = array['key:Épicerie Sétif', 'renewal:Dépôt Renouvelé'], v::text;
    assert private.batch_details('Lot Sétif', 'solo', 30) = '{"status": "unknown_batch"}', 'the days are part of a batch''s name';
    raise notice 'ok: a batch''s details: its counts, deadline, note, live keys, and the business each used code went to';
end $$;

-- ───────────── Deleting it while a code is a key ─────────────

do $$
begin
    assert private.delete_batch('Lot Sétif', 'solo', 365) = '{"status": "in_use", "keys": 1}';
    assert (select count(*) from private.activation_codes where batch = 'Lot Sétif') = 5, 'nothing deleted';
    raise notice 'ok: a batch whose code is still a business''s key is refused, and none of its codes goes';
end $$;

-- ───────────── Its free codes revoked ─────────────

do $$
declare
    v jsonb;
begin
    assert private.revoke_batch('Lot Sétif', 'solo', 365, 'Revendeur parti') = '{"status": "ok", "revoked": 3}';
    v := private.batch_details('Lot Sétif', 'solo', 365);
    assert (v ->> 'unused')::int = 0 and (v ->> 'revoked')::int = 3, v::text;
    assert (v ->> 'keys')::int = 1 and (v ->> 'renewals')::int = 1, 'the used codes are untouched';
    assert v ->> 'note' = 'payé en espèces', 'the batch''s own note, not a revoked code''s';
    assert private.code_status((select min(code) from setif)) ->> 'status' = 'key', 'the key still opens its business';
    assert (select count(*) from setif s where private.code_status(s.code) ->> 'status' = 'revoked') = 3;
    assert private.code_status((select code from setif order by code offset 1 limit 1)) ->> 'note' = 'payé en espèces — Revendeur parti';
    assert private.revoke_batch('Lot Sétif', 'solo', 365) = '{"status": "ok", "revoked": 0}', 'again: nothing left to revoke';
    assert private.revoke_batch('Lot inconnu', 'solo', 365) = '{"status": "unknown_batch"}';
    raise notice 'ok: revoking a batch takes its free codes only, with the reason in their note; the used ones stay';
end $$;

-- ───────────── Deleting it once no business holds its key ─────────────

do $$
begin
    -- « Supprimer le compte » in « Comptes clients »: the code that opened it is no business's key any more
    delete from public.businesses b where b.name = 'Épicerie Sétif';
    assert (private.batch_details('Lot Sétif', 'solo', 365) ->> 'live_keys')::int = 0;
    assert private.delete_batch('Lot Sétif', 'solo', 365) = '{"status": "deleted", "codes": 5}';
    assert not exists (select 1 from private.activation_codes where batch = 'Lot Sétif');
    assert (select s.valid_to from public.subscriptions s join public.businesses b on b.id = s.business_id
            where b.name = 'Dépôt Renouvelé') = (select valid_to from renewed_until), 'days a renewal added stay added';
    assert private.delete_batch('Lot Sétif', 'solo', 365) = '{"status": "unknown_batch"}';
    assert private.delete_batch('Lot vide', 'solo', 45) = '{"status": "deleted", "codes": 2}', 'a batch never used goes at once';
    raise notice 'ok: a batch goes once no business holds its key; a renewal''s days stay; a batch never used goes at once';
end $$;

-- ───────────── Out of the app's reach ─────────────

set role authenticated;
do $$ begin
    begin
        perform private.batch_details('Lot vide', 'solo', 45);
        assert false, 'a signed-in user read a batch directly';
    exception when insufficient_privilege then null;
    end;
    begin
        perform private.delete_batch('Lot vide', 'solo', 45);
        assert false, 'a signed-in user deleted a batch directly';
    exception when insufficient_privilege then null;
    end;
    raise notice 'ok: the batches'' functions are the server''s alone';
end $$;
reset role;

-- C4 (migration 20261014200000_codes_admin.sql), tested on a plain PostgreSQL after business_profile_test.sql:
-- run.sh. Every check prints "ok: …"; the first failure stops the script.

\set ON_ERROR_STOP on
set client_min_messages = notice;

-- ── People ──
-- owner_1     d1…41  the owner: distrigo_admin in app_metadata
-- merchant_2  d2…42  an e-mail account with a business, and an app_metadata of its own
-- stale_3     d3…43  anonymous, 8 days old, never got a business: goes
-- young_4     d4…44  anonymous, 2 days old, no business yet: stays
-- keyed_5     d5…45  anonymous, 30 days old, owner of a business: stays
-- past_6      d6…46  anonymous, 30 days old, a past membership (removed): stays
-- mailed_7    d7…47  given an e-mail (no longer anonymous), 30 days old, no business: stays
insert into auth.users (id, email, is_anonymous, raw_app_meta_data, created_at) values
    ('d1111111-0000-4000-8000-000000000041', 'owner@example.com', false, '{"provider": "email", "distrigo_admin": true}', now() - interval '90 days'),
    ('d2222222-0000-4000-8000-000000000042', 'm@example.com', false, '{"provider": "email", "distrigo_admin": "yes"}', now() - interval '90 days'),
    ('d3333333-0000-4000-8000-000000000043', null, true, '{}', now() - interval '8 days'),
    ('d4444444-0000-4000-8000-000000000044', null, true, '{}', now() - interval '2 days'),
    ('d5555555-0000-4000-8000-000000000045', null, true, '{}', now() - interval '30 days'),
    ('d6666666-0000-4000-8000-000000000046', null, true, '{}', now() - interval '30 days'),
    ('d7777777-0000-4000-8000-000000000047', 'later@example.com', false, '{}', now() - interval '30 days');

select private.create_business('d2222222-0000-4000-8000-000000000042', 'Dépôt Admin', 'phone-d2');
select private.create_business('d5555555-0000-4000-8000-000000000045', 'Dépôt Clé', 'phone-d5');
insert into public.memberships (business_id, user_id, role, status)
select business_id, 'd6666666-0000-4000-8000-000000000046', 'owner', 'removed'
from public.memberships where user_id = 'd5555555-0000-4000-8000-000000000045';

-- ───────────── Who may manage codes ─────────────

do $$ begin
    assert private.is_admin('d1111111-0000-4000-8000-000000000041');
    assert not private.is_admin('d2222222-0000-4000-8000-000000000042'), 'only the boolean true counts';
    assert not private.is_admin('d3333333-0000-4000-8000-000000000043');
    assert not private.is_admin('00000000-0000-4000-8000-000000000000'), 'an unknown account is no admin';
    raise notice 'ok: only an account whose app_metadata says distrigo_admin true manages codes';
end $$;

-- ───────────── Accounts that never got a business ─────────────

do $$
declare
    v_gone int := private.cleanup_anonymous_accounts();
begin
    assert v_gone = 1, format('%s accounts went', v_gone);
    assert not exists (select 1 from auth.users where id = 'd3333333-0000-4000-8000-000000000043');
    assert (select count(*) from auth.users where id in (
        'd1111111-0000-4000-8000-000000000041', 'd2222222-0000-4000-8000-000000000042',
        'd4444444-0000-4000-8000-000000000044', 'd5555555-0000-4000-8000-000000000045',
        'd6666666-0000-4000-8000-000000000046', 'd7777777-0000-4000-8000-000000000047')) = 6;
    assert private.cleanup_anonymous_accounts() = 0, 'a second run finds nothing';
    raise notice 'ok: an anonymous account older than 7 days with no business goes; young ones, members past or present, and e-mail accounts stay';
end $$;

do $$ begin
    assert private.cleanup_anonymous_accounts(interval '1 day') = 1, 'with a shorter delay the 2-day-old one goes too';
    assert not exists (select 1 from auth.users where id = 'd4444444-0000-4000-8000-000000000044');
    assert exists (select 1 from public.businesses where name = 'Dépôt Clé');
    raise notice 'ok: the delay is the argument; businesses are never touched';
end $$;

-- ───────────── Out of the app's reach ─────────────

set role authenticated;
select set_config('request.jwt.claim.sub', 'd1111111-0000-4000-8000-000000000041', false);
do $$ begin
    begin
        perform private.is_admin('d1111111-0000-4000-8000-000000000041');
        assert false, 'a signed-in user asked is_admin directly';
    exception when insufficient_privilege then null;
    end;
    begin
        perform private.cleanup_anonymous_accounts();
        assert false, 'a signed-in user ran the cleanup';
    exception when insufficient_privilege then null;
    end;
    raise notice 'ok: is_admin and the cleanup are the server''s alone';
end $$;
reset role;

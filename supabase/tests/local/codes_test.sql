-- Activation codes (migration 20261014120000_activation_codes.sql), tested on a plain PostgreSQL after
-- license_test.sql: run.sh. Every check prints "ok: …"; the first failure stops the script.

\set ON_ERROR_STOP on
set client_min_messages = notice;

-- ── People ──
-- anon_1  f1…11  anonymous: creates « Épicerie Benali » with a code, then loses the phone
-- anon_2  f2…12  anonymous: the same key on the next phone
-- anon_3  f3…13  anonymous: the same key on a third phone, while anon_2's phone still runs
-- mail_4  f4…14  e-mail account with a trial business: renews with codes
-- anon_5  f5…15  anonymous: a code without a company name
insert into auth.users (id, email, is_anonymous) values
    ('f1111111-0000-4000-8000-000000000011', null, true),
    ('f2222222-0000-4000-8000-000000000012', null, true),
    ('f3333333-0000-4000-8000-000000000013', null, true),
    ('f4444444-0000-4000-8000-000000000014', 'f4@example.com', false),
    ('f5555555-0000-4000-8000-000000000015', null, true);

-- One new code of a batch, as create_codes shows it.
create function pg_temp.code(p_plan text default 'solo', p_days int default 182, p_name text default null,
                             p_batch text default 'Tests') returns text
language sql as $$
    select c.code from private.create_codes(1, p_plan, p_days, p_batch, p_name) c
$$;

create function pg_temp.today() returns date language sql as $$ select private.local_today('Africa/Algiers') $$;

-- ───────────── A code's characters ─────────────

do $$ begin
    assert private.normalize_code('7kq4 m2xp-9htd') = '7KQ4M2XP9HTD';
    assert private.normalize_code('oIl') = '011';
    assert private.normalize_code('u ü #') = '';
    raise notice 'ok: a typed code is read leniently: capitals, O as 0, I and L as 1, the rest dropped';
end $$;

do $$
declare
    v_codes    text[] := array(select private.new_code() from generate_series(1, 300));
    v_code     text;
    v_typo     text;
    v_swap     text;
    v_missed   int := 0;
    v_alphabet constant text := '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
begin
    assert (select count(distinct c) from unnest(v_codes) c) = 300;
    foreach v_code in array v_codes loop
        assert private.code_well_formed(v_code), v_code;
    end loop;
    -- Every wrong character, at every place, in 20 codes
    foreach v_code in array v_codes[1:20] loop
        for i in 1 .. 12 loop
            for j in 1 .. 32 loop
                continue when substr(v_alphabet, j, 1) = substr(v_code, i, 1);
                v_typo := overlay(v_code placing substr(v_alphabet, j, 1) from i for 1);
                assert not private.code_well_formed(v_typo), v_code || ' → ' || v_typo;
            end loop;
        end loop;
        -- Two neighbours swapped: Luhn mod 32 misses only 0 with Z
        for i in 1 .. 11 loop
            continue when substr(v_code, i, 1) = substr(v_code, i + 1, 1);
            v_swap := overlay(v_code placing substr(v_code, i + 1, 1) || substr(v_code, i, 1) from i for 2);
            if private.code_well_formed(v_swap) then
                assert substr(v_code, i, 2) in ('0Z', 'Z0'), v_code || ' ⇄ ' || v_swap;
                v_missed := v_missed + 1;
            end if;
        end loop;
    end loop;
    raise notice 'ok: codes are 12 distinct well-formed characters; every typo is caught, every swap but 0/Z (% seen)', v_missed;
end $$;

do $$
declare
    v_code text := private.new_code();
begin
    assert (select count(*) from vault.secrets where name = 'activation_code_pepper') = 1;
    assert length(private.code_digest(v_code)) = 32;
    assert private.code_digest(v_code) = private.code_digest(lower(private.format_code(v_code)));
    assert private.code_digest(v_code) <> extensions.digest(v_code, 'sha256');
    raise notice 'ok: a code''s fingerprint is keyed by the Vault secret, and blind to dashes and case';
end $$;

-- ───────────── Creating codes ─────────────

do $$
declare
    r record;
    n int := 0;
begin
    for r in select * from private.create_codes(3, 'solo', 182, '  Revendeur Sétif — oct. 2026  ') loop
        assert r.code ~ '^[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$', r.code;
        assert r.expires_on = current_date + 365;
        assert exists (select 1 from private.activation_codes where digest = private.code_digest(r.code)
                       and batch = 'Revendeur Sétif — oct. 2026' and use is null);
        assert not exists (select 1 from private.activation_codes where digest = extensions.digest(private.normalize_code(r.code), 'sha256'));
        n := n + 1;
    end loop;
    assert n = 3;
    assert (select count(*) from private.create_codes(2, 'solo', 30, 'Sans fin', p_expires_on => null) where expires_on is null) = 2;
    begin
        perform private.create_codes(0, 'solo', 30, 'Rien');
        assert false, 'zero codes';
    exception when raise_exception then null;
    end;
    raise notice 'ok: codes come out by batch, shown once as XXXX-XXXX-XXXX, stored as a fingerprint only, 12 months to use them';
end $$;

-- ───────────── A new code makes the business, and becomes its key ─────────────

create temporary table codes (name text primary key, code text);
insert into codes values
    ('benali', pg_temp.code('solo', 182, 'Épicerie Benali')),
    ('nameless', pg_temp.code('solo', 365)),
    ('renew', pg_temp.code('solo', 182)),
    ('late', pg_temp.code('solo', 30)),
    ('trial', pg_temp.code('trial', 30)),
    ('onboard', pg_temp.code('solo', 90)),
    ('revoked', pg_temp.code('solo', 30));

do $$
declare
    r  jsonb;
    b  uuid;
begin
    r := private.redeem_code('f1111111-0000-4000-8000-000000000011', (select code from codes where name = 'benali'));
    assert r ->> 'status' = 'created', r::text;
    b := (r ->> 'business_id')::uuid;
    assert (select name from public.businesses where id = b) = 'Épicerie Benali';
    assert (select role from public.memberships where business_id = b and user_id = 'f1111111-0000-4000-8000-000000000011') = 'owner';
    assert (select plan_id = 'solo' and valid_from = pg_temp.today() and valid_to = pg_temp.today() + 181 and status = 'active'
            from public.subscriptions where business_id = b);
    assert (select use = 'key' and business_id = b and redeemed_by = 'f1111111-0000-4000-8000-000000000011'
            from private.activation_codes where digest = private.code_digest((select code from codes where name = 'benali')));
    assert (select count(*) from private.payments where business_id = b and provider = 'code' and days = 182) = 1;
    raise notice 'ok: a new code makes its business, named on the code, paid for its 182 days from today, and becomes its key';
end $$;

do $$
declare
    r  jsonb;
    b  uuid;
    c  text := (select code from codes where name = 'nameless');
begin
    r := private.redeem_code('f5555555-0000-4000-8000-000000000015', c);
    assert r ->> 'status' = 'code_needs_business', r::text;
    assert not exists (select 1 from public.memberships where user_id = 'f5555555-0000-4000-8000-000000000015');
    assert (select use is null from private.activation_codes where digest = private.code_digest(c));

    r := private.redeem_code('f5555555-0000-4000-8000-000000000015', c, '  Dépôt Hamza  ', ' 0661 23 45 67 ');
    assert r ->> 'status' = 'created', r::text;
    b := (r ->> 'business_id')::uuid;
    assert (select name = 'Dépôt Hamza' and contact_phone = '0661 23 45 67' from public.businesses where id = b);
    assert (select valid_to = pg_temp.today() + 364 from public.subscriptions where business_id = b);
    raise notice 'ok: a code without a company asks for it, then makes the business with its name and phone';
end $$;

-- ───────────── The key, on the next phones ─────────────

create function pg_temp.activate(p_user uuid, p_installation text, p_key text, p_business uuid)
returns jsonb language sql as $$
    select private.activate_device(p_user, p_business, p_installation::uuid, p_key, convert_to(p_key, 'UTF8'),
                                   null, 'SM-A156B', '15', '1.0', '{}'::jsonb, 'normal', true)
$$;

do $$
declare
    benali  text := (select code from codes where name = 'benali');
    b       uuid := (select business_id from private.activation_codes where digest = private.code_digest(benali));
    r       jsonb;
begin
    -- Phone 2 (anon_2): the old phone's account had no phone left active, so it goes
    r := private.redeem_code('f2222222-0000-4000-8000-000000000012', lower(benali));
    assert r ->> 'status' = 'joined' and (r ->> 'business_id')::uuid = b, r::text;
    assert (select status from public.memberships where business_id = b and user_id = 'f2222222-0000-4000-8000-000000000012') = 'active';
    assert (select status from public.memberships where business_id = b and user_id = 'f1111111-0000-4000-8000-000000000011') = 'removed';
    assert pg_temp.activate('f2222222-0000-4000-8000-000000000012', 'c0000000-0000-4000-8000-0000000000c2', 'key-c2', b) ->> 'status' = 'activated';

    -- Phone 3 (anon_3) while phone 2 still runs: anon_2 keeps its membership, and the seat goes by transfer
    r := private.redeem_code('f3333333-0000-4000-8000-000000000013', benali);
    assert r ->> 'status' = 'joined', r::text;
    assert (select status from public.memberships where business_id = b and user_id = 'f2222222-0000-4000-8000-000000000012') = 'active';
    assert pg_temp.activate('f3333333-0000-4000-8000-000000000013', 'c0000000-0000-4000-8000-0000000000c3', 'key-c3', b) ->> 'status' = 'activated';
    assert (select status from public.devices where installation_id = 'c0000000-0000-4000-8000-0000000000c2') = 'revoked';
    assert (select count(*) from public.subscriptions where business_id = b) = 1;
    raise notice 'ok: the key brings its business to a new phone; dead anonymous accounts leave it, a running phone is transferred';
end $$;

-- ───────────── Renewals ─────────────

do $$
declare
    b      uuid := (private.create_business('f4444444-0000-4000-8000-000000000014', 'Distribution F4', 'phone-f4') ->> 'business_id')::uuid;
    renew  text := (select code from codes where name = 'renew');
    r      jsonb;
begin
    r := private.renew_with_code('f4444444-0000-4000-8000-000000000014', renew);
    assert r ->> 'status' = 'renewed' and (r ->> 'business_id')::uuid = b, r::text;
    assert (select plan_id = 'solo' and valid_to = pg_temp.today() + 30 + 182 from public.subscriptions where business_id = b);
    assert (select use = 'renewal' and business_id = b from private.activation_codes where digest = private.code_digest(renew));
    assert private.renew_with_code('f4444444-0000-4000-8000-000000000014', renew) ->> 'status' = 'code_used';
    assert private.redeem_code('f5555555-0000-4000-8000-000000000015', renew) ->> 'status' = 'code_used';
    raise notice 'ok: a renewal adds its days after the trial''s end, turns the trial paid, and is spent: never a key, never twice';

    update public.subscriptions set valid_to = pg_temp.today() - 10 where business_id = b;
    r := private.renew_with_code('f4444444-0000-4000-8000-000000000014', (select code from codes where name = 'late'));
    assert (select valid_to = pg_temp.today() - 1 + 30 from public.subscriptions where business_id = b), r::text;
    raise notice 'ok: a renewal of a subscription that has ended counts from today';

    r := private.renew_with_code('f4444444-0000-4000-8000-000000000014', (select code from codes where name = 'trial'));
    assert r ->> 'plan' = 'solo' and (select plan_id from public.subscriptions where business_id = b) = 'solo', r::text;
    raise notice 'ok: a trial code renewing a paid business keeps it paid';

    r := private.redeem_code('f4444444-0000-4000-8000-000000000014', (select code from codes where name = 'onboard'));
    assert r ->> 'status' = 'renewed' and (r ->> 'business_id')::uuid = b, r::text;
    raise notice 'ok: a new code typed in the onboarding by an account that has a business pays for it';

    assert private.renew_with_code('f4444444-0000-4000-8000-000000000014', (select code from codes where name = 'benali')) ->> 'status' = 'code_used';
    assert private.renew_with_code('f5555555-0000-4000-8000-000000000015', pg_temp.code()) ->> 'status' = 'renewed';
    assert private.renew_with_code('f1111111-0000-4000-8000-000000000011', pg_temp.code()) ->> 'status' = 'no_business';
    raise notice 'ok: a key can''t be spent as a renewal; an account without a business has nothing to renew';
end $$;

-- ───────────── Refusals ─────────────

do $$
declare
    expired  text := pg_temp.code();
    revoked  text := (select code from codes where name = 'revoked');
    unknown  text := private.format_code(private.new_code());
    typo     text;
begin
    update private.activation_codes set expires_on = pg_temp.today() - 1 where digest = private.code_digest(expired);
    assert private.redeem_code('f1111111-0000-4000-8000-000000000011', expired) ->> 'status' = 'code_expired';
    assert private.renew_with_code('f4444444-0000-4000-8000-000000000014', expired) ->> 'status' = 'code_expired';
    assert private.code_status(expired) ->> 'status' = 'expired';

    assert private.revoke_code(revoked, 'Perdu') ->> 'status' = 'revoked';
    assert private.redeem_code('f1111111-0000-4000-8000-000000000011', revoked) ->> 'status' = 'bad_code';
    assert private.code_status(revoked) ->> 'status' = 'revoked' and private.code_status(revoked) ->> 'note' = 'Perdu';

    assert private.redeem_code('f1111111-0000-4000-8000-000000000011', unknown) ->> 'status' = 'bad_code';
    assert private.code_status(unknown) ->> 'status' = 'unknown';

    typo := overlay(unknown placing case when substr(unknown, 1, 1) = 'A' then 'B' else 'A' end from 1 for 1);
    assert private.redeem_code('f1111111-0000-4000-8000-000000000011', typo) ->> 'status' = 'bad_code';
    assert private.code_status(typo) ->> 'status' = 'malformed';
    assert private.redeem_code('f1111111-0000-4000-8000-000000000011', '') ->> 'status' = 'bad_code';
    raise notice 'ok: expired, revoked, unknown and mistyped codes are refused, and code_status tells which';

    assert private.code_status((select code from codes where name = 'benali')) ->> 'status' = 'key';
    assert private.code_status((select code from codes where name = 'benali')) ->> 'business_name' = 'Épicerie Benali';
    assert private.code_status((select code from codes where name = 'renew')) ->> 'status' = 'renewal';
    raise notice 'ok: code_status names a key''s business and a spent renewal';
end $$;

do $$
declare
    c  text := pg_temp.code('solo', 30, 'Distribution Fugace');
    b  uuid;
begin
    insert into auth.users (id, is_anonymous) values ('f6666666-0000-4000-8000-000000000016', true);
    b := (private.redeem_code('f6666666-0000-4000-8000-000000000016', c) ->> 'business_id')::uuid;
    assert private.delete_account_data('f6666666-0000-4000-8000-000000000016') ->> 'status' = 'ok';
    assert (select business_id is null and use = 'key' from private.activation_codes where digest = private.code_digest(c));
    assert private.redeem_code('f5555555-0000-4000-8000-000000000015', c) ->> 'status' = 'bad_code';
    raise notice 'ok: the key of a deleted business opens nothing';
end $$;

-- ───────────── Guessing ─────────────

do $$
declare
    phone  constant uuid := 'a7000000-0000-4000-8000-000000000001';
    g      jsonb;
begin
    for i in 1 .. 4 loop
        perform private.record_code_failure(phone, null, '203.0.113.7');
    end loop;
    assert not (private.code_attempts_gate(phone, null, '203.0.113.7') ->> 'blocked')::boolean;
    perform private.record_code_failure(phone, null, '203.0.113.7');
    g := private.code_attempts_gate(phone, null, '203.0.113.7');
    assert (g ->> 'blocked')::boolean and (g ->> 'retry_at')::timestamptz between now() + interval '59 minutes' and now() + interval '1 hour', g::text;
    assert not (private.code_attempts_gate('a7000000-0000-4000-8000-000000000002', null, '203.0.113.8') ->> 'blocked')::boolean;
    raise notice 'ok: a phone may get 5 codes refused an hour, then waits for the oldest to leave the hour';

    for i in 1 .. 5 loop
        perform private.record_code_failure(null, 'f5555555-0000-4000-8000-000000000015', '203.0.113.9');
    end loop;
    assert (private.code_attempts_gate('a7000000-0000-4000-8000-000000000003', 'f5555555-0000-4000-8000-000000000015', '203.0.113.10') ->> 'blocked')::boolean;
    raise notice 'ok: so may an account, whichever phone it uses';

    for i in 1 .. 30 loop
        perform private.record_code_failure(gen_random_uuid(), null, '198.51.100.1');
    end loop;
    assert (private.code_attempts_gate(gen_random_uuid(), null, '198.51.100.1') ->> 'blocked')::boolean;
    assert not (private.code_attempts_gate(gen_random_uuid(), null, '198.51.100.2') ->> 'blocked')::boolean;
    assert not exists (select 1 from private.code_attempts where ip_digest = convert_to('198.51.100.1', 'UTF8'));
    raise notice 'ok: a connection may get 30 refused, spread over many phones; its address is kept only as a fingerprint';
end $$;

-- ───────────── The owner's view, and the e-mail sign-up's details ─────────────

do $$
declare
    row record;
begin
    select * into row from private.codes_by_batch where batch = 'Revendeur Sétif — oct. 2026';
    assert row.total = 3 and row.unused = 3 and row.keys = 0, row::text;
    -- « Tests », 182 days: benali (key), renew and the one anon_5 spent (renewals), the expired one, one unused
    select * into row from private.codes_by_batch where batch = 'Tests' and days = 182;
    assert row.total = 5 and row.keys = 1 and row.renewals = 2 and row.expired = 1 and row.unused = 1, row::text;
    raise notice 'ok: codes_by_batch counts each batch''s unused codes, keys, renewals, expired and revoked';
end $$;

do $$
declare
    r  jsonb;
begin
    insert into auth.users (id, email) values ('f7777777-0000-4000-8000-000000000017', 'f7@example.com'),
                                              ('f8888888-0000-4000-8000-000000000018', 'f8@example.com');
    r := private.create_business('f7777777-0000-4000-8000-000000000017', 'Grossiste F7', 'phone-f7', '0550 11 22 33', 'boissons', '2-5');
    assert (select contact_phone = '0550 11 22 33' and activity = 'boissons' and sales_reps = '2-5'
            from public.businesses where id = (r ->> 'business_id')::uuid), r::text;
    begin
        perform private.create_business('f8888888-0000-4000-8000-000000000018', 'X', null, null, 'autre chose');
        assert false, 'unknown activity';
    exception when check_violation then null;
    end;
    raise notice 'ok: the e-mail sign-up''s business keeps its phone, activity and sales reps, from the known lists only';
end $$;

-- ───────────── Who may touch codes ─────────────

set role authenticated;
select set_config('request.jwt.claim.sub', 'f4444444-0000-4000-8000-000000000014', false);
do $$ begin
    begin
        perform 1 from private.activation_codes;
        assert false, 'a signed-in user read the codes';
    exception when insufficient_privilege then null;
    end;
    begin
        perform private.create_codes(1, 'solo', 365, 'Pirate');
        assert false, 'a signed-in user made a code';
    exception when insufficient_privilege then null;
    end;
    begin
        perform private.redeem_code('f4444444-0000-4000-8000-000000000014', 'AAAA-AAAA-AAAA');
        assert false, 'a signed-in user redeemed a code directly';
    exception when insufficient_privilege then null;
    end;
    begin
        perform 1 from private.codes_by_batch;
        assert false, 'a signed-in user read the batches';
    exception when insufficient_privilege then null;
    end;
    update public.businesses set activity = 'frais', sales_reps = '1'
    where id = (select business_id from public.memberships where user_id = 'f4444444-0000-4000-8000-000000000014');
    assert found;
    raise notice 'ok: codes are out of the app''s reach; an owner may set the activity and sales reps';
end $$;
reset role;
set role anon;
do $$ begin
    begin
        perform private.code_status('AAAA-AAAA-AAAA');
        assert false, 'anon read a code''s status';
    exception when insufficient_privilege then null;
    end;
    raise notice 'ok: nor anonymous visitors';
end $$;
reset role;

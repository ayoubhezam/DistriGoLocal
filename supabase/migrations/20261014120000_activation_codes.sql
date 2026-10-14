-- Activation codes (docs/activation_codes.md): a code bought from DistriGo or a reseller activates DistriGo
-- without an e-mail. Its first use creates the business and makes the code its key: the same code brings the
-- business to a new phone later. A code redeemed in Compte only adds time to an existing business.
--
-- The database never holds a code: only its HMAC under a secret kept in Supabase Vault. The owner creates codes
-- in bulk from Supabase Studio (private.create_codes) and sees each one once.

-- ───────────────────────────── The business's other details ─────────────────────────────

-- The phone comes from either sign-up; activity and sales reps from the e-mail sign-up, or later from Compte.
-- Codes, not labels: the app shows them in French.
alter table public.businesses
    add column contact_phone  text check (length(contact_phone) <= 30),
    add column activity       text check (activity in ('alimentation', 'boissons', 'frais', 'hygiene', 'droguerie',
                                                       'materiaux', 'pharmacie', 'autre')),
    add column sales_reps     text check (sales_reps in ('1', '2-5', '6-10', '10+'));

grant update (contact_phone, activity, sales_reps) on public.businesses to authenticated;

-- The e-mail sign-up's business, now with those details. Same rules as before (one per account, one trial per
-- phone); the new arguments default to null, so existing calls stand.
drop function private.create_business(uuid, text, text);
create function private.create_business(
    p_user             uuid,
    p_name             text,
    p_android_id_hint  text,
    p_contact_phone    text default null,
    p_activity         text default null,
    p_sales_reps       text default null
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_existing  uuid;
    v_business  public.businesses;
    v_today     date;
    v_trial     boolean := true;
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

    if p_android_id_hint is not null then
        insert into private.trial_claims (android_id_hint, business_id) values (p_android_id_hint, v_business.id)
        on conflict (android_id_hint) do nothing;
        v_trial := found;
    end if;

    v_today := private.local_today(v_business.time_zone);
    insert into public.subscriptions (business_id, plan_id, valid_from, valid_to, note)
    select v_business.id, 'trial', v_today,
           case when v_trial then v_today + p.trial_days else v_today - 1 end,
           case when v_trial then null else 'Essai déjà utilisé sur ce téléphone' end
    from public.plans p where p.id = 'trial';

    return jsonb_build_object('status', 'created', 'business_id', v_business.id, 'trial', v_trial);
end
$$;

-- ───────────────────────────── A code's characters ─────────────────────────────

-- Crockford's base32: no I, L, O or U, so no character reads as another. 11 random characters (55 bits) and a
-- Luhn mod 32 check character, which catches every single wrong character and almost every swap of two
-- neighbours: the app refuses a typo before it costs an attempt (ui/onboarding/CodePages.kt checks the same).

-- As typed: capitals, O read as 0, I and L as 1, and anything else dropped (spaces, dashes).
create function private.normalize_code(p_text text) returns text
language sql immutable set search_path = '' as $$
    select regexp_replace(translate(upper(coalesce(p_text, '')), 'OIL', '011'), '[^0-9A-HJKMNP-TV-Z]', '', 'g')
$$;

-- Luhn mod 32: the check character that follows p_payload.
create function private.code_check_char(p_payload text) returns text
language plpgsql immutable set search_path = '' as $$
declare
    v_factor  int := 2;
    v_sum     int := 0;
    v_addend  int;
begin
    for i in reverse length(p_payload) .. 1 loop
        v_addend := v_factor * (strpos('0123456789ABCDEFGHJKMNPQRSTVWXYZ', substr(p_payload, i, 1)) - 1);
        v_sum := v_sum + v_addend / 32 + v_addend % 32;
        v_factor := 3 - v_factor;
    end loop;
    return substr('0123456789ABCDEFGHJKMNPQRSTVWXYZ', (32 - v_sum % 32) % 32 + 1, 1);
end
$$;

-- Twelve characters of the alphabet, the last one checking the first eleven. Takes a normalized code.
create function private.code_well_formed(p_code text) returns boolean
language sql immutable set search_path = '' as $$
    select p_code ~ '^[0-9A-HJKMNP-TV-Z]{12}$' and private.code_check_char(left(p_code, 11)) = right(p_code, 1)
$$;

create function private.format_code(p_code text) returns text
language sql immutable set search_path = '' as $$
    select substr(p_code, 1, 4) || '-' || substr(p_code, 5, 4) || '-' || substr(p_code, 9, 4)
$$;

create function private.new_code() returns text
language plpgsql volatile set search_path = '' as $$
declare
    v_bits     bit(64) := ('x' || encode(extensions.gen_random_bytes(8), 'hex'))::bit(64);
    v_payload  text := '';
begin
    for i in 0 .. 10 loop
        v_payload := v_payload
            || substr('0123456789ABCDEFGHJKMNPQRSTVWXYZ', substring(v_bits from i * 5 + 1 for 5)::int + 1, 1);
    end loop;
    return v_payload || private.code_check_char(v_payload);
end
$$;

-- The secret the fingerprints are keyed with: made here, kept in Vault, never shown. Without it, a copy of the
-- codes table can't be tried against offline.
do $$
begin
    if not exists (select 1 from vault.secrets where name = 'activation_code_pepper') then
        perform vault.create_secret(encode(extensions.gen_random_bytes(32), 'hex'), 'activation_code_pepper',
                                    'Keys the activation codes'' fingerprints (private.code_digest). Never shown.');
    end if;
end
$$;

-- What the table keeps of a code: HMAC-SHA256 of its normalized characters.
create function private.code_digest(p_code text) returns bytea
language sql stable security definer set search_path = '' as $$
    select extensions.hmac(convert_to(private.normalize_code(p_code), 'UTF8'), convert_to(s.decrypted_secret, 'UTF8'), 'sha256')
    from vault.decrypted_secrets s
    where s.name = 'activation_code_pepper'
$$;

-- ───────────────────────────── Codes ─────────────────────────────

create table private.activation_codes (
    id             uuid primary key default gen_random_uuid(),
    digest         bytea not null unique,
    plan_id        text not null references public.plans (id),
    days           int  not null check (days between 1 and 1100),
    -- Set when the owner made the code for one business; otherwise the merchant names it.
    business_name  text check (length(trim(business_name)) between 1 and 120),
    -- Who the codes were made for: « Revendeur Sétif — oct. 2026 ».
    batch          text not null check (length(trim(batch)) between 1 and 120),
    note           text,
    -- An unused code can't be redeemed after this day (Algiers); null never expires.
    expires_on     date,
    created_at     timestamptz not null default now(),
    -- key: it created the business, and brings it to a new phone. renewal: it added time, and that's all.
    use            text check (use in ('key', 'renewal')),
    business_id    uuid references public.businesses (id) on delete set null,
    redeemed_at    timestamptz,
    redeemed_by    uuid references auth.users (id) on delete set null,
    revoked_at     timestamptz,
    check ((use is null) = (redeemed_at is null))
);
create index activation_codes_business on private.activation_codes (business_id);
create index activation_codes_batch on private.activation_codes (batch);

-- Codes refused, to stop guessing: per installation, account and connection (a fingerprint of the IP).
create table private.code_attempts (
    id               bigint generated always as identity primary key,
    installation_id  uuid,
    user_id          uuid,
    ip_digest        bytea,
    at               timestamptz not null default now()
);
create index code_attempts_at on private.code_attempts (at);

-- ───────────────────────────── For the owner, in Supabase Studio ─────────────────────────────

-- p_count codes of one batch, shown once as XXXX-XXXX-XXXX: copy them out, they can't be shown again.
--   select * from private.create_codes(50, 'solo', 182, 'Revendeur Sétif — oct. 2026');
--   select * from private.create_codes(1, 'solo', 365, 'Vente directe', p_business_name => 'Épicerie Benali');
-- Unused codes expire after 12 months unless p_expires_on says otherwise (null: never).
create function private.create_codes(
    p_count          int,
    p_plan           text,
    p_days           int,
    p_batch          text,
    p_business_name  text default null,
    p_expires_on     date default (current_date + 365),
    p_note           text default null
) returns table (code text, expires_on date)
language plpgsql volatile security definer set search_path = '' as $$
#variable_conflict use_column
declare
    v_code text;
begin
    if p_count is null or p_count not between 1 and 1000 then
        raise exception 'create_codes: p_count must be between 1 and 1000';
    end if;
    for i in 1 .. p_count loop
        loop
            v_code := private.new_code();
            begin
                insert into private.activation_codes (digest, plan_id, days, business_name, batch, note, expires_on)
                values (private.code_digest(v_code), p_plan, p_days, nullif(trim(p_business_name), ''), trim(p_batch),
                        p_note, p_expires_on);
                exit;
            exception when unique_violation then
                -- The same 55 bits twice: astronomically unlikely, but then draw again
                null;
            end;
        end loop;
        code := private.format_code(v_code);
        expires_on := p_expires_on;
        return next;
    end loop;
end
$$;

-- A merchant calls about a code: what it is, and what became of it.
create function private.code_status(p_code text) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
    v       private.activation_codes;
    v_name  text;
begin
    if not private.code_well_formed(private.normalize_code(p_code)) then
        return jsonb_build_object('status', 'malformed');
    end if;
    select * into v from private.activation_codes c where c.digest = private.code_digest(p_code);
    if v.id is null then
        return jsonb_build_object('status', 'unknown');
    end if;
    select b.name into v_name from public.businesses b where b.id = v.business_id;
    return jsonb_build_object(
        'status', case when v.revoked_at is not null then 'revoked'
                       when v.use is not null then v.use
                       when v.expires_on < private.local_today('Africa/Algiers') then 'expired'
                       else 'unused' end,
        'batch', v.batch, 'plan', v.plan_id, 'days', v.days, 'expires_on', v.expires_on,
        'business_id', v.business_id, 'business_name', coalesce(v_name, v.business_name),
        'redeemed_at', v.redeemed_at, 'revoked_at', v.revoked_at, 'note', v.note);
end
$$;

-- Blocks a code, e.g. a key that leaked: it opens nothing any more. The business it made keeps its subscription.
create function private.revoke_code(p_code text, p_reason text default null) returns jsonb
language plpgsql security definer set search_path = '' as $$
begin
    update private.activation_codes c
    set revoked_at = coalesce(c.revoked_at, now()),
        note = concat_ws(' — ', c.note, nullif(trim(p_reason), ''))
    where c.digest = private.code_digest(p_code);
    return jsonb_build_object('status', case when found then 'revoked' else 'unknown' end);
end
$$;

-- Each batch at a glance, to settle accounts with a reseller.
create view private.codes_by_batch as
select c.batch,
       c.plan_id,
       c.days,
       count(*)                                                                    as total,
       count(*) filter (where c.use is null and c.revoked_at is null
                          and (c.expires_on is null or c.expires_on >= current_date)) as unused,
       count(*) filter (where c.use = 'key')                                       as keys,
       count(*) filter (where c.use = 'renewal')                                   as renewals,
       count(*) filter (where c.use is null and c.revoked_at is null and c.expires_on < current_date) as expired,
       count(*) filter (where c.revoked_at is not null)                            as revoked,
       min(c.created_at)                                                           as created_at
from private.activation_codes c
group by c.batch, c.plan_id, c.days
order by min(c.created_at) desc;

-- ───────────────────────────── What the Edge Functions call ─────────────────────────────

-- A code that pays for an existing business: its days, from the subscription's end or from today if it has
-- ended (private.extend_subscription, the code as the payment's reference). A trial code keeps a paid plan.
create function private.apply_code_renewal(p_code private.activation_codes, p_business uuid, p_user uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_plan    text;
    v_result  jsonb;
begin
    select case when p_code.plan_id = 'trial' then s.plan_id else p_code.plan_id end into v_plan
    from public.subscriptions s where s.business_id = p_business;
    v_result := private.extend_subscription(p_business, p_code.days, v_plan, 'Code, lot « ' || p_code.batch || ' »',
                                            'code', p_code.id::text);
    update private.activation_codes c
    set use = 'renewal', business_id = p_business, redeemed_at = now(), redeemed_by = p_user
    where c.id = p_code.id;
    return jsonb_build_object('status', 'renewed', 'business_id', p_business, 'valid_to', v_result -> 'valid_to',
                              'plan', v_plan, 'days', p_code.days);
end
$$;

-- The code an activation brings (license-activate's activation_code), in its transaction. Returns
--   created  {business_id, plan, days}  a new code: the business is made with it, and the code is its key
--   joined   {business_id}              a key: its business, on this phone too
--   renewed  {business_id, valid_to}    a new code for an account that already has a business: time added
--   code_needs_business                 a new code without a company, and none given: ask its name and phone
--   bad_code, code_expired, code_used, choose_business
-- The caller then activates the phone in that business, and rolls everything back if that fails.
create function private.redeem_code(
    p_user           uuid,
    p_code           text,
    p_business_name  text default null,
    p_contact_phone  text default null
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_code      private.activation_codes;
    v_members   uuid[];
    v_name      text;
    v_business  public.businesses;
    v_today     date;
begin
    if not private.code_well_formed(private.normalize_code(p_code)) then
        return jsonb_build_object('status', 'bad_code');
    end if;
    -- Locked: two phones redeeming one new code make one business, not two.
    select * into v_code from private.activation_codes c where c.digest = private.code_digest(p_code) for update;
    if v_code.id is null or v_code.revoked_at is not null then
        return jsonb_build_object('status', 'bad_code');
    end if;

    if v_code.use = 'key' then
        -- Its business was deleted with its account: the key opens nothing.
        if v_code.business_id is null then
            return jsonb_build_object('status', 'bad_code');
        end if;
        insert into public.memberships (business_id, user_id, role) values (v_code.business_id, p_user, 'owner')
        on conflict (business_id, user_id) do update set status = 'active';
        -- The business's other anonymous members without an active phone: their session went with their phone.
        update public.memberships m set status = 'removed'
        where m.business_id = v_code.business_id and m.user_id <> p_user and m.status = 'active'
          and exists (select 1 from auth.users u where u.id = m.user_id and u.is_anonymous)
          and not exists (select 1 from public.devices d
                          where d.user_id = m.user_id and d.business_id = m.business_id and d.status = 'active');
        return jsonb_build_object('status', 'joined', 'business_id', v_code.business_id);
    elsif v_code.use = 'renewal' then
        return jsonb_build_object('status', 'code_used');
    end if;

    if v_code.expires_on is not null and private.local_today('Africa/Algiers') > v_code.expires_on then
        return jsonb_build_object('status', 'code_expired');
    end if;

    -- The same lock as create_business: one account makes one business, however many requests race.
    perform pg_advisory_xact_lock(hashtext('create_business:' || p_user::text));
    select array_agg(m.business_id) into v_members
    from public.memberships m where m.user_id = p_user and m.status = 'active';
    if cardinality(v_members) > 1 then
        return jsonb_build_object('status', 'choose_business');
    elsif cardinality(v_members) = 1 then
        -- Already a business (an expired trial, say): the code pays for it.
        return private.apply_code_renewal(v_code, v_members[1], p_user);
    end if;

    v_name := coalesce(v_code.business_name, nullif(trim(p_business_name), ''));
    if v_name is null then
        return jsonb_build_object('status', 'code_needs_business');
    end if;
    insert into public.businesses (name, created_by, contact_phone)
    values (v_name, p_user, nullif(trim(p_contact_phone), ''))
    returning * into v_business;
    insert into public.memberships (business_id, user_id, role) values (v_business.id, p_user, 'owner');
    -- Ends yesterday, so extend_subscription counts the code's days from today.
    v_today := private.local_today(v_business.time_zone);
    insert into public.subscriptions (business_id, plan_id, valid_from, valid_to)
    values (v_business.id, v_code.plan_id, v_today, v_today - 1);
    perform private.extend_subscription(v_business.id, v_code.days, v_code.plan_id,
                                        'Code, lot « ' || v_code.batch || ' »', 'code', v_code.id::text);
    update private.activation_codes c
    set use = 'key', business_id = v_business.id, redeemed_at = now(), redeemed_by = p_user
    where c.id = v_code.id;
    return jsonb_build_object('status', 'created', 'business_id', v_business.id, 'plan', v_code.plan_id,
                              'days', v_code.days);
end
$$;

-- « Saisir un code » in Compte (code-redeem): a new code adds its days to the account's business.
-- Returns renewed {business_id, valid_to, plan, days}, or bad_code, code_expired, code_used, no_business,
-- choose_business, not_member.
create function private.renew_with_code(p_user uuid, p_code text, p_business uuid default null) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_code      private.activation_codes;
    v_members   uuid[];
    v_business  uuid := p_business;
begin
    if not private.code_well_formed(private.normalize_code(p_code)) then
        return jsonb_build_object('status', 'bad_code');
    end if;
    select * into v_code from private.activation_codes c where c.digest = private.code_digest(p_code) for update;
    if v_code.id is null or v_code.revoked_at is not null then
        return jsonb_build_object('status', 'bad_code');
    elsif v_code.use is not null then
        return jsonb_build_object('status', 'code_used');
    elsif v_code.expires_on is not null and private.local_today('Africa/Algiers') > v_code.expires_on then
        return jsonb_build_object('status', 'code_expired');
    end if;

    if v_business is null then
        select array_agg(m.business_id) into v_members
        from public.memberships m where m.user_id = p_user and m.status = 'active';
        if coalesce(cardinality(v_members), 0) = 0 then
            return jsonb_build_object('status', 'no_business');
        elsif cardinality(v_members) > 1 then
            return jsonb_build_object('status', 'choose_business');
        end if;
        v_business := v_members[1];
    elsif not exists (select 1 from public.memberships m
                      where m.business_id = v_business and m.user_id = p_user and m.status = 'active') then
        return jsonb_build_object('status', 'not_member');
    end if;
    return private.apply_code_renewal(v_code, v_business, p_user);
end
$$;

-- Whether this phone, account or connection may try another code: 5 refusals an hour per installation or
-- account, 30 per connection (a mobile network's address is shared by many). retry_at: when the oldest of
-- those refusals leaves the hour.
create function private.code_attempts_gate(p_installation uuid, p_user uuid, p_ip text) returns jsonb
language sql stable security definer set search_path = '' as $$
    with recent as (
        select a.at,
               coalesce(a.installation_id = p_installation, false) or coalesce(a.user_id = p_user, false) as mine,
               coalesce(a.ip_digest = extensions.digest(p_ip, 'sha256'), false) as same_ip
        from private.code_attempts a
        where a.at > now() - interval '1 hour'
    ), counts as (
        select count(*) filter (where mine) as mine, min(at) filter (where mine) as first_mine,
               count(*) filter (where same_ip) as ip, min(at) filter (where same_ip) as first_ip
        from recent
    )
    select case
        when mine >= 5 then jsonb_build_object('blocked', true, 'retry_at', first_mine + interval '1 hour')
        when ip >= 30 then jsonb_build_object('blocked', true, 'retry_at', first_ip + interval '1 hour')
        else jsonb_build_object('blocked', false)
    end
    from counts
$$;

-- A refused code, counted against the phone, the account and the connection. Older than a day is forgotten.
create function private.record_code_failure(p_installation uuid, p_user uuid, p_ip text) returns void
language sql security definer set search_path = '' as $$
    delete from private.code_attempts where at < now() - interval '1 day';
    insert into private.code_attempts (installation_id, user_id, ip_digest)
    values (p_installation, p_user, extensions.digest(p_ip, 'sha256'));
$$;

-- ───────────────────────────── Privileges ─────────────────────────────

-- As for the rest of private: only the Edge Functions (as postgres) and Supabase Studio reach any of it.
revoke execute on all functions in schema private from public, anon, authenticated;
grant execute on function private.is_member(uuid), private.has_role(uuid, text[]), private.shares_business(uuid)
    to authenticated;
revoke all on private.activation_codes, private.code_attempts, private.codes_by_batch from public, anon, authenticated;

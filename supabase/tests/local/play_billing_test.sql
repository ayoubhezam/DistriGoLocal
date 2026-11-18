-- Google Play Billing, the database side (migration 20261118140000_play_billing.sql): recording a purchase, the
-- license running to the later of the subscription's end and Play's, refunds and cancellations, renewals counted once.
-- Run by run.sh after the other tests. Every check prints "ok: …"; the first failure stops the script.

\set ON_ERROR_STOP on
set client_min_messages = notice;

insert into auth.users (id, email) values
    ('b1000000-0000-4000-8000-0000000000b1', 'buyer@example.com'),
    ('b2000000-0000-4000-8000-0000000000b2', 'other-buyer@example.com');

create function pg_temp.today() returns date language sql as $$ select private.local_today('Africa/Algiers') $$;

-- A purchase as play-verify records it: solo-monthly, 30 days
create function pg_temp.record(p_token text, p_business uuid, p_state text, p_expires timestamptz,
                               p_order text default null, p_linked text default null) returns jsonb
language sql as $$
    select private.record_play_subscription(p_token, p_business, null, 'distroniq_solo', 'solo-monthly', 'solo', 30,
                                            p_state, p_expires, p_linked, p_order, false)
$$;

-- The license's last day for a business, as issue_license computes it
create function pg_temp.last_day(p_business uuid) returns date
language sql as $$
    select greatest(s.valid_to, coalesce(private.play_valid_to(p_business), s.valid_to))
    from public.subscriptions s where s.business_id = p_business
$$;

do $$
declare
    r     jsonb;
    b     uuid;
    other uuid;
    dev   uuid;
    lic   jsonb;
begin
    -- A business on its trial (30 days), and its phone
    r := private.create_business('b1000000-0000-4000-8000-0000000000b1', 'Dépôt Play', 'phone-play', null, null, null, true);
    b := (r ->> 'business_id')::uuid;
    r := private.activate_device('b1000000-0000-4000-8000-0000000000b1', null, 'b1000000-0000-4000-8000-0000000000d1',
                                 'key-play', '\x01', 'phone-play', 'SM-M346B', '16', '1.0', '{}'::jsonb, 'normal', false, null);
    dev := (r ->> 'device_id')::uuid;
    r := private.create_business('b2000000-0000-4000-8000-0000000000b2', 'Dépôt Autre', 'phone-other', null, null, null, true);
    other := (r ->> 'business_id')::uuid;

    assert private.play_business_of(private.play_account_id(b)) = b;
    assert length(private.play_account_id(b)) = 64 and private.play_account_id(b) <> b::text;
    raise notice 'ok: a purchase names its business by a hash of its id, which leads back to it';

    -- Bought for 40 days ahead: past the trial's 30, so the license runs to Play's end, and the plan is paid
    r := pg_temp.record('tok-1', b, 'SUBSCRIPTION_STATE_ACTIVE', now() + interval '40 days', 'GPA.0001-0001-0001-00001');
    assert r ->> 'status' = 'recorded', r::text;
    assert (r ->> 'valid_to')::date = (now() + interval '40 days')::date, r::text;
    assert (select plan_id from public.subscriptions where business_id = b) = 'solo';
    lic := private.issue_license(dev);
    assert (lic ->> 'vt')::bigint = private.day_end((now() + interval '40 days')::date, 'Africa/Algiers'), lic::text;
    raise notice 'ok: an active Play subscription past the trial carries the license to its end, on the paid plan';

    assert (select count(*) from private.payments where provider = 'google_play' and business_id = b) = 1;
    r := pg_temp.record('tok-1', b, 'SUBSCRIPTION_STATE_ACTIVE', now() + interval '40 days', 'GPA.0001-0001-0001-00001');
    assert (select count(*) from private.payments where provider = 'google_play' and business_id = b) = 1;
    r := pg_temp.record('tok-1', b, 'SUBSCRIPTION_STATE_ACTIVE', now() + interval '70 days', 'GPA.0001-0001-0001-00001..0');
    assert (select count(*) from private.payments where provider = 'google_play' and business_id = b) = 2;
    assert pg_temp.last_day(b) = (now() + interval '70 days')::date;
    raise notice 'ok: each Google order is one payment, however often it is reported; a renewal moves the end';

    -- A code adds 200 days to the subscription itself: later than Play's, so it wins; no stacking
    perform private.extend_subscription(b, 200, 'solo', null, 'code', 'play-test-code');
    assert pg_temp.last_day(b) = (select valid_to from public.subscriptions where business_id = b);
    assert pg_temp.last_day(b) < (now() + interval '70 days')::date + 200;
    raise notice 'ok: with a code and Play together, the later end wins and they do not add up';
    update public.subscriptions set valid_to = pg_temp.today() + 29 where business_id = b;

    -- Cancelled (will not renew): the paid time still runs
    r := pg_temp.record('tok-1', null, 'SUBSCRIPTION_STATE_CANCELED', now() + interval '70 days');
    assert pg_temp.last_day(b) = (now() + interval '70 days')::date;
    raise notice 'ok: a cancelled subscription keeps the time already paid';

    -- Refunded and revoked (Google reports it expired, now): back to the subscription's own end
    r := pg_temp.record('tok-1', null, 'SUBSCRIPTION_STATE_EXPIRED', now() - interval '1 minute');
    assert pg_temp.last_day(b) = pg_temp.today() + 29, pg_temp.last_day(b)::text;
    raise notice 'ok: a revoked or expired subscription gives nothing more, and the subscription''s own end stays';

    -- On hold (payment failed past the grace period): nothing
    r := pg_temp.record('tok-2', b, 'SUBSCRIPTION_STATE_ON_HOLD', now() + interval '20 days');
    assert private.play_valid_to(b) is null;
    raise notice 'ok: a subscription on hold gives nothing';

    -- Resubscribed: the new token replaces the old one
    r := pg_temp.record('tok-3', b, 'SUBSCRIPTION_STATE_ACTIVE', now() + interval '50 days', 'GPA.0003', 'tok-2');
    assert (select state from private.play_subscriptions where purchase_token = 'tok-2') = 'SUPERSEDED';
    assert pg_temp.last_day(b) = (now() + interval '50 days')::date;
    raise notice 'ok: a new purchase token linked to an old one supersedes it';

    -- Someone else's business cannot take a token that is already this one's
    r := pg_temp.record('tok-3', other, 'SUBSCRIPTION_STATE_ACTIVE', now() + interval '50 days');
    assert r ->> 'status' = 'other_business', r::text;
    assert (select business_id from private.play_subscriptions where purchase_token = 'tok-3') = b;
    assert private.play_valid_to(other) is null;
    raise notice 'ok: a purchase already recorded for one business cannot be moved to another';

    -- A notification for a token nobody verified, naming no business: nothing written
    r := pg_temp.record('tok-unknown', null, 'SUBSCRIPTION_STATE_ACTIVE', now() + interval '30 days');
    assert r ->> 'status' = 'unknown_token', r::text;
    assert not exists (select 1 from private.play_subscriptions where purchase_token = 'tok-unknown');
    raise notice 'ok: a notification for an unknown purchase naming no business writes nothing';

    perform private.play_acknowledged('tok-3');
    assert (select acknowledged from private.play_subscriptions where purchase_token = 'tok-3');
    raise notice 'ok: an acknowledged purchase is marked so';
end $$;

do $$
declare
    r jsonb;
    b uuid;
begin
    -- A refused trial (no grace) bought on Play: the grace comes back, as with a code
    insert into auth.users (id, email) values ('b3000000-0000-4000-8000-0000000000b3', 'refused@example.com');
    r := private.create_business('b3000000-0000-4000-8000-0000000000b3', 'Dépôt Refusé', 'phone-refused', null, null, null, false);
    b := (r ->> 'business_id')::uuid;
    assert (select grace_days from public.subscriptions where business_id = b) = 0;
    r := pg_temp.record('tok-refused', b, 'SUBSCRIPTION_STATE_ACTIVE', now() + interval '30 days', 'GPA.0009');
    assert (select grace_days is null and plan_id = 'solo' from public.subscriptions where business_id = b);
    raise notice 'ok: a Play purchase ends a refused trial''s « no grace », as a code does';
end $$;

do $$
begin
    assert not has_function_privilege('anon', 'private.record_play_subscription(text, uuid, uuid, text, text, text, int, text, timestamptz, text, text, boolean)', 'execute');
    assert not has_function_privilege('authenticated', 'private.play_valid_to(uuid)', 'execute');
    raise notice 'ok: the Play functions cannot be called from the app''s roles';
end $$;

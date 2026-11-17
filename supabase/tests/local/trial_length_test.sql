-- A trial is plans.trial_days days, today included (migration 20261117120000_trial_thirty_days.sql). Run by run.sh
-- after the other tests. Every check prints "ok: …"; the first failure stops the script.

\set ON_ERROR_STOP on
set client_min_messages = notice;

insert into auth.users (id, email) values
    ('81000000-0000-4000-8000-000000000001', 'trial-length@example.com'),
    ('82000000-0000-4000-8000-000000000002', 'trial-length-again@example.com');

do $$
declare
    r     jsonb;
    s     public.subscriptions;
    today date := private.local_today('Africa/Algiers');
    days  int  := (select trial_days from public.plans where id = 'trial');
begin
    assert days = 30, days::text;
    r := private.create_business('81000000-0000-4000-8000-000000000001', 'Distribution T', 'phone-t');
    assert (r ->> 'trial')::boolean, r::text;
    select * into s from public.subscriptions where business_id = (r ->> 'business_id')::uuid;
    assert s.valid_from = today and s.valid_to = today + 29, row_to_json(s)::text;
    -- Both ends included, as issue_license reads valid_to: exactly 30 days
    assert s.valid_to - s.valid_from + 1 = 30, row_to_json(s)::text;
    raise notice 'ok: a new trial runs 30 days, today included (from today through today + 29)';

    -- A refused trial is unchanged: it ended yesterday
    r := private.create_business('82000000-0000-4000-8000-000000000002', 'Distribution T2', 'phone-t');
    assert not (r ->> 'trial')::boolean, r::text;
    select * into s from public.subscriptions where business_id = (r ->> 'business_id')::uuid;
    assert s.valid_to = today - 1 and s.grace_days = 0, row_to_json(s)::text;
    raise notice 'ok: a refused trial still ends yesterday, with no grace';
end $$;

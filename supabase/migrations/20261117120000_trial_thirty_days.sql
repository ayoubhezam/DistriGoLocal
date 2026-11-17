-- A trial lasted 31 days: valid_to is inclusive (issue_license ends it at 23:59:59 on that day), and create_business set
-- it to today + trial_days, so the app showed « Durée : 31 jours » beside « Il reste 30 jours » (docs/backlog.md,
-- 2026-11-03). A trial is now trial_days days, today included, as a code of N days already is (extend_subscription).
-- Trials already running keep the end they were given.

-- ── create_business: as 20261103120000_refused_trial_no_grace.sql, with the trial's end one day earlier ──
create or replace function private.create_business(
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
    insert into public.subscriptions (business_id, plan_id, valid_from, valid_to, note, grace_days)
    select v_business.id, 'trial', v_today,
           -- Both ends count: a 30-day trial runs from today through today + 29 (it was today + 30, 31 days)
           case when v_trial then v_today + p.trial_days - 1 else v_today - 1 end,
           case when v_trial then null else 'Essai déjà utilisé sur ce téléphone' end,
           -- No grace after a refused trial: it was never a subscription that ran out
           case when v_trial then null else 0 end
    from public.plans p where p.id = 'trial';

    return jsonb_build_object('status', 'created', 'business_id', v_business.id, 'trial', v_trial);
end
$$;

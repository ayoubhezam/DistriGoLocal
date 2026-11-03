-- A trial refused because the phone already had one (create_business) left a subscription that ended yesterday,
-- and issue_license added the plan's grace after it: each new account on that phone could record for 3 more days,
-- again and again (seen on the owner's phone, 2026-11-03). Such a subscription now has no grace: the license ends
-- at once and the app opens read-only, asking for a code. A code or a payment that extends it (extend_subscription)
-- gives the plan's grace back, so a paying customer is treated as any other.
--
-- The marker is the note create_business writes, « Essai déjà utilisé sur ce téléphone ».

-- ── create_business: as 20261014120000_activation_codes.sql, with grace_days 0 when the trial is refused ──
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
           case when v_trial then v_today + p.trial_days else v_today - 1 end,
           case when v_trial then null else 'Essai déjà utilisé sur ce téléphone' end,
           -- No grace after a refused trial: it was never a subscription that ran out
           case when v_trial then null else 0 end
    from public.plans p where p.id = 'trial';

    return jsonb_build_object('status', 'created', 'business_id', v_business.id, 'trial', v_trial);
end
$$;

-- ── extend_subscription: as 20261010120000_accounts_and_licenses.sql, giving a refused trial's grace back ──
create or replace function private.extend_subscription(
    p_business      uuid,
    p_days          int,
    p_plan          text default 'solo',
    p_note          text default null,
    p_provider      text default 'manual',
    p_external_ref  text default null
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_sub   public.subscriptions;
    v_zone  text;
    v_from  date;
begin
    select * into v_sub from public.subscriptions s where s.business_id = p_business for update;
    if v_sub.business_id is null then
        return jsonb_build_object('status', 'unknown_business');
    end if;
    insert into private.payments (business_id, provider, external_ref, days, plan_id, note)
    values (p_business, p_provider, p_external_ref, p_days, p_plan, p_note)
    on conflict (provider, external_ref) do nothing;
    if not found then
        return jsonb_build_object('status', 'already_applied', 'valid_to', v_sub.valid_to);
    end if;
    select b.time_zone into v_zone from public.businesses b where b.id = p_business;
    v_from := greatest(v_sub.valid_to, private.local_today(v_zone) - 1);
    update public.subscriptions s
    set plan_id = p_plan, status = 'active', valid_to = v_from + p_days, note = coalesce(p_note, s.note),
        -- A refused trial's « no grace » (create_business) ends with the first payment; any other value stays
        grace_days = case when s.note = 'Essai déjà utilisé sur ce téléphone' and s.grace_days = 0 then null
                          else s.grace_days end
    where s.business_id = p_business;
    return jsonb_build_object('status', 'extended', 'valid_to', v_from + p_days);
end
$$;

-- ── Businesses already created this way ──
update public.subscriptions
set grace_days = 0
where note = 'Essai déjà utilisé sur ce téléphone' and grace_days is null;

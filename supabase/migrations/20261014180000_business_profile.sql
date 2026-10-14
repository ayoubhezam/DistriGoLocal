-- « Informations de l'entreprise » in Compte (docs/activation_codes.md, step C5): the business's name, phone,
-- activity and sales reps, which the app reads and changes through the business-profile Edge Function.
-- Owners and managers change them, as the businesses' RLS policies already allow; agents only read.

-- The caller's business: the one named, or their only one. {status: ok, business_id, role}, or no_business,
-- choose_business (several, and none named), not_member (the one named is not theirs).
create function private.business_of(p_user uuid, p_business uuid default null) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
    v_count     int;
    v_business  uuid;
    v_role      text;
begin
    select count(*) into v_count
    from public.memberships m
    where m.user_id = p_user and m.status = 'active' and (p_business is null or m.business_id = p_business);
    if v_count = 0 then
        return jsonb_build_object('status', case when p_business is null then 'no_business' else 'not_member' end);
    elsif v_count > 1 then
        return jsonb_build_object('status', 'choose_business');
    end if;
    select m.business_id, m.role into v_business, v_role
    from public.memberships m
    where m.user_id = p_user and m.status = 'active' and (p_business is null or m.business_id = p_business);
    return jsonb_build_object('status', 'ok', 'business_id', v_business, 'role', v_role);
end
$$;

-- The profile as Compte shows it: {status: ok, business_id, name, contact_phone, activity, sales_reps, can_edit},
-- or business_of's refusals.
create function private.business_profile(p_user uuid, p_business uuid default null) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
    v_member  jsonb := private.business_of(p_user, p_business);
begin
    if v_member ->> 'status' <> 'ok' then
        return v_member;
    end if;
    return (
        select jsonb_build_object(
            'status', 'ok',
            'business_id', b.id,
            'name', b.name,
            'contact_phone', b.contact_phone,
            'activity', b.activity,
            'sales_reps', b.sales_reps,
            'can_edit', v_member ->> 'role' in ('owner', 'manager'))
        from public.businesses b
        where b.id = (v_member ->> 'business_id')::uuid
    );
end
$$;

-- Changes what p_changes names, and nothing else: name, contact_phone (null or blank clears it), activity,
-- sales_reps. The Edge Function checks the values; the table's checks stand behind it. Then answers as
-- business_profile, or business_of's refusals, or not_allowed for an agent.
create function private.update_business_profile(p_user uuid, p_changes jsonb, p_business uuid default null) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare
    v_member  jsonb := private.business_of(p_user, p_business);
    v_id      uuid;
begin
    if v_member ->> 'status' <> 'ok' then
        return v_member;
    elsif v_member ->> 'role' not in ('owner', 'manager') then
        return jsonb_build_object('status', 'not_allowed');
    end if;
    v_id := (v_member ->> 'business_id')::uuid;
    update public.businesses b set
        name          = case when p_changes ? 'name' then trim(p_changes ->> 'name') else b.name end,
        contact_phone = case when p_changes ? 'contact_phone' then nullif(trim(p_changes ->> 'contact_phone'), '')
                             else b.contact_phone end,
        activity      = case when p_changes ? 'activity' then p_changes ->> 'activity' else b.activity end,
        sales_reps    = case when p_changes ? 'sales_reps' then p_changes ->> 'sales_reps' else b.sales_reps end
    where b.id = v_id;
    return private.business_profile(p_user, v_id);
end
$$;

-- Only the Edge Functions (as postgres or service_role) run them. Revoked by name, as the earlier migrations do:
-- the private schema's default privileges cannot take back the EXECUTE every new function gives PUBLIC.
revoke execute on function private.business_of(uuid, uuid), private.business_profile(uuid, uuid),
    private.update_business_profile(uuid, jsonb, uuid) from public, anon, authenticated;

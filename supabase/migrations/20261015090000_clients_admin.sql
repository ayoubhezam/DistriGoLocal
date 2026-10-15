-- C4, « Comptes clients » (docs/activation_codes.md): from DistriGo's « Administration », through codes-admin, the
-- owner finds a client's business, suspends or reactivates its subscription, or deletes it.

-- The businesses matching p_query: a part of the name, the contact phone's digits (4 or more), a part of a
-- member's e-mail, or one of the business's activation codes. The latest first; when p_query is empty, the 30
-- latest. Each with its subscription, its active phones and when one was last seen, the owner's e-mail (none for
-- an account made by a code), and whether a code is its key.
create function private.find_businesses(p_query text default null) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
    v_query     text := nullif(trim(p_query), '');
    v_digits    text := nullif(regexp_replace(coalesce(p_query, ''), '\D', '', 'g'), '');
    v_by_code   uuid;
begin
    if v_query is not null and private.code_well_formed(private.normalize_code(v_query)) then
        select c.business_id into v_by_code from private.activation_codes c where c.digest = private.code_digest(v_query);
    end if;
    return coalesce((
        select jsonb_agg(found.item order by found.created_at desc)
        from (
            select b.created_at, jsonb_build_object(
                'business_id', b.id,
                'name', b.name,
                'contact_phone', b.contact_phone,
                'created_on', b.created_at::date,
                'plan', s.plan_id,
                'status', s.status,
                'valid_from', s.valid_from,
                'valid_to', s.valid_to,
                'phones', (select count(*) from public.devices d where d.business_id = b.id and d.status = 'active'),
                'last_seen', (select max(d.last_seen_at) from public.devices d where d.business_id = b.id),
                'owner_email', (select u.email
                                from public.memberships m join auth.users u on u.id = m.user_id
                                where m.business_id = b.id and m.role = 'owner' and m.status = 'active'
                                  and u.email is not null
                                order by m.created_at limit 1),
                'by_code', exists (select 1 from private.activation_codes c where c.business_id = b.id and c.use = 'key')
            ) as item
            from public.businesses b
            left join public.subscriptions s on s.business_id = b.id
            where v_query is null
               or b.id = v_by_code
               or b.name ilike '%' || v_query || '%'
               or (length(v_digits) >= 4
                   and regexp_replace(coalesce(b.contact_phone, ''), '\D', '', 'g') like '%' || v_digits || '%')
               or exists (select 1
                          from public.memberships m join auth.users u on u.id = m.user_id
                          where m.business_id = b.id and u.email ilike '%' || v_query || '%')
            order by b.created_at desc
            limit 30
        ) found
    ), '[]'::jsonb);
end
$$;

-- Suspends (p_suspended true) or reactivates a business's subscription. Suspended, no license is issued: an
-- activation is refused, and each phone's license ends at its next check-in. The owner's own business is refused
-- (own_business), so a slip cannot lock him out. Returns ok {subscription_status}, or unknown_business.
create function private.set_business_suspended(p_admin uuid, p_business uuid, p_suspended boolean, p_note text default null)
returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare
    v_status text := case when p_suspended then 'suspended' else 'active' end;
begin
    if exists (select 1 from public.memberships m where m.business_id = p_business and m.user_id = p_admin) then
        return jsonb_build_object('status', 'own_business');
    end if;
    update public.subscriptions s
    set status = v_status,
        updated_at = now(),
        note = concat_ws(' — ', s.note, nullif(trim(p_note), ''))
    where s.business_id = p_business;
    if not found then
        return jsonb_build_object('status', 'unknown_business');
    end if;
    return jsonb_build_object('status', 'ok', 'subscription_status', v_status);
end
$$;

-- Deletes a business: its subscription, its phones' seats and its memberships go with it, and its key codes open
-- nothing any more. The accounts stay: an e-mail account can sign in again; an account made by a code, left
-- without a business, goes with the nightly cleanup. Never the owner's own (own_business). Returns deleted, or
-- unknown_business.
create function private.delete_business(p_admin uuid, p_business uuid) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
begin
    if exists (select 1 from public.memberships m where m.business_id = p_business and m.user_id = p_admin) then
        return jsonb_build_object('status', 'own_business');
    end if;
    delete from public.businesses b where b.id = p_business;
    if not found then
        return jsonb_build_object('status', 'unknown_business');
    end if;
    return jsonb_build_object('status', 'deleted');
end
$$;

-- Only the Edge Functions run them; revoked by name, as the earlier migrations do.
revoke execute on function private.find_businesses(text), private.set_business_suspended(uuid, uuid, boolean, text),
    private.delete_business(uuid, uuid)
    from public, anon, authenticated;

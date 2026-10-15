-- « Lots » in DistriGo's « Administration » (the owner's request, 2026-10-15): a batch opened for its details, its
-- free codes revoked, or the batch deleted once none of its codes is a business's key. A batch is named as the list
-- groups it (private.codes_by_batch): its name, its plan and its days.

-- The batch's details: its counts; its codes' activation deadline, company and note (made together, they share them;
-- a revoked code's reason is appended to its own note, so the shortest note is the batch's); how many codes are the
-- key of a business that still exists (live_keys: the batch can't be deleted while there are any); and what each used
-- code did. Returns ok {...}, or unknown_batch.
create function private.batch_details(p_batch text, p_plan text, p_days int) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
    v_batch jsonb;
begin
    select jsonb_build_object(
               'status', 'ok', 'batch', b.batch, 'plan', b.plan_id, 'days', b.days, 'total', b.total, 'unused', b.unused,
               'keys', b.keys, 'renewals', b.renewals, 'expired', b.expired, 'revoked', b.revoked,
               'created_on', b.created_at::date)
    into v_batch
    from private.codes_by_batch b
    where b.batch = p_batch and b.plan_id = p_plan and b.days = p_days;
    if v_batch is null then
        return jsonb_build_object('status', 'unknown_batch');
    end if;
    return v_batch || (
        select jsonb_build_object(
                   'expires_on', max(c.expires_on),
                   'business_name', max(c.business_name),
                   'note', (array_agg(c.note order by length(c.note)) filter (where c.note is not null))[1],
                   'live_keys', count(*) filter (where c.use = 'key' and bz.id is not null),
                   'uses', coalesce(
                       jsonb_agg(jsonb_build_object('use', c.use, 'business_name', bz.name, 'redeemed_at', c.redeemed_at)
                                 order by c.redeemed_at desc) filter (where c.use is not null),
                       '[]'::jsonb))
        from private.activation_codes c
        left join public.businesses bz on bz.id = c.business_id
        where c.batch = p_batch and c.plan_id = p_plan and c.days = p_days
    );
end
$$;

-- « Révoquer les codes libres »: every code of the batch still free (not used, not revoked, not expired) can never be
-- used; the used ones are untouched, as are the businesses they opened. [p_reason] goes into each code's note, as
-- private.revoke_code does. Returns ok {revoked}, or unknown_batch.
create function private.revoke_batch(p_batch text, p_plan text, p_days int, p_reason text default null) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare
    v_count int;
begin
    if not exists (select 1 from private.activation_codes c where c.batch = p_batch and c.plan_id = p_plan and c.days = p_days) then
        return jsonb_build_object('status', 'unknown_batch');
    end if;
    update private.activation_codes c
    set revoked_at = now(),
        note = concat_ws(' — ', c.note, nullif(trim(p_reason), ''))
    where c.batch = p_batch and c.plan_id = p_plan and c.days = p_days
      and c.use is null and c.revoked_at is null
      and (c.expires_on is null or c.expires_on >= current_date);
    get diagnostics v_count = row_count;
    return jsonb_build_object('status', 'ok', 'revoked', v_count);
end
$$;

-- « Supprimer le lot »: its codes go, unless one is still the key of a business that exists (in_use {keys}): that
-- business would lose the code that brings it to a new phone. Days a renewal added stay added. The batch's rows are
-- locked first, so a code redeemed meanwhile waits and then finds nothing, rather than becoming a key that is then
-- deleted. Returns deleted {codes}, in_use {keys}, or unknown_batch.
create function private.delete_batch(p_batch text, p_plan text, p_days int) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare
    v_keys int;
    v_count int;
begin
    perform 1 from private.activation_codes c
    where c.batch = p_batch and c.plan_id = p_plan and c.days = p_days
    for update;
    if not found then
        return jsonb_build_object('status', 'unknown_batch');
    end if;
    select count(*)::int into v_keys
    from private.activation_codes c
    where c.batch = p_batch and c.plan_id = p_plan and c.days = p_days and c.use = 'key'
      and exists (select 1 from public.businesses b where b.id = c.business_id);
    if v_keys > 0 then
        return jsonb_build_object('status', 'in_use', 'keys', v_keys);
    end if;
    delete from private.activation_codes c where c.batch = p_batch and c.plan_id = p_plan and c.days = p_days;
    get diagnostics v_count = row_count;
    return jsonb_build_object('status', 'deleted', 'codes', v_count);
end
$$;

-- Only the Edge Functions run them; revoked by name, as the earlier migrations do.
revoke execute on function private.batch_details(text, text, int), private.revoke_batch(text, text, int, text),
    private.delete_batch(text, text, int)
    from public, anon, authenticated;

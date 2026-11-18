-- What the privacy policy says, held by the database (distrigoo.com/confidentialite, 2026-11-18):
--   1. License check-ins and old phones are kept 12 months at most: a nightly purge.
--   2. A deleted account leaves nothing that names it: the business name written on a code made for that business
--      is cleared with the rest.

-- ───────────────────────────── 1. Twelve months at most ─────────────────────────────

-- Deletes what is older than p_after (12 months):
--   * license check-ins (license_issuances), whatever the phone;
--   * phones released or revoked that long ago, with their model, identifiers and attestation (their check-ins go
--     with them); a phone still active is never touched, however old;
--   * phone changes (device_transfers): the 3-per-30-days rule only reads the last 30 days.
-- Returns how many of each it deleted.
create function private.purge_old_records(p_after interval default interval '12 months') returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare
    v_issuances int;
    v_devices   int;
    v_transfers int;
begin
    delete from private.license_issuances i where i.issued_at < now() - p_after;
    get diagnostics v_issuances = row_count;

    delete from public.devices d
    where d.status in ('released', 'revoked') and d.released_at < now() - p_after;
    get diagnostics v_devices = row_count;

    delete from private.device_transfers t where t.at < now() - p_after;
    get diagnostics v_transfers = row_count;

    return jsonb_build_object('issuances', v_issuances, 'devices', v_devices, 'transfers', v_transfers);
end
$$;

-- Every night at 02:45 UTC (03:45 in Algiers), after the anonymous-account cleanup, where pg_cron is available: on
-- Supabase, not on the local test cluster, which calls the function itself.
do $$
begin
    if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
        create extension if not exists pg_cron;
        perform cron.schedule('distrigo-retention-purge', '45 2 * * *', 'select private.purge_old_records()');
    end if;
end
$$;

-- ───────────────────────────── 2. Deletion leaves no name ─────────────────────────────

-- As before (an owner whose business has other active members transfers it first), and now: a code made for one
-- of the deleted businesses loses the business name written on it. The code itself stays, marked used, so it can't
-- be redeemed again; its business_id and redeemed_by are cleared by their foreign keys.
create or replace function private.delete_account_data(p_user uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    v_blocked uuid[];
    v_owned   uuid[];
begin
    select array_agg(m.business_id) into v_blocked
    from public.memberships m
    where m.user_id = p_user and m.role = 'owner' and m.status = 'active'
      and exists (select 1 from public.memberships other
                  where other.business_id = m.business_id and other.user_id <> p_user and other.status = 'active');
    if v_blocked is not null then
        return jsonb_build_object('status', 'transfer_ownership_first', 'businesses', to_jsonb(v_blocked));
    end if;

    select array_agg(m.business_id) into v_owned
    from public.memberships m
    where m.user_id = p_user and m.role = 'owner' and m.status = 'active';

    update private.activation_codes c set business_name = null
    where c.business_id = any (v_owned) and c.business_name is not null;

    delete from public.businesses b where b.id = any (v_owned);
    return jsonb_build_object('status', 'ok');
end
$$;

-- Only the Edge Functions and the schedule run them (Postgres grants EXECUTE to public by default)
revoke execute on function private.purge_old_records(interval), private.delete_account_data(uuid)
    from public, anon, authenticated;

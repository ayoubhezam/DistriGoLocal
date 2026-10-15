-- C4 (docs/activation_codes.md): the owner manages codes from DistriGo's « Administration » screen, through the
-- codes-admin Edge Function, rather than Supabase Studio; and an account made by a code that never got a business
-- is deleted after 7 days (the owner's choice, 2026-10-14).

-- ───────────────────────────── Who may manage codes ─────────────────────────────

-- The owner's account carries {"distrigo_admin": true} in its app_metadata, which only the service role can write
-- (Studio, or SQL as postgres). The app reads it from the session to show the screen; this checks it on every call.
create function private.is_admin(p_user uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from auth.users u
        where u.id = p_user and u.raw_app_meta_data ->> 'distrigo_admin' = 'true'
    )
$$;

-- ───────────────────────────── Accounts that never got a business ─────────────────────────────

-- « Activer avec un code » makes the account before the code is checked. A wrong code, or a merchant who gave up,
-- leaves an account with no business and no way back in (it has no e-mail). After p_after it goes. An account with
-- any membership, even a past one, stays, and so does one given an e-mail. Returns how many went.
create function private.cleanup_anonymous_accounts(p_after interval default interval '7 days') returns int
language plpgsql volatile security definer set search_path = '' as $$
declare
    v_count int;
begin
    delete from auth.users u
    where u.is_anonymous
      and u.created_at < now() - p_after
      and not exists (select 1 from public.memberships m where m.user_id = u.id);
    get diagnostics v_count = row_count;
    return v_count;
end
$$;

-- Every night at 02:30 UTC (03:30 in Algiers), where pg_cron is available: on Supabase, not on the local test
-- cluster, which calls the function itself.
do $$
begin
    if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
        create extension if not exists pg_cron;
        perform cron.schedule('distrigo-anonymous-cleanup', '30 2 * * *', 'select private.cleanup_anonymous_accounts()');
    end if;
end
$$;

-- Only the Edge Functions (as postgres or service_role) and the schedule run them; revoked by name, as the
-- earlier migrations do, since the private schema's default privileges cannot take back PUBLIC's EXECUTE.
revoke execute on function private.is_admin(uuid), private.cleanup_anonymous_accounts(interval)
    from public, anon, authenticated;

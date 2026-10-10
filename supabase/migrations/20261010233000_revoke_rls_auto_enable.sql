-- public.rls_auto_enable() is Supabase's: the security-definer function behind the ensure_rls event trigger, which
-- turns row-level security on for every new table. With Postgres's default privileges anyone may execute it, through
-- the API too (/rest/v1/rpc/rls_auto_enable). Called that way it can only fail, being an event-trigger function, but
-- nothing outside the trigger should call it, and the security advisor flags it. The trigger keeps working: DDL runs
-- as postgres, the function's owner, who keeps the privilege. Checked on the live project after this revoke: a new
-- table still had row-level security turned on.
--
-- Guarded: the function exists only on projects created with that option, not in the local tests.
do $$
begin
    if to_regprocedure('public.rls_auto_enable()') is not null then
        revoke execute on function public.rls_auto_enable() from public, anon, authenticated;
    end if;
end
$$;
